# Dev Radar — Plan 3: AI Radar Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the AI agent that turns ~150 ingested `source_items` into a personalized "Radar" — themes with cited sources — using Claude Sonnet for orchestration, Haiku for cheap relevance scoring, Redis for cross-user summary caching, and SSE for theme-by-theme streaming to the frontend.

**Architecture:** A `com.devradar.ai` module wraps the Anthropic SDK behind an `AiClient` interface (so tests can swap in a recorded fake). A `RadarOrchestrator` runs a multi-turn tool-calling loop with three tools (`searchItems`, `scoreRelevance`, `getItemDetail`); each loop iteration parses tool_use blocks, executes them, sends tool_results back. As themes finalize they're persisted to MySQL AND published to a per-radar in-memory event bus that an SSE endpoint relays to the client. Generation runs in a Spring `@Async` task so the POST returns immediately with a radar_id.

**Tech Stack:** `com.anthropic:anthropic-java` SDK, Spring Boot 3.5+ async + SSE (SseEmitter), Spring Data JPA, Redis (StringRedisTemplate from Plan 2), JUnit 5 + Testcontainers + Mockito, AssertJ.

---

## File Structure

```
backend/
├── pom.xml                                          (modify: + anthropic-java)
├── src/main/
│   ├── java/com/devradar/
│   │   ├── DevRadarApplication.java                (modify: add @EnableAsync)
│   │   ├── config/
│   │   │   └── AsyncConfig.java                    (taskExecutor for radar generation)
│   │   ├── domain/
│   │   │   ├── Radar.java
│   │   │   ├── RadarStatus.java                    (enum GENERATING / READY / FAILED)
│   │   │   ├── RadarTheme.java
│   │   │   └── RadarThemeItem.java
│   │   ├── repository/
│   │   │   ├── RadarRepository.java
│   │   │   ├── RadarThemeRepository.java
│   │   │   └── RadarThemeItemRepository.java
│   │   ├── ai/
│   │   │   ├── AiClient.java                       (interface — swappable)
│   │   │   ├── AnthropicAiClient.java              (real SDK impl)
│   │   │   ├── AiMessage.java                      (DTO for our wrapper)
│   │   │   ├── AiToolCall.java                     (DTO)
│   │   │   ├── AiToolResult.java                   (DTO)
│   │   │   ├── AiResponse.java                     (DTO with stop_reason + content blocks)
│   │   │   ├── AiSummaryCache.java                 (Redis-backed)
│   │   │   ├── tools/
│   │   │   │   ├── ToolDefinition.java             (name, description, input schema JSON)
│   │   │   │   ├── ToolRegistry.java               (looks up tool by name and dispatches)
│   │   │   │   ├── SearchItemsTool.java
│   │   │   │   ├── ScoreRelevanceTool.java         (calls Haiku)
│   │   │   │   └── GetItemDetailTool.java
│   │   │   └── RadarOrchestrator.java              (the agent loop)
│   │   ├── radar/
│   │   │   ├── RadarService.java                   (create + persist)
│   │   │   ├── RadarGenerationService.java         (@Async — runs the loop)
│   │   │   ├── RadarEventBus.java                  (in-memory event publisher per radar_id)
│   │   │   ├── application/
│   │   │   │   └── RadarApplicationService.java
│   │   │   └── event/
│   │   │       ├── RadarStartedEvent.java
│   │   │       ├── ThemeCompleteEvent.java
│   │   │       ├── RadarCompleteEvent.java
│   │   │       └── RadarFailedEvent.java
│   │   └── web/rest/
│   │       ├── RadarResource.java                  (POST/GET REST)
│   │       ├── RadarSseResource.java               (SSE stream)
│   │       └── dto/
│   │           ├── RadarSummaryDTO.java
│   │           ├── RadarDetailDTO.java
│   │           ├── RadarThemeDTO.java
│   │           └── RadarItemDTO.java
│   └── resources/
│       └── db/changelog/
│           ├── db.changelog-master.xml             (modify: + 007)
│           └── 007-radar-schema.xml
└── src/test/
    └── java/com/devradar/
        ├── ai/
        │   ├── AiSummaryCacheTest.java             (real Redis)
        │   ├── tools/
        │   │   ├── SearchItemsToolTest.java        (real MySQL)
        │   │   ├── ScoreRelevanceToolTest.java     (mocked AiClient)
        │   │   └── GetItemDetailToolTest.java      (real MySQL)
        │   ├── RadarOrchestratorTest.java          (mocked AiClient + recorded tool-call script)
        │   └── RecordedAiClient.java               (test double that replays a canned response queue)
        ├── radar/
        │   └── RadarGenerationServiceIT.java       (full pipeline; mocked AiClient)
        └── web/rest/
            └── RadarResourceIT.java                (POST + GET + SSE)
```

---

## Task 1: Add Anthropic SDK + AsyncConfig

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/devradar/config/AsyncConfig.java`
- Modify: `backend/src/main/java/com/devradar/DevRadarApplication.java`

- [ ] **Step 1: Add Anthropic Java SDK to pom.xml**

In the `<dependencies>` block, after `jsoup`:

```xml
        <dependency>
            <groupId>com.anthropic</groupId>
            <artifactId>anthropic-java</artifactId>
            <version>0.49.0</version>
        </dependency>
```

If `0.49.0` doesn't resolve, run `mvn versions:display-dependency-updates` or check `https://central.sonatype.com/artifact/com.anthropic/anthropic-java` for the latest. Pin to a stable release.

- [ ] **Step 2: Create AsyncConfig**

```java
// backend/src/main/java/com/devradar/config/AsyncConfig.java
package com.devradar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "radarGenerationExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("radar-gen-");
        exec.initialize();
        return exec;
    }
}
```

- [ ] **Step 3: Add `@EnableAsync` to `DevRadarApplication.java`**

```java
// backend/src/main/java/com/devradar/DevRadarApplication.java
package com.devradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableScheduling
@EnableAsync
public class DevRadarApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevRadarApplication.class, args);
    }
}
```

- [ ] **Step 4: Add Anthropic config to application.yml**

Append at the bottom of `backend/src/main/resources/application.yml`:

```yaml
anthropic:
  api-key: ${ANTHROPIC_API_KEY:}
  orchestrator-model: claude-sonnet-4-6
  scoring-model: claude-haiku-4-5-20251001
  max-tool-iterations: 8
  max-tokens-per-call: 4096
```

And to `backend/src/test/resources/application-test.yml`:

```yaml
anthropic:
  api-key: test-only-not-used
  orchestrator-model: claude-sonnet-4-6
  scoring-model: claude-haiku-4-5-20251001
  max-tool-iterations: 4
  max-tokens-per-call: 1024
```

- [ ] **Step 5: Verify build**

```bash
cd backend && mvn -DskipTests compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit (no Co-Authored-By trailer)**

```bash
git add backend/pom.xml backend/src/main/java/com/devradar/config/AsyncConfig.java backend/src/main/java/com/devradar/DevRadarApplication.java backend/src/main/resources/application.yml backend/src/test/resources/application-test.yml
git commit -m "feat(ai): add Anthropic SDK dependency + async executor + config"
```

---

## Task 2: Liquibase radar schema

**Files:**
- Create: `backend/src/main/resources/db/changelog/007-radar-schema.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml` (append `<include file="db/changelog/007-radar-schema.xml"/>`)

- [ ] **Step 1: Create schema**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="007-create-radars" author="devradar">
        <createTable tableName="radars">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="user_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="period_start" type="TIMESTAMP"><constraints nullable="false"/></column>
            <column name="period_end" type="TIMESTAMP"><constraints nullable="false"/></column>
            <column name="status" type="VARCHAR(20)"><constraints nullable="false"/></column>
            <column name="generated_at" type="TIMESTAMP"/>
            <column name="generation_ms" type="BIGINT"/>
            <column name="token_count" type="INT"/>
            <column name="error_code" type="VARCHAR(80)"/>
            <column name="error_message" type="VARCHAR(1000)"/>
            <column name="created_at" type="TIMESTAMP"><constraints nullable="false"/></column>
        </createTable>
        <addForeignKeyConstraint baseTableName="radars" baseColumnNames="user_id"
            constraintName="fk_radars_user" referencedTableName="users" referencedColumnNames="id" onDelete="CASCADE"/>
        <createIndex tableName="radars" indexName="ix_radars_user_generated">
            <column name="user_id"/>
            <column name="generated_at" descending="true"/>
        </createIndex>
    </changeSet>

    <changeSet id="007-create-radar-themes" author="devradar">
        <createTable tableName="radar_themes">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="radar_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="title" type="VARCHAR(500)"><constraints nullable="false"/></column>
            <column name="summary" type="TEXT"><constraints nullable="false"/></column>
            <column name="display_order" type="INT"><constraints nullable="false"/></column>
            <column name="created_at" type="TIMESTAMP"><constraints nullable="false"/></column>
        </createTable>
        <addForeignKeyConstraint baseTableName="radar_themes" baseColumnNames="radar_id"
            constraintName="fk_radar_themes_radar" referencedTableName="radars" referencedColumnNames="id" onDelete="CASCADE"/>
        <createIndex tableName="radar_themes" indexName="ix_radar_themes_radar">
            <column name="radar_id"/>
        </createIndex>
    </changeSet>

    <changeSet id="007-create-radar-theme-items" author="devradar">
        <createTable tableName="radar_theme_items">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="theme_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="source_item_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="ai_commentary" type="VARCHAR(1000)"/>
            <column name="display_order" type="INT"><constraints nullable="false"/></column>
        </createTable>
        <addForeignKeyConstraint baseTableName="radar_theme_items" baseColumnNames="theme_id"
            constraintName="fk_radar_theme_items_theme" referencedTableName="radar_themes" referencedColumnNames="id" onDelete="CASCADE"/>
        <addForeignKeyConstraint baseTableName="radar_theme_items" baseColumnNames="source_item_id"
            constraintName="fk_radar_theme_items_source" referencedTableName="source_items" referencedColumnNames="id" onDelete="RESTRICT"/>
        <createIndex tableName="radar_theme_items" indexName="ix_radar_theme_items_theme">
            <column name="theme_id"/>
        </createIndex>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: Append to master changelog**

Inside `db.changelog-master.xml`, after the existing 6 includes, add:

```xml
    <include file="db/changelog/007-radar-schema.xml"/>
```

- [ ] **Step 3: Verify migration applies against local MySQL**

```bash
cd backend
DB_HOST_PORT=${DB_HOST_PORT:-3307} docker compose up -d mysql
sleep 8
DB_HOST_PORT=${DB_HOST_PORT:-3307} mvn spring-boot:run &
APP=$!
sleep 25
DB_HOST_PORT=${DB_HOST_PORT:-3307} docker compose exec mysql mysql -udevradar -pdevradar devradar -e "SHOW TABLES;" | grep -i radar
kill $APP
```

Expected output includes: `radars`, `radar_themes`, `radar_theme_items`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/
git commit -m "feat(db): add radar schema (radars, radar_themes, radar_theme_items)"
```

---

## Task 3: Domain entities

**Files:**
- Create: `backend/src/main/java/com/devradar/domain/RadarStatus.java`
- Create: `backend/src/main/java/com/devradar/domain/Radar.java`
- Create: `backend/src/main/java/com/devradar/domain/RadarTheme.java`
- Create: `backend/src/main/java/com/devradar/domain/RadarThemeItem.java`

- [ ] **Step 1: Create `RadarStatus.java`**

```java
package com.devradar.domain;
public enum RadarStatus { GENERATING, READY, FAILED }
```

- [ ] **Step 2: Create `Radar.java`**

```java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "radars")
public class Radar {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RadarStatus status;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "generation_ms")
    private Long generationMs;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant v) { this.periodStart = v; }
    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant v) { this.periodEnd = v; }
    public RadarStatus getStatus() { return status; }
    public void setStatus(RadarStatus v) { this.status = v; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant v) { this.generatedAt = v; }
    public Long getGenerationMs() { return generationMs; }
    public void setGenerationMs(Long v) { this.generationMs = v; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer v) { this.tokenCount = v; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String v) { this.errorCode = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 3: Create `RadarTheme.java`**

```java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "radar_themes")
public class RadarTheme {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "radar_id", nullable = false)
    private Long radarId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getRadarId() { return radarId; }
    public void setRadarId(Long v) { this.radarId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
}
```

- [ ] **Step 4: Create `RadarThemeItem.java`**

```java
package com.devradar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "radar_theme_items")
public class RadarThemeItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theme_id", nullable = false)
    private Long themeId;

    @Column(name = "source_item_id", nullable = false)
    private Long sourceItemId;

    @Column(name = "ai_commentary", length = 1000)
    private String aiCommentary;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public Long getId() { return id; }
    public Long getThemeId() { return themeId; }
    public void setThemeId(Long v) { this.themeId = v; }
    public Long getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(Long v) { this.sourceItemId = v; }
    public String getAiCommentary() { return aiCommentary; }
    public void setAiCommentary(String v) { this.aiCommentary = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
}
```

- [ ] **Step 5: Verify compile + commit**

```bash
cd backend && mvn -DskipTests compile
git add backend/src/main/java/com/devradar/domain/Radar.java backend/src/main/java/com/devradar/domain/RadarStatus.java backend/src/main/java/com/devradar/domain/RadarTheme.java backend/src/main/java/com/devradar/domain/RadarThemeItem.java
git commit -m "feat(domain): add Radar, RadarTheme, RadarThemeItem entities"
```

---

## Task 4: Repositories

**Files:**
- Create: `backend/src/main/java/com/devradar/repository/RadarRepository.java`
- Create: `backend/src/main/java/com/devradar/repository/RadarThemeRepository.java`
- Create: `backend/src/main/java/com/devradar/repository/RadarThemeItemRepository.java`

- [ ] **Step 1: Create the three repositories**

```java
// backend/src/main/java/com/devradar/repository/RadarRepository.java
package com.devradar.repository;

import com.devradar.domain.Radar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RadarRepository extends JpaRepository<Radar, Long> {
    Page<Radar> findByUserIdOrderByGeneratedAtDesc(Long userId, Pageable pageable);
}
```

```java
// backend/src/main/java/com/devradar/repository/RadarThemeRepository.java
package com.devradar.repository;

import com.devradar.domain.RadarTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RadarThemeRepository extends JpaRepository<RadarTheme, Long> {
    List<RadarTheme> findByRadarIdOrderByDisplayOrderAsc(Long radarId);
}
```

```java
// backend/src/main/java/com/devradar/repository/RadarThemeItemRepository.java
package com.devradar.repository;

import com.devradar.domain.RadarThemeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RadarThemeItemRepository extends JpaRepository<RadarThemeItem, Long> {
    List<RadarThemeItem> findByThemeIdOrderByDisplayOrderAsc(Long themeId);
}
```

- [ ] **Step 2: Verify compile + commit**

```bash
cd backend && mvn -DskipTests compile
git add backend/src/main/java/com/devradar/repository/RadarRepository.java backend/src/main/java/com/devradar/repository/RadarThemeRepository.java backend/src/main/java/com/devradar/repository/RadarThemeItemRepository.java
git commit -m "feat(repo): add Radar, RadarTheme, RadarThemeItem repositories"
```

---

## Task 5: AiSummaryCache (Redis) — TDD

**Files:**
- Create: `backend/src/test/java/com/devradar/ai/AiSummaryCacheTest.java`
- Create: `backend/src/main/java/com/devradar/ai/AiSummaryCache.java`

- [ ] **Step 1: Write failing tests**

```java
// backend/src/test/java/com/devradar/ai/AiSummaryCacheTest.java
package com.devradar.ai;

import com.devradar.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AiSummaryCacheTest extends AbstractIntegrationTest {

    @Autowired AiSummaryCache cache;

    @Test
    void putAndGet_roundTrip() {
        List<Long> ids = List.of(1L, 2L, 3L);
        cache.put(ids, "Spring Boot 3.5 ships virtual threads.");

        Optional<String> got = cache.get(ids);
        assertThat(got).contains("Spring Boot 3.5 ships virtual threads.");
    }

    @Test
    void get_missingKey_returnsEmpty() {
        assertThat(cache.get(List.of(99999L))).isEmpty();
    }

    @Test
    void key_isOrderIndependent() {
        cache.put(List.of(10L, 20L, 30L), "summary text");
        assertThat(cache.get(List.of(30L, 10L, 20L))).contains("summary text");
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=AiSummaryCacheTest test
```

- [ ] **Step 3: Implement `AiSummaryCache.java`**

```java
package com.devradar.ai;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class AiSummaryCache {

    private static final Duration TTL = Duration.ofDays(30);
    private static final String PREFIX = "ai:summary:";

    private final StringRedisTemplate redis;

    public AiSummaryCache(StringRedisTemplate redis) { this.redis = redis; }

    public Optional<String> get(List<Long> sourceItemIds) {
        String key = buildKey(sourceItemIds);
        String v = redis.opsForValue().get(key);
        return Optional.ofNullable(v);
    }

    public void put(List<Long> sourceItemIds, String summary) {
        String key = buildKey(sourceItemIds);
        redis.opsForValue().set(key, summary, TTL);
    }

    private static String buildKey(List<Long> ids) {
        // Sort to make key order-independent
        List<Long> sorted = ids.stream().sorted().toList();
        String joined = String.join(",", sorted.stream().map(String::valueOf).toList());
        return PREFIX + sha256Hex(joined);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
```

- [ ] **Step 4: Run — expect 3/3 PASS**

```bash
cd backend && mvn -Dtest=AiSummaryCacheTest test
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ai/AiSummaryCache.java backend/src/test/java/com/devradar/ai/AiSummaryCacheTest.java
git commit -m "feat(ai): add AiSummaryCache (Redis) keyed by sorted source_item ids"
```

---

## Task 6: AI DTOs + AiClient interface

**Files:**
- Create: `backend/src/main/java/com/devradar/ai/AiMessage.java`
- Create: `backend/src/main/java/com/devradar/ai/AiToolCall.java`
- Create: `backend/src/main/java/com/devradar/ai/AiToolResult.java`
- Create: `backend/src/main/java/com/devradar/ai/AiResponse.java`
- Create: `backend/src/main/java/com/devradar/ai/AiClient.java`

- [ ] **Step 1: Create the data records**

```java
// backend/src/main/java/com/devradar/ai/AiMessage.java
package com.devradar.ai;

import java.util.List;

/**
 * A message in the conversation. role is "user" or "assistant".
 * `content` is the text part; `toolCalls` and `toolResults` carry structured pieces.
 */
public record AiMessage(String role, String content, List<AiToolCall> toolCalls, List<AiToolResult> toolResults) {
    public static AiMessage userText(String text) {
        return new AiMessage("user", text, List.of(), List.of());
    }
    public static AiMessage userToolResults(List<AiToolResult> results) {
        return new AiMessage("user", null, List.of(), results);
    }
}
```

```java
// backend/src/main/java/com/devradar/ai/AiToolCall.java
package com.devradar.ai;

/** A tool call requested by the assistant. `id` is provider-assigned. `inputJson` is the raw arguments. */
public record AiToolCall(String id, String name, String inputJson) {}
```

```java
// backend/src/main/java/com/devradar/ai/AiToolResult.java
package com.devradar.ai;

/** Our response to a tool call, sent back as a user message. `outputJson` is the tool's result text. */
public record AiToolResult(String toolCallId, String outputJson, boolean isError) {}
```

```java
// backend/src/main/java/com/devradar/ai/AiResponse.java
package com.devradar.ai;

import java.util.List;

/**
 * What the model returned. `text` is the assistant's text content (may be empty).
 * `toolCalls` is non-empty when the model wants to invoke tools.
 * `stopReason` is "end_turn" when done, "tool_use" when more tools to run.
 */
public record AiResponse(String text, List<AiToolCall> toolCalls, String stopReason, int inputTokens, int outputTokens) {}
```

```java
// backend/src/main/java/com/devradar/ai/AiClient.java
package com.devradar.ai;

import com.devradar.ai.tools.ToolDefinition;
import java.util.List;

/**
 * Provider-agnostic interface to a chat model with tool use.
 * Production impl uses Anthropic SDK; tests use a recorded fake.
 */
public interface AiClient {
    AiResponse generate(String model, String systemPrompt, List<AiMessage> messages, List<ToolDefinition> tools, int maxTokens);
}
```

- [ ] **Step 2: Verify compile**

This will fail because `ToolDefinition` doesn't exist yet. Stub it now:

```java
// backend/src/main/java/com/devradar/ai/tools/ToolDefinition.java
package com.devradar.ai.tools;

/**
 * `inputSchemaJson` is a JSON Schema describing the tool's input.
 * Anthropic format: {"type":"object","properties":{...},"required":[...]}
 */
public record ToolDefinition(String name, String description, String inputSchemaJson) {}
```

```bash
cd backend && mvn -DskipTests compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devradar/ai/
git commit -m "feat(ai): add provider-agnostic AiClient interface + DTOs"
```

---

## Task 7: AnthropicAiClient (real SDK impl)

**File:** `backend/src/main/java/com/devradar/ai/AnthropicAiClient.java`

The exact SDK API depends on the `com.anthropic:anthropic-java` version. The implementation below uses the typical builder pattern; verify against the actual classes available after Task 1's dependency lands.

- [ ] **Step 1: Implement using current SDK API**

```java
package com.devradar.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.Tool.InputSchema;
import com.devradar.ai.tools.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AnthropicAiClient implements AiClient {

    private final AnthropicClient sdk;
    private final ObjectMapper json = new ObjectMapper();

    public AnthropicAiClient(@Value("${anthropic.api-key}") String apiKey) {
        this.sdk = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    @Override
    public AiResponse generate(String model, String systemPrompt, List<AiMessage> messages, List<ToolDefinition> tools, int maxTokens) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .system(systemPrompt);

        for (AiMessage m : messages) {
            // Convert AiMessage -> MessageParam (provider-specific shape)
            // Simplification: support text-only assistant + user, plus user tool_results.
            // Tool calls in the assistant's prior turn are reconstructed by the SDK from the response message.
            // For MVP we only add USER messages here; the SDK keeps assistant turns implicitly via the response.
            // If you need to send a multi-turn history with prior tool_use blocks, build content blocks:
            //   ContentBlockParam.ofText / ofToolUse / ofToolResult
            if ("user".equals(m.role())) {
                if (m.toolResults() != null && !m.toolResults().isEmpty()) {
                    // Build tool_result content blocks
                    var contentBlocks = new ArrayList<com.anthropic.models.messages.ContentBlockParam>();
                    for (AiToolResult r : m.toolResults()) {
                        contentBlocks.add(com.anthropic.models.messages.ContentBlockParam.ofToolResult(
                            com.anthropic.models.messages.ToolResultBlockParam.builder()
                                .toolUseId(r.toolCallId())
                                .content(r.outputJson())
                                .isError(r.isError())
                                .build()
                        ));
                    }
                    builder.addMessage(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(contentBlocks).build());
                } else if (m.content() != null) {
                    builder.addUserMessage(m.content());
                }
            } else if ("assistant".equals(m.role()) && m.content() != null) {
                builder.addAssistantMessage(m.content());
            }
        }

        for (ToolDefinition t : tools) {
            try {
                JsonNode schema = json.readTree(t.inputSchemaJson());
                InputSchema is = InputSchema.builder()
                    .type(InputSchema.Type.OBJECT)
                    .properties(json.convertValue(schema.get("properties"), JsonNode.class))
                    .build();
                builder.addTool(Tool.builder()
                    .name(t.name())
                    .description(t.description())
                    .inputSchema(is)
                    .build());
            } catch (Exception e) {
                throw new RuntimeException("invalid tool schema for " + t.name(), e);
            }
        }

        Message resp = sdk.messages().create(builder.build());

        StringBuilder textOut = new StringBuilder();
        List<AiToolCall> toolCalls = new ArrayList<>();
        for (ContentBlock block : resp.content()) {
            block.text().ifPresent(t -> textOut.append(t.text()));
            block.toolUse().ifPresent(tu -> {
                String inputJson;
                try { inputJson = json.writeValueAsString(tu._input()); } catch (Exception e) { inputJson = "{}"; }
                toolCalls.add(new AiToolCall(tu.id(), tu.name(), inputJson));
            });
        }

        String stop = resp.stopReason().map(Object::toString).orElse("end_turn");
        int in = (int) resp.usage().inputTokens();
        int out = (int) resp.usage().outputTokens();

        return new AiResponse(textOut.toString(), toolCalls, stop, in, out);
    }
}
```

**If the SDK API differs** from what's shown, the implementer should:
1. Run `mvn dependency:tree | grep anthropic` to confirm the version.
2. Open the SDK jar in IDE to inspect actual class names (`Message`, `MessageParam`, `Tool`, `ContentBlockParam`, `ToolUseBlock`, etc.).
3. Adjust the calls to match while preserving the interface contract: input is `(model, system, messages, tools, maxTokens)` → output is `AiResponse` with text + tool_calls + stop_reason + token counts.
4. Report DONE_WITH_CONCERNS noting the actual API shape used.

- [ ] **Step 2: Verify compile**

```bash
cd backend && mvn -DskipTests compile
```

If it fails because of SDK API mismatch, fix and re-try. NO TESTS in this task — the SDK is exercised through integration tests in Task 12.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devradar/ai/AnthropicAiClient.java
git commit -m "feat(ai): wire AnthropicAiClient implementing AiClient with tool use"
```

---

## Task 8: SearchItemsTool — TDD

**Files:**
- Create: `backend/src/test/java/com/devradar/ai/tools/SearchItemsToolTest.java`
- Create: `backend/src/main/java/com/devradar/ai/tools/SearchItemsTool.java`

The tool searches `source_items` by tag IDs from the user's interests, returning the top N most recent.

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/devradar/ai/tools/SearchItemsToolTest.java
package com.devradar.ai.tools;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.SourceItem;
import com.devradar.domain.SourceItemTag;
import com.devradar.repository.InterestTagRepository;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceItemTagRepository;
import com.devradar.repository.SourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchItemsToolTest extends AbstractIntegrationTest {

    @Autowired SearchItemsTool tool;
    @Autowired SourceRepository sources;
    @Autowired SourceItemRepository items;
    @Autowired SourceItemTagRepository sit;
    @Autowired InterestTagRepository tags;
    @Autowired ObjectMapper json;

    @Test
    void searchByTagSlugs_returnsItemsTaggedWithAny() throws Exception {
        var hn = sources.findByCode("HN").orElseThrow();
        var spring = tags.findBySlug("spring_boot").orElseThrow();
        var react = tags.findBySlug("react").orElseThrow();

        SourceItem a = newItem(hn.getId(), "search-1", "Spring Boot 3.5");
        SourceItem b = newItem(hn.getId(), "search-2", "React 19 hooks");
        items.save(a); items.save(b);
        sit.save(new SourceItemTag(a.getId(), spring.getId()));
        sit.save(new SourceItemTag(b.getId(), react.getId()));

        String input = """
            {"tag_slugs": ["spring_boot"], "limit": 10}
            """;
        String resultJson = tool.execute(input);

        JsonNode arr = json.readTree(resultJson);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isGreaterThanOrEqualTo(1);
        boolean foundSpring = false;
        for (JsonNode n : arr) if ("search-1".equals(n.get("external_id").asText())) foundSpring = true;
        assertThat(foundSpring).isTrue();
    }

    private SourceItem newItem(Long sourceId, String extId, String title) {
        SourceItem si = new SourceItem();
        si.setSourceId(sourceId);
        si.setExternalId(extId);
        si.setUrl("https://example.com/" + extId);
        si.setTitle(title);
        si.setPostedAt(Instant.now());
        return si;
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=SearchItemsToolTest test
```

- [ ] **Step 3: Implement `SearchItemsTool.java`**

```java
package com.devradar.ai.tools;

import com.devradar.domain.InterestTag;
import com.devradar.domain.SourceItem;
import com.devradar.repository.InterestTagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchItemsTool {

    public static final String NAME = "searchItems";
    public static final String DESCRIPTION = "Find recent ingested items matching the given interest tag slugs (case-sensitive). Returns the most recent N items.";
    public static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "tag_slugs": { "type": "array", "items": { "type": "string" }, "description": "Interest tag slugs like spring_boot, react" },
            "limit":     { "type": "integer", "description": "Max items to return (default 20, max 100)", "default": 20 }
          },
          "required": ["tag_slugs"]
        }
        """;

    private final InterestTagRepository tagRepo;
    private final ObjectMapper json = new ObjectMapper();

    @PersistenceContext
    private EntityManager em;

    public SearchItemsTool(InterestTagRepository tagRepo) { this.tagRepo = tagRepo; }

    public ToolDefinition definition() {
        return new ToolDefinition(NAME, DESCRIPTION, INPUT_SCHEMA);
    }

    public String execute(String inputJson) {
        try {
            var node = json.readTree(inputJson);
            List<String> slugs = new java.util.ArrayList<>();
            for (var s : node.get("tag_slugs")) slugs.add(s.asText());
            int limit = Math.min(node.has("limit") ? node.get("limit").asInt(20) : 20, 100);

            List<InterestTag> tags = tagRepo.findBySlugIn(slugs);
            if (tags.isEmpty()) return "[]";
            List<Long> tagIds = tags.stream().map(InterestTag::getId).toList();

            // Last 7 days, distinct items joined to source_item_tags
            @SuppressWarnings("unchecked")
            List<SourceItem> items = em.createQuery(
                "SELECT DISTINCT si FROM SourceItem si, SourceItemTag sit " +
                "WHERE sit.sourceItemId = si.id AND sit.interestTagId IN :tagIds " +
                "AND si.postedAt > :cutoff " +
                "ORDER BY si.postedAt DESC")
                .setParameter("tagIds", tagIds)
                .setParameter("cutoff", java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS))
                .setMaxResults(limit)
                .getResultList();

            ArrayNode arr = json.createArrayNode();
            for (SourceItem si : items) {
                ObjectNode n = json.createObjectNode();
                n.put("id", si.getId());
                n.put("external_id", si.getExternalId());
                n.put("title", si.getTitle());
                n.put("url", si.getUrl());
                n.put("posted_at", si.getPostedAt().toString());
                arr.add(n);
            }
            return json.writeValueAsString(arr);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
```

- [ ] **Step 4: Run — expect 1/1 PASS**

```bash
cd backend && mvn -Dtest=SearchItemsToolTest test
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ai/tools/SearchItemsTool.java backend/src/test/java/com/devradar/ai/tools/SearchItemsToolTest.java
git commit -m "feat(ai): add SearchItemsTool querying source_items by tag slug"
```

---

## Task 9: ScoreRelevanceTool — TDD with mocked AiClient

**Files:**
- Create: `backend/src/test/java/com/devradar/ai/tools/ScoreRelevanceToolTest.java`
- Create: `backend/src/main/java/com/devradar/ai/tools/ScoreRelevanceTool.java`

This tool calls Haiku to score a small batch of items by relevance to user interests.

- [ ] **Step 1: Write failing test (Mockito)**

```java
// backend/src/test/java/com/devradar/ai/tools/ScoreRelevanceToolTest.java
package com.devradar.ai.tools;

import com.devradar.ai.AiClient;
import com.devradar.ai.AiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoreRelevanceToolTest {

    @Test
    void execute_returnsScoresFromHaiku() throws Exception {
        AiClient ai = mock(AiClient.class);
        when(ai.generate(
                ArgumentMatchers.eq("claude-haiku-4-5-20251001"),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyList(),
                ArgumentMatchers.anyList(),
                ArgumentMatchers.anyInt()))
            .thenReturn(new AiResponse("[{\"id\":1,\"score\":0.92},{\"id\":2,\"score\":0.41}]", java.util.List.of(), "end_turn", 100, 30));

        ScoreRelevanceTool tool = new ScoreRelevanceTool(ai, "claude-haiku-4-5-20251001");
        String input = """
            {
              "user_interests": ["spring_boot","react"],
              "items": [
                {"id": 1, "title": "Spring Boot 3.5"},
                {"id": 2, "title": "Linux gaming benchmarks"}
              ]
            }
            """;

        String result = tool.execute(input);
        ObjectMapper om = new ObjectMapper();
        JsonNode arr = om.readTree(result);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isEqualTo(2);
        assertThat(arr.get(0).get("score").asDouble()).isGreaterThan(arr.get(1).get("score").asDouble());
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=ScoreRelevanceToolTest test
```

- [ ] **Step 3: Implement `ScoreRelevanceTool.java`**

```java
package com.devradar.ai.tools;

import com.devradar.ai.AiClient;
import com.devradar.ai.AiMessage;
import com.devradar.ai.AiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScoreRelevanceTool {

    public static final String NAME = "scoreRelevance";
    public static final String DESCRIPTION = "Score how relevant a small batch of items is to the user's interests. Returns array of {id, score 0..1}.";
    public static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "user_interests": { "type": "array", "items": {"type": "string"} },
            "items": { "type": "array", "items": { "type": "object",
              "properties": { "id": {"type": "integer"}, "title": {"type": "string"} },
              "required": ["id", "title"] } }
          },
          "required": ["user_interests", "items"]
        }
        """;

    private static final String SYSTEM = "You are a relevance scorer. Given a user's interest tags and a batch of items (id + title), output a JSON array: [{\"id\":N,\"score\":0..1}, ...]. Score = how likely this user wants to read this. Output ONLY the JSON array, no prose.";

    private final AiClient ai;
    private final String model;

    public ScoreRelevanceTool(AiClient ai, @Value("${anthropic.scoring-model}") String model) {
        this.ai = ai; this.model = model;
    }

    public ToolDefinition definition() {
        return new ToolDefinition(NAME, DESCRIPTION, INPUT_SCHEMA);
    }

    public String execute(String inputJson) {
        AiResponse r = ai.generate(model, SYSTEM, List.of(AiMessage.userText(inputJson)), List.of(), 512);
        return r.text();
    }
}
```

- [ ] **Step 4: Run — expect 1/1 PASS**

```bash
cd backend && mvn -Dtest=ScoreRelevanceToolTest test
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ai/tools/ScoreRelevanceTool.java backend/src/test/java/com/devradar/ai/tools/ScoreRelevanceToolTest.java
git commit -m "feat(ai): add ScoreRelevanceTool calling Haiku for cheap batch scoring"
```

---

## Task 10: GetItemDetailTool — TDD

**Files:**
- Create: `backend/src/test/java/com/devradar/ai/tools/GetItemDetailToolTest.java`
- Create: `backend/src/main/java/com/devradar/ai/tools/GetItemDetailTool.java`

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/devradar/ai/tools/GetItemDetailToolTest.java
package com.devradar.ai.tools;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.SourceItem;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GetItemDetailToolTest extends AbstractIntegrationTest {

    @Autowired GetItemDetailTool tool;
    @Autowired SourceItemRepository items;
    @Autowired SourceRepository sources;
    @Autowired ObjectMapper json;

    @Test
    void execute_returnsItemFields() throws Exception {
        var hn = sources.findByCode("HN").orElseThrow();
        SourceItem si = new SourceItem();
        si.setSourceId(hn.getId());
        si.setExternalId("detail-1");
        si.setUrl("https://example.com/detail-1");
        si.setTitle("Detail item");
        si.setAuthor("alice");
        si.setPostedAt(Instant.now());
        si.setRawPayload("{\"k\":\"v\"}");
        items.save(si);

        String input = "{\"id\": " + si.getId() + "}";
        String result = tool.execute(input);

        JsonNode n = json.readTree(result);
        assertThat(n.get("title").asText()).isEqualTo("Detail item");
        assertThat(n.get("url").asText()).contains("detail-1");
        assertThat(n.get("author").asText()).isEqualTo("alice");
    }

    @Test
    void execute_unknownId_returnsError() throws Exception {
        String result = tool.execute("{\"id\": 999999}");
        JsonNode n = json.readTree(result);
        assertThat(n.has("error")).isTrue();
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=GetItemDetailToolTest test
```

- [ ] **Step 3: Implement `GetItemDetailTool.java`**

```java
package com.devradar.ai.tools;

import com.devradar.repository.SourceItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class GetItemDetailTool {

    public static final String NAME = "getItemDetail";
    public static final String DESCRIPTION = "Get the full details (title, url, author, posted_at, raw_payload) of one source_item by id.";
    public static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "id": { "type": "integer", "description": "source_items.id" }
          },
          "required": ["id"]
        }
        """;

    private final SourceItemRepository repo;
    private final ObjectMapper json = new ObjectMapper();

    public GetItemDetailTool(SourceItemRepository repo) { this.repo = repo; }

    public ToolDefinition definition() {
        return new ToolDefinition(NAME, DESCRIPTION, INPUT_SCHEMA);
    }

    public String execute(String inputJson) {
        try {
            long id = json.readTree(inputJson).get("id").asLong();
            var maybe = repo.findById(id);
            if (maybe.isEmpty()) return "{\"error\":\"item not found: " + id + "\"}";
            var si = maybe.get();
            ObjectNode n = json.createObjectNode();
            n.put("id", si.getId());
            n.put("external_id", si.getExternalId());
            n.put("title", si.getTitle());
            n.put("url", si.getUrl());
            n.put("author", si.getAuthor());
            n.put("posted_at", si.getPostedAt().toString());
            if (si.getRawPayload() != null) n.set("raw_payload", json.readTree(si.getRawPayload()));
            return json.writeValueAsString(n);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
```

- [ ] **Step 4: Run — expect 2/2 PASS**

```bash
cd backend && mvn -Dtest=GetItemDetailToolTest test
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ai/tools/GetItemDetailTool.java backend/src/test/java/com/devradar/ai/tools/GetItemDetailToolTest.java
git commit -m "feat(ai): add GetItemDetailTool returning full source_item by id"
```

---

## Task 11: ToolRegistry — dispatcher

**File:** `backend/src/main/java/com/devradar/ai/tools/ToolRegistry.java`

- [ ] **Step 1: Implement registry**

```java
package com.devradar.ai.tools;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolRegistry {

    private final SearchItemsTool search;
    private final ScoreRelevanceTool score;
    private final GetItemDetailTool detail;

    public ToolRegistry(SearchItemsTool search, ScoreRelevanceTool score, GetItemDetailTool detail) {
        this.search = search; this.score = score; this.detail = detail;
    }

    public List<ToolDefinition> definitions() {
        return List.of(search.definition(), score.definition(), detail.definition());
    }

    /** Dispatch a tool call by name. Returns the JSON string the tool produced (errors included as {"error":...}). */
    public String dispatch(String name, String inputJson) {
        return switch (name) {
            case SearchItemsTool.NAME -> search.execute(inputJson);
            case ScoreRelevanceTool.NAME -> score.execute(inputJson);
            case GetItemDetailTool.NAME -> detail.execute(inputJson);
            default -> "{\"error\":\"unknown tool: " + name + "\"}";
        };
    }
}
```

- [ ] **Step 2: Verify compile + commit**

```bash
cd backend && mvn -DskipTests compile
git add backend/src/main/java/com/devradar/ai/tools/ToolRegistry.java
git commit -m "feat(ai): add ToolRegistry exposing definitions + dispatching by name"
```

---

## Task 12: RadarOrchestrator — agent loop with TDD via RecordedAiClient

**Files:**
- Create: `backend/src/test/java/com/devradar/ai/RecordedAiClient.java`
- Create: `backend/src/test/java/com/devradar/ai/RadarOrchestratorTest.java`
- Create: `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java`

The orchestrator runs the multi-turn tool-call loop. It calls `AiClient.generate(...)`, parses tool_calls, executes them via `ToolRegistry`, sends results back, repeats until `stop_reason="end_turn"` or max iterations.

It emits a `RadarOrchestrationResult` containing the final themes (parsed from the assistant's last text) plus token totals.

- [ ] **Step 1: Create `RecordedAiClient` test fixture**

```java
// backend/src/test/java/com/devradar/ai/RecordedAiClient.java
package com.devradar.ai;

import com.devradar.ai.tools.ToolDefinition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Replays a queue of canned AiResponses; used in tests to script the agent loop. */
public class RecordedAiClient implements AiClient {

    private final Deque<AiResponse> queue = new ArrayDeque<>();

    public void enqueue(AiResponse r) { queue.add(r); }

    @Override
    public AiResponse generate(String model, String systemPrompt, List<AiMessage> messages, List<ToolDefinition> tools, int maxTokens) {
        if (queue.isEmpty()) throw new IllegalStateException("No more recorded responses");
        return queue.poll();
    }
}
```

- [ ] **Step 2: Write failing test**

```java
// backend/src/test/java/com/devradar/ai/RadarOrchestratorTest.java
package com.devradar.ai;

import com.devradar.ai.tools.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RadarOrchestratorTest {

    @Test
    void runs_toolCallLoop_untilEndTurn() {
        RecordedAiClient ai = new RecordedAiClient();
        // Turn 1: model wants to call searchItems
        ai.enqueue(new AiResponse("", List.of(new AiToolCall("call_1", "searchItems", "{\"tag_slugs\":[\"spring_boot\"]}")),
            "tool_use", 100, 20));
        // Turn 2: end_turn with structured radar text
        ai.enqueue(new AiResponse("""
            {"themes":[
              {"title":"Spring Boot 3.5 ships","summary":"VTs default for @Async","item_ids":[1,2]}
            ]}
            """, List.of(), "end_turn", 80, 50));

        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.definitions()).thenReturn(List.of());
        when(tools.dispatch("searchItems", "{\"tag_slugs\":[\"spring_boot\"]}")).thenReturn("[{\"id\":1,\"title\":\"sb 3.5\"},{\"id\":2,\"title\":\"sb perf\"}]");

        RadarOrchestrator orch = new RadarOrchestrator(ai, tools, "claude-sonnet-4-6", 8, 4096);

        var result = orch.generate(List.of("spring_boot"), List.of(1L, 2L, 3L));

        assertThat(result.themes()).hasSize(1);
        assertThat(result.themes().get(0).title()).isEqualTo("Spring Boot 3.5 ships");
        assertThat(result.themes().get(0).itemIds()).containsExactly(1L, 2L);
        assertThat(result.totalInputTokens()).isEqualTo(180);
        assertThat(result.totalOutputTokens()).isEqualTo(70);
        verify(tools).dispatch("searchItems", "{\"tag_slugs\":[\"spring_boot\"]}");
    }

    @Test
    void respects_maxIterations_evenIfModelKeepsCallingTools() {
        RecordedAiClient ai = new RecordedAiClient();
        for (int i = 0; i < 5; i++) {
            ai.enqueue(new AiResponse("", List.of(new AiToolCall("call_" + i, "searchItems", "{}")), "tool_use", 50, 10));
        }
        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.definitions()).thenReturn(List.of());
        when(tools.dispatch(eq("searchItems"), anyString())).thenReturn("[]");

        RadarOrchestrator orch = new RadarOrchestrator(ai, tools, "claude-sonnet-4-6", 3, 4096);

        var result = orch.generate(List.of(), List.of());
        // 3 iterations, then we stop with whatever themes (none in this case)
        assertThat(result.themes()).isEmpty();
        verify(tools, times(3)).dispatch(eq("searchItems"), anyString());
    }
}
```

- [ ] **Step 3: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=RadarOrchestratorTest test
```

- [ ] **Step 4: Implement `RadarOrchestrator.java`**

```java
package com.devradar.ai;

import com.devradar.ai.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RadarOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(RadarOrchestrator.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are a tech radar analyst. Given a user's interest tags and a pool of recently ingested items,
        identify 3-5 themes that genuinely matter to this user. Use the provided tools to refine your candidate
        set. When you are done investigating, output a single JSON object with NO PROSE around it:

        {"themes": [
          {"title": "...", "summary": "...", "item_ids": [<source_item ids cited>]},
          ...
        ]}

        Each theme should:
        - Have a title under 100 chars.
        - Have a summary 1-3 sentences citing why it matters to this user specifically.
        - Reference 1-5 source_item ids from your search results.
        Do not invent ids — only cite ids you've seen in tool results.
        """;

    private final AiClient ai;
    private final ToolRegistry tools;
    private final String model;
    private final int maxIterations;
    private final int maxTokens;

    public RadarOrchestrator(AiClient ai, ToolRegistry tools,
                             @Value("${anthropic.orchestrator-model}") String model,
                             @Value("${anthropic.max-tool-iterations}") int maxIterations,
                             @Value("${anthropic.max-tokens-per-call}") int maxTokens) {
        this.ai = ai; this.tools = tools; this.model = model;
        this.maxIterations = maxIterations; this.maxTokens = maxTokens;
    }

    public RadarOrchestrationResult generate(List<String> userInterests, List<Long> candidateItemIds) {
        String userMsg = """
            User interests: %s
            Candidate item ids (from last 7 days, pre-filtered to user's tags): %s
            
            Use the tools to look up titles, score relevance, fetch full details, and produce the final themes JSON.
            """.formatted(userInterests, candidateItemIds);

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.userText(userMsg));

        int totalIn = 0, totalOut = 0;
        String lastText = "";

        for (int iter = 0; iter < maxIterations; iter++) {
            AiResponse resp = ai.generate(model, SYSTEM_PROMPT, messages, tools.definitions(), maxTokens);
            totalIn += resp.inputTokens();
            totalOut += resp.outputTokens();
            lastText = resp.text() != null && !resp.text().isBlank() ? resp.text() : lastText;

            if (resp.toolCalls().isEmpty() || "end_turn".equals(resp.stopReason())) break;

            // Execute tool calls and feed results back as a user message
            List<AiToolResult> results = new ArrayList<>();
            for (AiToolCall call : resp.toolCalls()) {
                String out = tools.dispatch(call.name(), call.inputJson());
                boolean isError = out != null && out.contains("\"error\"");
                results.add(new AiToolResult(call.id(), out, isError));
                LOG.debug("tool dispatched name={} resultLen={}", call.name(), out == null ? 0 : out.length());
            }
            messages.add(AiMessage.userToolResults(results));
        }

        // Parse the final JSON object from lastText
        List<RadarOrchestrationTheme> themes = new ArrayList<>();
        try {
            JsonNode root = JSON.readTree(extractJsonObject(lastText));
            JsonNode arr = root.get("themes");
            if (arr != null && arr.isArray()) {
                int order = 0;
                for (JsonNode t : arr) {
                    String title = t.path("title").asText();
                    String summary = t.path("summary").asText();
                    List<Long> ids = new ArrayList<>();
                    for (JsonNode i : t.path("item_ids")) ids.add(i.asLong());
                    themes.add(new RadarOrchestrationTheme(title, summary, ids, order++));
                }
            }
        } catch (Exception e) {
            LOG.warn("failed to parse final radar JSON; returning empty themes. err={}", e.toString());
        }

        return new RadarOrchestrationResult(themes, totalIn, totalOut);
    }

    /** Find the first {...} JSON object in the text and return that substring. */
    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) return "{}";
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return text.substring(start, i + 1); }
        }
        return "{}";
    }

    public record RadarOrchestrationResult(List<RadarOrchestrationTheme> themes, int totalInputTokens, int totalOutputTokens) {}
    public record RadarOrchestrationTheme(String title, String summary, List<Long> itemIds, int displayOrder) {}
}
```

- [ ] **Step 5: Run — expect 2/2 PASS**

```bash
cd backend && mvn -Dtest=RadarOrchestratorTest test
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/ai/RadarOrchestrator.java backend/src/test/java/com/devradar/ai/
git commit -m "feat(ai): add RadarOrchestrator multi-turn agent loop with tool dispatch"
```

---

## Task 13: RadarEventBus — in-memory event publisher

**Files:**
- Create: `backend/src/main/java/com/devradar/radar/RadarEventBus.java`
- Create: `backend/src/main/java/com/devradar/radar/event/RadarStartedEvent.java`
- Create: `backend/src/main/java/com/devradar/radar/event/ThemeCompleteEvent.java`
- Create: `backend/src/main/java/com/devradar/radar/event/RadarCompleteEvent.java`
- Create: `backend/src/main/java/com/devradar/radar/event/RadarFailedEvent.java`

- [ ] **Step 1: Create event records**

```java
// backend/src/main/java/com/devradar/radar/event/RadarStartedEvent.java
package com.devradar.radar.event;
public record RadarStartedEvent(Long radarId) {}
```

```java
// backend/src/main/java/com/devradar/radar/event/ThemeCompleteEvent.java
package com.devradar.radar.event;
import java.util.List;
public record ThemeCompleteEvent(Long radarId, Long themeId, String title, String summary, List<Long> itemIds, int displayOrder) {}
```

```java
// backend/src/main/java/com/devradar/radar/event/RadarCompleteEvent.java
package com.devradar.radar.event;
public record RadarCompleteEvent(Long radarId, long generationMs, int tokenCount) {}
```

```java
// backend/src/main/java/com/devradar/radar/event/RadarFailedEvent.java
package com.devradar.radar.event;
public record RadarFailedEvent(Long radarId, String errorCode, String message) {}
```

- [ ] **Step 2: Create `RadarEventBus`**

```java
package com.devradar.radar;

import com.devradar.radar.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory event bus: per-radar list of SseEmitters. Events are pushed synchronously to subscribers. */
@Component
public class RadarEventBus {

    private static final Logger LOG = LoggerFactory.getLogger(RadarEventBus.class);

    private final ConcurrentHashMap<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long radarId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout — long-lived
        subscribers.computeIfAbsent(radarId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(radarId, emitter));
        emitter.onTimeout(() -> remove(radarId, emitter));
        emitter.onError(e -> remove(radarId, emitter));
        return emitter;
    }

    public void publishStarted(RadarStartedEvent event) { send(event.radarId(), "radar.started", event); }
    public void publishThemeComplete(ThemeCompleteEvent event) { send(event.radarId(), "theme.complete", event); }
    public void publishComplete(RadarCompleteEvent event) {
        send(event.radarId(), "radar.complete", event);
        completeAll(event.radarId());
    }
    public void publishFailed(RadarFailedEvent event) {
        send(event.radarId(), "radar.failed", event);
        completeAll(event.radarId());
    }

    private void send(Long radarId, String eventName, Object data) {
        List<SseEmitter> list = subscribers.get(radarId);
        if (list == null) return;
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException ex) {
                LOG.debug("subscriber dropped for radar={}: {}", radarId, ex.toString());
            }
        }
    }

    private void completeAll(Long radarId) {
        List<SseEmitter> list = subscribers.remove(radarId);
        if (list == null) return;
        for (SseEmitter e : list) {
            try { e.complete(); } catch (Exception ignored) {}
        }
    }

    private void remove(Long radarId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(radarId);
        if (list != null) list.remove(emitter);
    }
}
```

- [ ] **Step 3: Verify compile + commit**

```bash
cd backend && mvn -DskipTests compile
git add backend/src/main/java/com/devradar/radar/RadarEventBus.java backend/src/main/java/com/devradar/radar/event/
git commit -m "feat(radar): add in-memory RadarEventBus + SSE event records"
```

---

## Task 14: RadarGenerationService (@Async) + RadarService

**Files:**
- Create: `backend/src/main/java/com/devradar/radar/RadarService.java`
- Create: `backend/src/main/java/com/devradar/radar/RadarGenerationService.java`

- [ ] **Step 1: Create `RadarService.java`**

```java
package com.devradar.radar;

import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.repository.RadarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class RadarService {

    private final RadarRepository repo;
    public RadarService(RadarRepository repo) { this.repo = repo; }

    @Transactional
    public Radar createPending(Long userId) {
        Radar r = new Radar();
        r.setUserId(userId);
        r.setPeriodEnd(Instant.now());
        r.setPeriodStart(Instant.now().minus(7, ChronoUnit.DAYS));
        r.setStatus(RadarStatus.GENERATING);
        return repo.save(r);
    }

    @Transactional
    public void markReady(Long radarId, long generationMs, int tokenCount) {
        Radar r = repo.findById(radarId).orElseThrow();
        r.setStatus(RadarStatus.READY);
        r.setGeneratedAt(Instant.now());
        r.setGenerationMs(generationMs);
        r.setTokenCount(tokenCount);
        repo.save(r);
    }

    @Transactional
    public void markFailed(Long radarId, String errorCode, String message) {
        Radar r = repo.findById(radarId).orElseThrow();
        r.setStatus(RadarStatus.FAILED);
        r.setErrorCode(errorCode);
        r.setErrorMessage(message);
        repo.save(r);
    }
}
```

- [ ] **Step 2: Create `RadarGenerationService.java`**

```java
package com.devradar.radar;

import com.devradar.ai.AiSummaryCache;
import com.devradar.ai.RadarOrchestrator;
import com.devradar.domain.RadarTheme;
import com.devradar.domain.RadarThemeItem;
import com.devradar.repository.RadarThemeItemRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.radar.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RadarGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(RadarGenerationService.class);

    private final RadarOrchestrator orchestrator;
    private final RadarService radarService;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeItemRepository themeItemRepo;
    private final AiSummaryCache cache;
    private final RadarEventBus events;

    public RadarGenerationService(
        RadarOrchestrator orchestrator,
        RadarService radarService,
        RadarThemeRepository themeRepo,
        RadarThemeItemRepository themeItemRepo,
        AiSummaryCache cache,
        RadarEventBus events
    ) {
        this.orchestrator = orchestrator;
        this.radarService = radarService;
        this.themeRepo = themeRepo;
        this.themeItemRepo = themeItemRepo;
        this.cache = cache;
        this.events = events;
    }

    /** Fired by RadarApplicationService.create(). Runs asynchronously. */
    @Async("radarGenerationExecutor")
    public void runGeneration(Long radarId, Long userId, List<String> userInterests, List<Long> candidateIds) {
        long t0 = System.currentTimeMillis();
        events.publishStarted(new RadarStartedEvent(radarId));
        try {
            var result = orchestrator.generate(userInterests, candidateIds);
            persistAndStream(radarId, result.themes());
            long elapsed = System.currentTimeMillis() - t0;
            int tokens = result.totalInputTokens() + result.totalOutputTokens();
            radarService.markReady(radarId, elapsed, tokens);
            events.publishComplete(new RadarCompleteEvent(radarId, elapsed, tokens));
        } catch (Exception e) {
            LOG.error("radar generation failed radar={}: {}", radarId, e.toString(), e);
            radarService.markFailed(radarId, "GENERATION_FAILED", e.getMessage());
            events.publishFailed(new RadarFailedEvent(radarId, "GENERATION_FAILED", e.getMessage()));
        }
    }

    /**
     * Persist themes + items and publish SSE events. NOT @Transactional — Spring's proxy is bypassed
     * when called from same-class runGeneration(). Each repo.save() runs in its own auto-tx (Spring
     * Data JPA default). Acceptable for ingestion-style writes; partial failure leaves an orphan
     * theme/items pair, which we accept rather than complicating the design.
     */
    private void persistAndStream(Long radarId, List<RadarOrchestrator.RadarOrchestrationTheme> themes) {
        for (var t : themes) {
            // Cross-user summary cache: if we've seen this exact item set before, reuse the cached summary.
            String summary = cache.get(t.itemIds()).orElseGet(() -> {
                cache.put(t.itemIds(), t.summary());
                return t.summary();
            });

            RadarTheme theme = new RadarTheme();
            theme.setRadarId(radarId);
            theme.setTitle(t.title());
            theme.setSummary(summary);
            theme.setDisplayOrder(t.displayOrder());
            theme = themeRepo.save(theme);

            int order = 0;
            for (Long itemId : t.itemIds()) {
                RadarThemeItem rti = new RadarThemeItem();
                rti.setThemeId(theme.getId());
                rti.setSourceItemId(itemId);
                rti.setDisplayOrder(order++);
                themeItemRepo.save(rti);
            }

            events.publishThemeComplete(new ThemeCompleteEvent(radarId, theme.getId(), theme.getTitle(), theme.getSummary(), t.itemIds(), theme.getDisplayOrder()));
        }
    }
}
```

- [ ] **Step 3: Verify compile + commit**

```bash
cd backend && mvn -DskipTests compile
git add backend/src/main/java/com/devradar/radar/RadarService.java backend/src/main/java/com/devradar/radar/RadarGenerationService.java
git commit -m "feat(radar): add RadarService + @Async RadarGenerationService"
```

---

## Task 15: Radar DTOs

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/dto/RadarSummaryDTO.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/RadarItemDTO.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/RadarThemeDTO.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/RadarDetailDTO.java`

- [ ] **Step 1: Create the four records**

```java
// backend/src/main/java/com/devradar/web/rest/dto/RadarItemDTO.java
package com.devradar.web.rest.dto;
public record RadarItemDTO(Long id, String title, String url, String author) {}
```

```java
// backend/src/main/java/com/devradar/web/rest/dto/RadarThemeDTO.java
package com.devradar.web.rest.dto;
import java.util.List;
public record RadarThemeDTO(Long id, String title, String summary, int displayOrder, List<RadarItemDTO> items) {}
```

```java
// backend/src/main/java/com/devradar/web/rest/dto/RadarSummaryDTO.java
package com.devradar.web.rest.dto;
import com.devradar.domain.RadarStatus;
import java.time.Instant;
public record RadarSummaryDTO(Long id, RadarStatus status, Instant periodStart, Instant periodEnd, Instant generatedAt, Long generationMs, Integer tokenCount) {}
```

```java
// backend/src/main/java/com/devradar/web/rest/dto/RadarDetailDTO.java
package com.devradar.web.rest.dto;
import com.devradar.domain.RadarStatus;
import java.time.Instant;
import java.util.List;
public record RadarDetailDTO(Long id, RadarStatus status, Instant periodStart, Instant periodEnd, Instant generatedAt, Long generationMs, Integer tokenCount, List<RadarThemeDTO> themes) {}
```

- [ ] **Step 2: Verify compile + commit**

```bash
cd backend && mvn -DskipTests compile
git add backend/src/main/java/com/devradar/web/rest/dto/RadarSummaryDTO.java backend/src/main/java/com/devradar/web/rest/dto/RadarItemDTO.java backend/src/main/java/com/devradar/web/rest/dto/RadarThemeDTO.java backend/src/main/java/com/devradar/web/rest/dto/RadarDetailDTO.java
git commit -m "feat(radar): add Radar* DTOs for REST surface"
```

---

## Task 16: RadarApplicationService

**File:** `backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java`

- [ ] **Step 1: Implement**

```java
package com.devradar.radar.application;

import com.devradar.domain.InterestTag;
import com.devradar.domain.Radar;
import com.devradar.domain.SourceItem;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.repository.*;
import com.devradar.security.SecurityUtils;
import com.devradar.radar.RadarGenerationService;
import com.devradar.radar.RadarService;
import com.devradar.service.UserInterestService;
import com.devradar.web.rest.dto.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RadarApplicationService {

    private final RadarService radarService;
    private final RadarGenerationService generation;
    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeItemRepository themeItemRepo;
    private final SourceItemRepository sourceItemRepo;
    private final UserInterestService interests;

    @PersistenceContext private EntityManager em;

    public RadarApplicationService(
        RadarService radarService,
        RadarGenerationService generation,
        RadarRepository radarRepo,
        RadarThemeRepository themeRepo,
        RadarThemeItemRepository themeItemRepo,
        SourceItemRepository sourceItemRepo,
        UserInterestService interests
    ) {
        this.radarService = radarService;
        this.generation = generation;
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.themeItemRepo = themeItemRepo;
        this.sourceItemRepo = sourceItemRepo;
        this.interests = interests;
    }

    public RadarSummaryDTO createForCurrentUser() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();

        List<InterestTag> userTags = interests.findInterestsForUser(uid);
        List<String> slugs = userTags.stream().map(InterestTag::getSlug).toList();
        List<Long> candidateIds = preFilterCandidates(slugs);

        Radar created = radarService.createPending(uid);
        generation.runGeneration(created.getId(), uid, slugs, candidateIds);
        return summary(created);
    }

    public RadarDetailDTO get(Long radarId) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        Radar r = radarRepo.findById(radarId).orElseThrow();
        if (!r.getUserId().equals(uid)) throw new RuntimeException("forbidden");

        var themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(radarId);
        List<RadarThemeDTO> themeDtos = new ArrayList<>();
        for (var t : themes) {
            var rtis = themeItemRepo.findByThemeIdOrderByDisplayOrderAsc(t.getId());
            List<RadarItemDTO> itemDtos = new ArrayList<>();
            for (var rti : rtis) {
                SourceItem si = sourceItemRepo.findById(rti.getSourceItemId()).orElse(null);
                if (si == null) continue;
                itemDtos.add(new RadarItemDTO(si.getId(), si.getTitle(), si.getUrl(), si.getAuthor()));
            }
            themeDtos.add(new RadarThemeDTO(t.getId(), t.getTitle(), t.getSummary(), t.getDisplayOrder(), itemDtos));
        }
        return new RadarDetailDTO(r.getId(), r.getStatus(), r.getPeriodStart(), r.getPeriodEnd(), r.getGeneratedAt(), r.getGenerationMs(), r.getTokenCount(), themeDtos);
    }

    public Page<RadarSummaryDTO> list(Pageable pageable) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return radarRepo.findByUserIdOrderByGeneratedAtDesc(uid, pageable).map(this::summary);
    }

    private RadarSummaryDTO summary(Radar r) {
        return new RadarSummaryDTO(r.getId(), r.getStatus(), r.getPeriodStart(), r.getPeriodEnd(), r.getGeneratedAt(), r.getGenerationMs(), r.getTokenCount());
    }

    @SuppressWarnings("unchecked")
    private List<Long> preFilterCandidates(List<String> slugs) {
        if (slugs.isEmpty()) return List.of();
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        return em.createQuery(
            "SELECT DISTINCT si.id FROM SourceItem si, SourceItemTag sit, InterestTag it " +
            "WHERE sit.sourceItemId = si.id AND sit.interestTagId = it.id " +
            "AND it.slug IN :slugs AND si.postedAt > :cutoff " +
            "ORDER BY si.postedAt DESC")
            .setParameter("slugs", slugs)
            .setParameter("cutoff", cutoff)
            .setMaxResults(200)
            .getResultList();
    }
}
```

- [ ] **Step 2: Verify compile + commit**

```bash
cd backend && mvn -DskipTests compile
git add backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java
git commit -m "feat(radar): add RadarApplicationService — create + get + list radars"
```

---

## Task 17: RadarResource (REST) + RadarSseResource (SSE)

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/RadarResource.java`
- Create: `backend/src/main/java/com/devradar/web/rest/RadarSseResource.java`

- [ ] **Step 1: Create REST controller**

```java
// backend/src/main/java/com/devradar/web/rest/RadarResource.java
package com.devradar.web.rest;

import com.devradar.radar.application.RadarApplicationService;
import com.devradar.web.rest.dto.RadarDetailDTO;
import com.devradar.web.rest.dto.RadarSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/radars")
public class RadarResource {

    private final RadarApplicationService app;
    public RadarResource(RadarApplicationService app) { this.app = app; }

    @PostMapping
    public ResponseEntity<RadarSummaryDTO> create() {
        RadarSummaryDTO created = app.createForCurrentUser();
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/{id}")
    public RadarDetailDTO get(@PathVariable Long id) {
        return app.get(id);
    }

    @GetMapping
    public Page<RadarSummaryDTO> list(Pageable pageable) {
        return app.list(pageable);
    }
}
```

- [ ] **Step 2: Create SSE controller**

```java
// backend/src/main/java/com/devradar/web/rest/RadarSseResource.java
package com.devradar.web.rest;

import com.devradar.radar.RadarEventBus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/radars")
public class RadarSseResource {

    private final RadarEventBus events;
    public RadarSseResource(RadarEventBus events) { this.events = events; }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        return events.subscribe(id);
    }
}
```

- [ ] **Step 3: Verify compile + commit**

```bash
cd backend && mvn -DskipTests compile
git add backend/src/main/java/com/devradar/web/rest/RadarResource.java backend/src/main/java/com/devradar/web/rest/RadarSseResource.java
git commit -m "feat(web): add RadarResource (REST) + RadarSseResource (SSE)"
```

---

## Task 18: End-to-end IT — generate radar with mocked AiClient

**File:** `backend/src/test/java/com/devradar/radar/RadarGenerationServiceIT.java`

- [ ] **Step 1: Write the test**

This test wires everything together: real MySQL + real Redis + real RadarOrchestrator + a mocked AiClient that returns canned responses. It asserts that themes get persisted, the radar transitions to READY, and event bus fires the right events.

```java
package com.devradar.radar;

import com.devradar.AbstractIntegrationTest;
import com.devradar.ai.AiClient;
import com.devradar.ai.AiResponse;
import com.devradar.domain.*;
import com.devradar.repository.*;
import com.devradar.security.JwtUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class RadarGenerationServiceIT extends AbstractIntegrationTest {

    @MockBean AiClient ai;

    @Autowired UserRepository userRepo;
    @Autowired UserInterestRepository userInterestRepo;
    @Autowired InterestTagRepository tagRepo;
    @Autowired SourceRepository sourceRepo;
    @Autowired SourceItemRepository sourceItemRepo;
    @Autowired SourceItemTagRepository sourceItemTagRepo;
    @Autowired RadarRepository radarRepo;
    @Autowired RadarThemeRepository themeRepo;

    @Autowired com.devradar.radar.application.RadarApplicationService app;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void createRadar_runsAgentLoop_persistsThemes_andMarksReady() throws Exception {
        // Given a user with one interest, one source_item tagged with that interest
        User u = new User();
        u.setEmail("radar@example.com");
        u.setDisplayName("R");
        u.setPasswordHash("$2a$12$abcdefghijklmnopqrstuv");
        u.setActive(true);
        u = userRepo.save(u);

        InterestTag spring = tagRepo.findBySlug("spring_boot").orElseThrow();
        userInterestRepo.save(new UserInterest(u.getId(), spring.getId()));

        Source hn = sourceRepo.findByCode("HN").orElseThrow();
        SourceItem si = new SourceItem();
        si.setSourceId(hn.getId());
        si.setExternalId("rad-1");
        si.setUrl("https://example.com/sb35");
        si.setTitle("Spring Boot 3.5");
        si.setPostedAt(Instant.now());
        si = sourceItemRepo.save(si);
        sourceItemTagRepo.save(new SourceItemTag(si.getId(), spring.getId()));
        Long siId = si.getId();

        // Given the AI returns a single end_turn with the radar JSON
        when(ai.generate(anyString(), anyString(), anyList(), anyList(), anyInt()))
            .thenReturn(new AiResponse(
                "{\"themes\":[{\"title\":\"Spring 3.5\",\"summary\":\"VTs default\",\"item_ids\":[" + siId + "]}]}",
                List.of(), "end_turn", 200, 100));

        // Auth context for the user
        var auth = new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of());
        auth.setDetails(new JwtUserDetails(u.getId(), u.getEmail()));
        SecurityContextHolder.getContext().setAuthentication(auth);

        var summary = app.createForCurrentUser();
        Long radarId = summary.id();

        await().atMost(15, TimeUnit.SECONDS).until(() -> {
            var r = radarRepo.findById(radarId).orElseThrow();
            return r.getStatus() == RadarStatus.READY;
        });

        var themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(radarId);
        assertThat(themes).hasSize(1);
        assertThat(themes.get(0).getTitle()).isEqualTo("Spring 3.5");
    }
}
```

- [ ] **Step 2: Add Awaitility dep to pom.xml (if not already present)**

```xml
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <version>4.2.2</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 3: Run — expect 1/1 PASS**

```bash
cd backend && mvn -Dtest=RadarGenerationServiceIT test
```

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml backend/src/test/java/com/devradar/radar/RadarGenerationServiceIT.java
git commit -m "test(radar): add end-to-end RadarGenerationService IT with mocked AiClient"
```

---

## Task 19: README + manual smoke

**File:** `backend/README.md`

- [ ] **Step 1: Append a "Plan 3 — AI Radar Generation" section**

```markdown

## Plan 3 — AI Radar Generation

Multi-step Anthropic tool-calling agent loop that turns ingested `source_items` into personalized themed radars.

### Architecture

| Component | Role |
|---|---|
| `AiClient` (interface) | Provider-agnostic chat-with-tools abstraction |
| `AnthropicAiClient` | Real impl using `com.anthropic:anthropic-java` |
| `RecordedAiClient` (test) | Replays canned responses for deterministic agent loop tests |
| `SearchItemsTool` | Query `source_items` by interest tag slugs |
| `ScoreRelevanceTool` | Cheap Haiku call to score a small batch by relevance |
| `GetItemDetailTool` | Fetch full item details by id |
| `RadarOrchestrator` | Multi-turn agent loop; max 8 iterations by default |
| `RadarGenerationService` | `@Async` runner; persists themes; publishes SSE events |
| `AiSummaryCache` | Redis cache keyed by sorted source_item_id list |
| `RadarEventBus` | In-memory per-radar SseEmitter list |

### Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/radars` | Create radar; returns 201 + radar_id immediately; generation runs async |
| GET | `/api/radars/{id}` | Full radar with themes + cited items |
| GET | `/api/radars` | Paginated list of your radars |
| GET | `/api/radars/{id}/stream` | SSE: `radar.started`, `theme.complete`, `radar.complete`, `radar.failed` |

### Configuration

| Property | Default | Description |
|---|---|---|
| `anthropic.api-key` | `${ANTHROPIC_API_KEY}` | Required at runtime |
| `anthropic.orchestrator-model` | `claude-sonnet-4-6` | Sonnet for the agent loop |
| `anthropic.scoring-model` | `claude-haiku-4-5-20251001` | Haiku for cheap relevance scoring |
| `anthropic.max-tool-iterations` | `8` | Hard cap on agent loop turns |
| `anthropic.max-tokens-per-call` | `4096` | Max output tokens per Sonnet call |

### Local manual radar generation

Requires a real `ANTHROPIC_API_KEY` and ingested data:

```bash
cd backend
export ANTHROPIC_API_KEY=sk-ant-...
DB_HOST_PORT=${DB_HOST_PORT:-3307} docker compose up -d
DB_HOST_PORT=${DB_HOST_PORT:-3307} mvn spring-boot:run &
sleep 25  # wait for app + first ingestion fire

# Register + login + set interests + create radar
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"radar@me","password":"Password1!","displayName":"Me"}' && \
  curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"radar@me","password":"Password1!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -s -X PUT localhost:8080/api/users/me/interests -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"tagSlugs":["spring_boot","react","mysql"]}'

RADAR_ID=$(curl -s -X POST localhost:8080/api/radars -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')

# Stream the SSE events as the agent runs
curl -N -H "Authorization: Bearer $TOKEN" "localhost:8080/api/radars/$RADAR_ID/stream"
```

You should see `radar.started`, then `theme.complete` events as the agent finalizes each theme, then `radar.complete`. Then GET `/api/radars/$RADAR_ID` returns the full radar.
```

- [ ] **Step 2: Final verification**

```bash
cd backend && mvn -B verify
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/README.md
git commit -m "docs(backend): document Plan 3 AI radar generation architecture + endpoints"
```

---

## Plan 3 Done — End-to-End Verification

- [ ] **Step 1: Run the full test suite**

```bash
cd backend && mvn -B verify
```
Expected: BUILD SUCCESS, all P1 + P2 + P3 tests pass.

- [ ] **Step 2: Optional live smoke**

If you have a real `ANTHROPIC_API_KEY` and want to see real Claude tool calls, run the curl flow from the README. Otherwise, the integration tests cover the same code paths with a mocked AiClient.

Plan 3 complete. Move to **Plan 4: GitHub OAuth + Auto-PR**.
