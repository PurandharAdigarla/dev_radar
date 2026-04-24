# Dev Radar — Plan 2: Ingestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Continuously ingest dev content from three sources (Hacker News, GitHub Trending, GitHub Security Advisories) into a `source_items` table, with dedup + interest-tag extraction at ingestion time, so Plan 3's AI radar generator has a rich, pre-tagged candidate pool.

**Architecture:** Per-source `@Scheduled` jobs in a `com.devradar.ingest` module. Each job: fetch via HTTP client → for each item, acquire Redis SETNX dedup lock → extract metadata + tags → upsert into MySQL `source_items` + `source_item_tags`. Errors per-item are logged and skipped, never fail the batch. Background jobs run independently of users.

**Tech Stack:** Spring Boot 3.5+, Spring Data Redis, Spring Web (RestClient), Jsoup (GitHub trending HTML), WireMock (HTTP test stubs), Testcontainers (MySQL + Redis), Liquibase, JUnit 5 + AssertJ.

---

## File Structure

```
backend/
├── pom.xml                                      (modify: + spring-boot-starter-data-redis, jsoup, wiremock)
├── docker-compose.yml                           (modify: + redis service)
├── src/main/
│   ├── java/com/devradar/
│   │   ├── config/
│   │   │   └── RedisConfig.java                 (RedisTemplate<String, String> bean)
│   │   ├── domain/
│   │   │   ├── Source.java                      (entity)
│   │   │   ├── SourceItem.java                  (entity)
│   │   │   ├── SourceItemTag.java               (entity)
│   │   │   └── SourceItemTagId.java             (composite PK)
│   │   ├── repository/
│   │   │   ├── SourceRepository.java
│   │   │   ├── SourceItemRepository.java
│   │   │   └── SourceItemTagRepository.java
│   │   ├── ingest/
│   │   │   ├── DedupService.java                (Redis SETNX wrapper)
│   │   │   ├── TagExtractor.java                (text → matched interest_tag slugs)
│   │   │   ├── IngestionService.java            (shared orchestration: persist, tag, dedup)
│   │   │   ├── client/
│   │   │   │   ├── HackerNewsClient.java        (Algolia REST)
│   │   │   │   ├── GitHubTrendingClient.java    (HTML scrape via Jsoup)
│   │   │   │   ├── GHSAClient.java              (GitHub REST advisories)
│   │   │   │   └── FetchedItem.java             (DTO between client and ingestion)
│   │   │   └── job/
│   │   │       ├── HackerNewsIngestor.java      (@Scheduled hourly)
│   │   │       ├── GitHubTrendingIngestor.java  (@Scheduled every 6h)
│   │   │       └── GHSAIngestor.java            (@Scheduled every 30 min)
│   │   └── web/rest/
│   │       └── (no new endpoints in Plan 2 — admin observability comes in Plan 7)
│   └── resources/
│       └── db/changelog/
│           ├── db.changelog-master.xml          (modify: include 005, 006)
│           ├── 005-ingestion-schema.xml         (sources + source_items + source_item_tags)
│           └── 006-seed-sources.xml             (HN, GH_TRENDING, GHSA rows)
└── src/test/
    └── java/com/devradar/
        ├── ingest/
        │   ├── DedupServiceTest.java            (real Redis via Testcontainers)
        │   ├── TagExtractorTest.java            (pure unit)
        │   ├── client/
        │   │   ├── HackerNewsClientTest.java    (WireMock)
        │   │   ├── GitHubTrendingClientTest.java(WireMock + canned HTML)
        │   │   └── GHSAClientTest.java          (WireMock)
        │   └── IngestionServiceIT.java          (full pipeline; MySQL + Redis Testcontainers + WireMock)
        └── AbstractIntegrationTest.java         (modify: + Redis container + dynamic property registration)
```

---

## Task 1: Add Redis + Jsoup + WireMock dependencies

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Append dependencies inside the existing `<dependencies>` block**

Add after the existing `mapstruct` dependency:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jsoup</groupId>
            <artifactId>jsoup</artifactId>
            <version>1.18.1</version>
        </dependency>
```

And inside the test scope dependencies block (after `testcontainers mysql`):

```xml
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <version>3.9.2</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Verify the build downloads the new deps**

Run: `cd backend && mvn -DskipTests dependency:resolve`
Expected: BUILD SUCCESS, no resolution errors.

- [ ] **Step 3: Commit (no Co-Authored-By trailer)**

```bash
git add backend/pom.xml
git commit -m "feat(backend): add spring-data-redis, jsoup, wiremock dependencies"
```

---

## Task 2: Add Redis to docker-compose

**Files:**
- Modify: `backend/docker-compose.yml`

- [ ] **Step 1: Replace the entire docker-compose.yml**

```yaml
# backend/docker-compose.yml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: devradar
      MYSQL_USER: devradar
      MYSQL_PASSWORD: devradar
    ports:
      - "${DB_HOST_PORT:-3306}:3306"
    volumes:
      - devradar-mysql:/var/lib/mysql
  redis:
    image: redis:7.4-alpine
    ports:
      - "${REDIS_HOST_PORT:-6379}:6379"
    volumes:
      - devradar-redis:/data
volumes:
  devradar-mysql:
  devradar-redis:
```

- [ ] **Step 2: Update `application.yml` to point at Redis**

Append to `backend/src/main/resources/application.yml` under the `spring:` block (after `liquibase:`):

```yaml
  data:
    redis:
      host: localhost
      port: ${REDIS_HOST_PORT:6379}
```

(Keep the existing `spring.data.web.pageable` block — be careful not to duplicate the `spring.data:` prefix. Merge under one `data:` key.)

The merged `data:` block should look like:

```yaml
  data:
    redis:
      host: localhost
      port: ${REDIS_HOST_PORT:6379}
    web:
      pageable:
        max-page-size: 100
        default-page-size: 20
```

- [ ] **Step 3: Update `.env.example`**

```env
# backend/.env.example
# Override host port mapped to MySQL (default: 3306)
# DB_HOST_PORT=3307

# Override host port mapped to Redis (default: 6379)
# REDIS_HOST_PORT=6380
```

- [ ] **Step 4: Bring up Redis and verify**

```bash
cd backend
docker compose up -d redis
sleep 3
docker compose exec redis redis-cli PING
```
Expected: `PONG`

- [ ] **Step 5: Commit**

```bash
git add backend/docker-compose.yml backend/src/main/resources/application.yml backend/.env.example
git commit -m "feat(backend): add Redis to docker-compose and Spring config"
```

---

## Task 3: Liquibase ingestion schema

**Files:**
- Create: `backend/src/main/resources/db/changelog/005-ingestion-schema.xml`
- Create: `backend/src/main/resources/db/changelog/006-seed-sources.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml` (add includes)

- [ ] **Step 1: Create `005-ingestion-schema.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="005-create-sources" author="devradar">
        <createTable tableName="sources">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="code" type="VARCHAR(40)"><constraints nullable="false" unique="true" uniqueConstraintName="uk_sources_code"/></column>
            <column name="display_name" type="VARCHAR(120)"><constraints nullable="false"/></column>
            <column name="active" type="BOOLEAN" defaultValueBoolean="true"><constraints nullable="false"/></column>
            <column name="fetch_interval_minutes" type="INT"><constraints nullable="false"/></column>
            <column name="created_at" type="TIMESTAMP"><constraints nullable="false"/></column>
        </createTable>
    </changeSet>

    <changeSet id="005-create-source-items" author="devradar">
        <createTable tableName="source_items">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="source_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="external_id" type="VARCHAR(255)"><constraints nullable="false"/></column>
            <column name="url" type="VARCHAR(2048)"><constraints nullable="false"/></column>
            <column name="title" type="VARCHAR(1000)"><constraints nullable="false"/></column>
            <column name="author" type="VARCHAR(255)"/>
            <column name="posted_at" type="TIMESTAMP"><constraints nullable="false"/></column>
            <column name="raw_payload" type="JSON"/>
            <column name="fetched_at" type="TIMESTAMP"><constraints nullable="false"/></column>
        </createTable>
        <addUniqueConstraint tableName="source_items" columnNames="source_id, external_id" constraintName="uk_source_items_source_external"/>
        <addForeignKeyConstraint baseTableName="source_items" baseColumnNames="source_id"
            constraintName="fk_source_items_source" referencedTableName="sources" referencedColumnNames="id" onDelete="RESTRICT"/>
        <createIndex tableName="source_items" indexName="ix_source_items_posted_at">
            <column name="posted_at"/>
        </createIndex>
    </changeSet>

    <changeSet id="005-create-source-item-tags" author="devradar">
        <createTable tableName="source_item_tags">
            <column name="source_item_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="interest_tag_id" type="BIGINT"><constraints nullable="false"/></column>
        </createTable>
        <addPrimaryKey tableName="source_item_tags" columnNames="source_item_id, interest_tag_id" constraintName="pk_source_item_tags"/>
        <addForeignKeyConstraint baseTableName="source_item_tags" baseColumnNames="source_item_id"
            constraintName="fk_source_item_tags_item" referencedTableName="source_items" referencedColumnNames="id" onDelete="CASCADE"/>
        <addForeignKeyConstraint baseTableName="source_item_tags" baseColumnNames="interest_tag_id"
            constraintName="fk_source_item_tags_tag" referencedTableName="interest_tags" referencedColumnNames="id" onDelete="CASCADE"/>
        <createIndex tableName="source_item_tags" indexName="ix_source_item_tags_tag">
            <column name="interest_tag_id"/>
        </createIndex>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: Create `006-seed-sources.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="006-seed-sources" author="devradar">
        <sql>
INSERT INTO sources (code, display_name, active, fetch_interval_minutes, created_at) VALUES
  ('HN', 'Hacker News', true, 60, NOW()),
  ('GH_TRENDING', 'GitHub Trending', true, 360, NOW()),
  ('GHSA', 'GitHub Security Advisories', true, 30, NOW());
        </sql>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 3: Append both includes to the master changelog**

Edit `backend/src/main/resources/db/changelog/db.changelog-master.xml`. Inside the existing `<databaseChangeLog>` element, add after the existing four includes:

```xml
    <include file="db/changelog/005-ingestion-schema.xml"/>
    <include file="db/changelog/006-seed-sources.xml"/>
```

The full master file should now reference 001, 002, 003, 004, 005, 006.

- [ ] **Step 4: Verify migrations apply against local MySQL**

```bash
cd backend
DB_HOST_PORT=${DB_HOST_PORT:-3307} docker compose up -d mysql
sleep 8
DB_HOST_PORT=${DB_HOST_PORT:-3307} mvn spring-boot:run &
sleep 25
docker compose exec mysql mysql -udevradar -pdevradar devradar -e "SHOW TABLES;"
docker compose exec mysql mysql -udevradar -pdevradar devradar -e "SELECT code, fetch_interval_minutes FROM sources;"
pkill -f spring-boot
```

Expected `SHOW TABLES`: includes `sources`, `source_items`, `source_item_tags` (in addition to prior tables).
Expected `SELECT`: 3 rows (HN/60, GH_TRENDING/360, GHSA/30).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/changelog/
git commit -m "feat(db): add ingestion schema (sources, source_items, source_item_tags) + seed sources"
```

---

## Task 4: Domain entities for ingestion

**Files:**
- Create: `backend/src/main/java/com/devradar/domain/Source.java`
- Create: `backend/src/main/java/com/devradar/domain/SourceItem.java`
- Create: `backend/src/main/java/com/devradar/domain/SourceItemTagId.java`
- Create: `backend/src/main/java/com/devradar/domain/SourceItemTag.java`

- [ ] **Step 1: Create `Source.java`**

```java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sources")
public class Source {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean active = true;

    @Column(name = "fetch_interval_minutes", nullable = false)
    private int fetchIntervalMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getFetchIntervalMinutes() { return fetchIntervalMinutes; }
    public void setFetchIntervalMinutes(int fetchIntervalMinutes) { this.fetchIntervalMinutes = fetchIntervalMinutes; }
}
```

- [ ] **Step 2: Create `SourceItem.java`**

```java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "source_items",
       uniqueConstraints = @UniqueConstraint(name = "uk_source_items_source_external", columnNames = {"source_id", "external_id"}))
public class SourceItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(length = 255)
    private String author;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "raw_payload", columnDefinition = "JSON")
    private String rawPayload;

    @Column(name = "fetched_at", nullable = false, updatable = false)
    private Instant fetchedAt;

    @PrePersist
    void onCreate() { fetchedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public Instant getFetchedAt() { return fetchedAt; }
}
```

- [ ] **Step 3: Create `SourceItemTagId.java`**

```java
package com.devradar.domain;

import java.io.Serializable;
import java.util.Objects;

public class SourceItemTagId implements Serializable {
    private Long sourceItemId;
    private Long interestTagId;

    public SourceItemTagId() {}
    public SourceItemTagId(Long sourceItemId, Long interestTagId) {
        this.sourceItemId = sourceItemId; this.interestTagId = interestTagId;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceItemTagId other)) return false;
        return Objects.equals(sourceItemId, other.sourceItemId) && Objects.equals(interestTagId, other.interestTagId);
    }
    @Override public int hashCode() { return Objects.hash(sourceItemId, interestTagId); }
}
```

- [ ] **Step 4: Create `SourceItemTag.java`**

```java
package com.devradar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "source_item_tags")
@IdClass(SourceItemTagId.class)
public class SourceItemTag {
    @Id @Column(name = "source_item_id") private Long sourceItemId;
    @Id @Column(name = "interest_tag_id") private Long interestTagId;

    public SourceItemTag() {}
    public SourceItemTag(Long sourceItemId, Long interestTagId) {
        this.sourceItemId = sourceItemId; this.interestTagId = interestTagId;
    }
    public Long getSourceItemId() { return sourceItemId; }
    public Long getInterestTagId() { return interestTagId; }
}
```

- [ ] **Step 5: Verify compile**

```bash
cd backend && mvn -DskipTests compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/domain/Source.java backend/src/main/java/com/devradar/domain/SourceItem.java backend/src/main/java/com/devradar/domain/SourceItemTag.java backend/src/main/java/com/devradar/domain/SourceItemTagId.java
git commit -m "feat(domain): add Source, SourceItem, SourceItemTag entities"
```

---

## Task 5: Repositories for ingestion

**Files:**
- Create: `backend/src/main/java/com/devradar/repository/SourceRepository.java`
- Create: `backend/src/main/java/com/devradar/repository/SourceItemRepository.java`
- Create: `backend/src/main/java/com/devradar/repository/SourceItemTagRepository.java`

- [ ] **Step 1: Create `SourceRepository.java`**

```java
package com.devradar.repository;

import com.devradar.domain.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SourceRepository extends JpaRepository<Source, Long> {
    Optional<Source> findByCode(String code);
}
```

- [ ] **Step 2: Create `SourceItemRepository.java`**

```java
package com.devradar.repository;

import com.devradar.domain.SourceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SourceItemRepository extends JpaRepository<SourceItem, Long> {
    Optional<SourceItem> findBySourceIdAndExternalId(Long sourceId, String externalId);
    boolean existsBySourceIdAndExternalId(Long sourceId, String externalId);
}
```

- [ ] **Step 3: Create `SourceItemTagRepository.java`**

```java
package com.devradar.repository;

import com.devradar.domain.SourceItemTag;
import com.devradar.domain.SourceItemTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceItemTagRepository extends JpaRepository<SourceItemTag, SourceItemTagId> {}
```

- [ ] **Step 4: Verify compile**

```bash
cd backend && mvn -DskipTests compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/repository/SourceRepository.java backend/src/main/java/com/devradar/repository/SourceItemRepository.java backend/src/main/java/com/devradar/repository/SourceItemTagRepository.java
git commit -m "feat(repo): add Source, SourceItem, SourceItemTag repositories"
```

---

## Task 6: RedisConfig + extend AbstractIntegrationTest with Redis Testcontainer

**Files:**
- Create: `backend/src/main/java/com/devradar/config/RedisConfig.java`
- Modify: `backend/src/test/java/com/devradar/AbstractIntegrationTest.java`

- [ ] **Step 1: Create `RedisConfig.java`**

```java
package com.devradar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
```

(Spring Data Redis auto-configures the connection factory from `spring.data.redis.host/port`. We just expose a typed template bean for SETNX use.)

- [ ] **Step 2: Replace `AbstractIntegrationTest.java` with the version that also starts a Redis container**

```java
package com.devradar;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("devradar_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
```

- [ ] **Step 3: Verify existing tests still pass with the Redis container in the base class**

```bash
cd backend && mvn -Dtest='*IT' test
```
Expected: BUILD SUCCESS, all existing IT tests pass (they ignore Redis but the container starting doesn't hurt).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devradar/config/RedisConfig.java backend/src/test/java/com/devradar/AbstractIntegrationTest.java
git commit -m "feat(redis): add RedisConfig and Testcontainer Redis in AbstractIntegrationTest"
```

---

## Task 7: DedupService (Redis SETNX wrapper) — TDD with real Redis

**Files:**
- Create: `backend/src/test/java/com/devradar/ingest/DedupServiceTest.java`
- Create: `backend/src/main/java/com/devradar/ingest/DedupService.java`

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/devradar/ingest/DedupServiceTest.java
package com.devradar.ingest;

import com.devradar.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DedupServiceTest extends AbstractIntegrationTest {

    @Autowired DedupService dedup;
    @Autowired StringRedisTemplate redis;

    @Test
    void tryAcquire_returnsTrueFirstTime_falseSecondTime() {
        String key = "ingest:HN:" + UUID.randomUUID();

        boolean first = dedup.tryAcquire(key, Duration.ofMinutes(5));
        boolean second = dedup.tryAcquire(key, Duration.ofMinutes(5));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void release_clearsLock_soNextAcquireSucceeds() {
        String key = "ingest:HN:" + UUID.randomUUID();
        dedup.tryAcquire(key, Duration.ofMinutes(5));
        dedup.release(key);

        assertThat(dedup.tryAcquire(key, Duration.ofMinutes(5))).isTrue();
    }
}
```

- [ ] **Step 2: Run — expect compile fail (no DedupService)**

```bash
cd backend && mvn -Dtest=DedupServiceTest test
```

- [ ] **Step 3: Implement `DedupService.java`**

```java
package com.devradar.ingest;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class DedupService {

    private final StringRedisTemplate redis;

    public DedupService(StringRedisTemplate redis) { this.redis = redis; }

    public boolean tryAcquire(String key, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void release(String key) {
        redis.delete(key);
    }
}
```

- [ ] **Step 4: Run — expect 2/2 PASS**

```bash
cd backend && mvn -Dtest=DedupServiceTest test
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/DedupService.java backend/src/test/java/com/devradar/ingest/DedupServiceTest.java
git commit -m "feat(ingest): add DedupService backed by Redis SETNX with TTL"
```

---

## Task 8: TagExtractor — TDD pure unit

**Files:**
- Create: `backend/src/test/java/com/devradar/ingest/TagExtractorTest.java`
- Create: `backend/src/main/java/com/devradar/ingest/TagExtractor.java`

- [ ] **Step 1: Write failing tests**

```java
// backend/src/test/java/com/devradar/ingest/TagExtractorTest.java
package com.devradar.ingest;

import com.devradar.domain.InterestCategory;
import com.devradar.domain.InterestTag;
import com.devradar.repository.InterestTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TagExtractorTest {

    InterestTagRepository repo;
    TagExtractor extractor;

    @BeforeEach
    void setup() {
        repo = mock(InterestTagRepository.class);
        // Provide a pre-loaded set of tags
        List<InterestTag> tags = List.of(
            tag("spring_boot", "Spring Boot", InterestCategory.framework),
            tag("react", "React", InterestCategory.framework),
            tag("mysql", "MySQL", InterestCategory.tool),
            tag("rust", "Rust", InterestCategory.language)
        );
        when(repo.findAll()).thenReturn(tags);
        extractor = new TagExtractor(repo);
    }

    @Test
    void extracts_displayName_caseInsensitive() {
        Set<Long> ids = extractor.extract("Spring Boot 3.5 just shipped", List.of());
        assertThat(ids).hasSize(1);
    }

    @Test
    void extracts_slugWordBoundary() {
        Set<Long> ids = extractor.extract("Why I'm switching from React to Svelte", List.of());
        assertThat(ids).hasSize(1); // react matches; svelte not in our tag set
    }

    @Test
    void extracts_fromExplicitTopics() {
        Set<Long> ids = extractor.extract("Some unrelated title", List.of("rust", "mysql"));
        assertThat(ids).hasSize(2);
    }

    @Test
    void noMatch_returnsEmpty() {
        Set<Long> ids = extractor.extract("Nothing relevant here at all", List.of());
        assertThat(ids).isEmpty();
    }

    @Test
    void deduplicates_acrossTextAndTopics() {
        Set<Long> ids = extractor.extract("React is great", List.of("react"));
        assertThat(ids).hasSize(1);
    }

    private static InterestTag tag(String slug, String display, InterestCategory cat) {
        InterestTag t = new InterestTag();
        t.setSlug(slug);
        t.setDisplayName(display);
        t.setCategory(cat);
        try { var f = InterestTag.class.getDeclaredField("id"); f.setAccessible(true); f.set(t, (long) slug.hashCode()); }
        catch (Exception e) {}
        return t;
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=TagExtractorTest test
```

- [ ] **Step 3: Implement `TagExtractor.java`**

```java
package com.devradar.ingest;

import com.devradar.domain.InterestTag;
import com.devradar.repository.InterestTagRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TagExtractor {

    private final InterestTagRepository repo;

    public TagExtractor(InterestTagRepository repo) { this.repo = repo; }

    /**
     * Extract interest_tag IDs that match in the given text or are explicitly listed in topics.
     * Match is case-insensitive substring (with word-boundary intent — see implementation).
     */
    public Set<Long> extract(String text, List<String> explicitTopics) {
        String hay = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> topicSlugs = explicitTopics == null ? Set.of()
            : explicitTopics.stream().map(s -> s.toLowerCase(Locale.ROOT).trim()).collect(java.util.stream.Collectors.toSet());

        Set<Long> matched = new HashSet<>();
        for (InterestTag tag : repo.findAll()) {
            String slug = tag.getSlug().toLowerCase(Locale.ROOT);
            String displayLower = tag.getDisplayName().toLowerCase(Locale.ROOT);

            boolean inText = containsAsWord(hay, displayLower) || containsAsWord(hay, slug);
            boolean inTopics = topicSlugs.contains(slug);

            if (inText || inTopics) matched.add(tag.getId());
        }
        return matched;
    }

    private static boolean containsAsWord(String hay, String needle) {
        if (needle.isBlank()) return false;
        int idx = 0;
        while ((idx = hay.indexOf(needle, idx)) != -1) {
            boolean leftOk = (idx == 0) || !Character.isLetterOrDigit(hay.charAt(idx - 1));
            int end = idx + needle.length();
            boolean rightOk = (end == hay.length()) || !Character.isLetterOrDigit(hay.charAt(end));
            if (leftOk && rightOk) return true;
            idx = end;
        }
        return false;
    }
}
```

- [ ] **Step 4: Run — expect 5/5 PASS**

```bash
cd backend && mvn -Dtest=TagExtractorTest test
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/TagExtractor.java backend/src/test/java/com/devradar/ingest/TagExtractorTest.java
git commit -m "feat(ingest): add TagExtractor matching tags in text + explicit topics"
```

---

## Task 9: FetchedItem DTO + IngestionService (shared persistence)

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/client/FetchedItem.java`
- Create: `backend/src/main/java/com/devradar/ingest/IngestionService.java`

- [ ] **Step 1: Create `FetchedItem.java`**

```java
package com.devradar.ingest.client;

import java.time.Instant;
import java.util.List;

/**
 * Source-agnostic DTO produced by source clients and consumed by IngestionService.
 * `topics` are slugs the source itself has tagged the item with (e.g., GitHub repo topics).
 */
public record FetchedItem(
    String externalId,
    String url,
    String title,
    String author,
    Instant postedAt,
    String rawPayload,        // optional JSON blob for forensic debugging
    List<String> topics       // optional explicit topic slugs from the source
) {}
```

- [ ] **Step 2: Create `IngestionService.java`**

```java
package com.devradar.ingest;

import com.devradar.domain.Source;
import com.devradar.domain.SourceItem;
import com.devradar.domain.SourceItemTag;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceItemTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class IngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionService.class);
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

    private final SourceItemRepository itemRepo;
    private final SourceItemTagRepository tagRepo;
    private final TagExtractor tagExtractor;
    private final DedupService dedup;

    public IngestionService(
        SourceItemRepository itemRepo,
        SourceItemTagRepository tagRepo,
        TagExtractor tagExtractor,
        DedupService dedup
    ) {
        this.itemRepo = itemRepo;
        this.tagRepo = tagRepo;
        this.tagExtractor = tagExtractor;
        this.dedup = dedup;
    }

    /**
     * Ingest a batch of items for one source. Per-item failures are logged and skipped.
     * Returns count of newly persisted items.
     */
    public int ingestBatch(Source source, List<FetchedItem> items) {
        int inserted = 0;
        for (FetchedItem item : items) {
            try {
                if (ingestOne(source, item)) inserted++;
            } catch (Exception e) {
                LOG.warn("ingest item failed source={} extId={}: {}", source.getCode(), item.externalId(), e.toString());
            }
        }
        LOG.info("ingest source={} fetched={} inserted={}", source.getCode(), items.size(), inserted);
        return inserted;
    }

    /**
     * Per-item insert. NOT @Transactional — Spring's proxy is bypassed when called from same-class
     * ingestBatch(). Each repo.save() runs in its own JPA tx (Spring Data JPA default). If the tag
     * inserts fail mid-loop, we'll have an orphan source_item with partial tags; cleanup is acceptable
     * for ingestion (no user impact) and surfaces via observability later.
     */
    private boolean ingestOne(Source source, FetchedItem item) {
        String dedupKey = "ingest:" + source.getCode() + ":" + item.externalId();

        // Two-stage dedup: cheap Redis SETNX, then DB unique constraint as backstop.
        if (!dedup.tryAcquire(dedupKey, DEDUP_TTL)) return false;
        if (itemRepo.existsBySourceIdAndExternalId(source.getId(), item.externalId())) return false;

        SourceItem si = new SourceItem();
        si.setSourceId(source.getId());
        si.setExternalId(item.externalId());
        si.setUrl(item.url());
        si.setTitle(item.title());
        si.setAuthor(item.author());
        si.setPostedAt(item.postedAt());
        si.setRawPayload(item.rawPayload());
        SourceItem saved = itemRepo.save(si);

        Set<Long> tagIds = tagExtractor.extract(item.title(), item.topics());
        for (Long tagId : tagIds) {
            tagRepo.save(new SourceItemTag(saved.getId(), tagId));
        }
        return true;
    }
}
```

- [ ] **Step 3: Verify compile**

```bash
cd backend && mvn -DskipTests compile
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/client/FetchedItem.java backend/src/main/java/com/devradar/ingest/IngestionService.java
git commit -m "feat(ingest): add FetchedItem DTO and IngestionService with dedup + tagging"
```

---

## Task 10: HackerNewsClient (Algolia REST) — TDD with WireMock

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/client/HackerNewsClient.java`
- Create: `backend/src/test/java/com/devradar/ingest/client/HackerNewsClientTest.java`

- [ ] **Step 1: Write failing test using WireMock**

```java
// backend/src/test/java/com/devradar/ingest/client/HackerNewsClientTest.java
package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HackerNewsClientTest {

    WireMockServer wm;
    HackerNewsClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new HackerNewsClient(RestClient.builder(), "http://localhost:" + wm.port());
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void fetchRecent_returnsParsedItems() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/search_by_date"))
            .willReturn(WireMock.okJson("""
                {
                  "hits": [
                    {
                      "objectID": "12345",
                      "title": "Spring Boot 3.5 released",
                      "url": "https://example.com/spring-boot-3-5",
                      "author": "rstoyanchev",
                      "created_at_i": 1755100000,
                      "points": 280
                    },
                    {
                      "objectID": "12346",
                      "title": "Show HN: htmx 2.1",
                      "url": "https://example.com/htmx",
                      "author": "carson",
                      "created_at_i": 1755103600,
                      "points": 95
                    }
                  ]
                }
                """)));

        List<FetchedItem> items = client.fetchRecent(50);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).externalId()).isEqualTo("12345");
        assertThat(items.get(0).title()).isEqualTo("Spring Boot 3.5 released");
        assertThat(items.get(0).url()).isEqualTo("https://example.com/spring-boot-3-5");
        assertThat(items.get(0).author()).isEqualTo("rstoyanchev");
        assertThat(items.get(1).externalId()).isEqualTo("12346");
    }

    @Test
    void fetchRecent_returnsEmpty_onEmptyHits() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/search_by_date"))
            .willReturn(WireMock.okJson("{\"hits\": []}")));

        assertThat(client.fetchRecent(50)).isEmpty();
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=HackerNewsClientTest test
```

- [ ] **Step 3: Implement `HackerNewsClient.java`**

```java
package com.devradar.ingest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class HackerNewsClient {

    private final RestClient http;

    public HackerNewsClient(RestClient.Builder builder,
                            @Value("${devradar.hn.base-url:https://hn.algolia.com}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public List<FetchedItem> fetchRecent(int minPoints) {
        JsonNode body = http.get()
            .uri(uri -> uri.path("/api/v1/search_by_date")
                .queryParam("tags", "story")
                .queryParam("numericFilters", "points>" + minPoints)
                .build())
            .retrieve()
            .body(JsonNode.class);

        List<FetchedItem> out = new ArrayList<>();
        if (body == null || !body.has("hits")) return out;
        for (JsonNode hit : body.get("hits")) {
            String externalId = textOrNull(hit, "objectID");
            String title = textOrNull(hit, "title");
            String url = textOrNull(hit, "url");
            if (externalId == null || title == null || url == null) continue;
            String author = textOrNull(hit, "author");
            long createdAtSec = hit.path("created_at_i").asLong();
            Instant posted = createdAtSec > 0 ? Instant.ofEpochSecond(createdAtSec) : Instant.now();
            out.add(new FetchedItem(externalId, url, title, author, posted, hit.toString(), List.of()));
        }
        return out;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
```

- [ ] **Step 4: Add `@Bean RestClient.Builder` if not auto-provided** — confirm by running:

```bash
cd backend && mvn -Dtest=HackerNewsClientTest test
```
Expected: 2/2 passing. Spring Boot's `RestClientAutoConfiguration` provides the builder bean automatically; no extra config needed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/client/HackerNewsClient.java backend/src/test/java/com/devradar/ingest/client/HackerNewsClientTest.java
git commit -m "feat(ingest): HackerNewsClient with Algolia API + WireMock tests"
```

---

## Task 11: GitHubTrendingClient (HTML scrape via Jsoup) — TDD with WireMock

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/client/GitHubTrendingClient.java`
- Create: `backend/src/test/java/com/devradar/ingest/client/GitHubTrendingClientTest.java`
- Create: `backend/src/test/resources/github-trending-sample.html`

- [ ] **Step 1: Create the canned HTML fixture**

```html
<!-- backend/src/test/resources/github-trending-sample.html -->
<html><body>
<article class="Box-row">
  <h2 class="h3 lh-condensed"><a href="/spring-projects/spring-boot">spring-projects/spring-boot</a></h2>
  <p class="col-9 color-fg-muted my-1 pr-4">Spring Boot</p>
  <span itemprop="programmingLanguage">Java</span>
</article>
<article class="Box-row">
  <h2 class="h3 lh-condensed"><a href="/bigskysoftware/htmx">bigskysoftware/htmx</a></h2>
  <p class="col-9 color-fg-muted my-1 pr-4">&lt;/&gt; htmx - high power tools for HTML</p>
  <span itemprop="programmingLanguage">JavaScript</span>
</article>
</body></html>
```

- [ ] **Step 2: Write failing test**

```java
// backend/src/test/java/com/devradar/ingest/client/GitHubTrendingClientTest.java
package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubTrendingClientTest {

    WireMockServer wm;
    GitHubTrendingClient client;
    String sampleHtml;

    @BeforeEach
    void setup() throws IOException {
        wm = new WireMockServer(0);
        wm.start();
        client = new GitHubTrendingClient(RestClient.builder(), "http://localhost:" + wm.port());
        sampleHtml = Files.readString(Path.of("src/test/resources/github-trending-sample.html"));
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void fetchTrending_parsesRepoCards() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/trending"))
            .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "text/html").withBody(sampleHtml)));

        List<FetchedItem> items = client.fetchTrending(null);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).externalId()).isEqualTo("spring-projects/spring-boot");
        assertThat(items.get(0).title()).isEqualTo("spring-projects/spring-boot");
        assertThat(items.get(0).url()).isEqualTo("https://github.com/spring-projects/spring-boot");
        assertThat(items.get(0).topics()).contains("java");
        assertThat(items.get(1).externalId()).isEqualTo("bigskysoftware/htmx");
        assertThat(items.get(1).topics()).contains("javascript");
    }

    @Test
    void fetchTrending_filtersByLanguage() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/trending/java"))
            .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "text/html").withBody(sampleHtml)));

        List<FetchedItem> items = client.fetchTrending("java");

        assertThat(items).isNotEmpty();
    }
}
```

- [ ] **Step 3: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=GitHubTrendingClientTest test
```

- [ ] **Step 4: Implement `GitHubTrendingClient.java`**

```java
package com.devradar.ingest.client;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GitHubTrendingClient {

    private final RestClient http;
    private final String baseUrl;

    public GitHubTrendingClient(RestClient.Builder builder,
                                @Value("${devradar.gh-trending.base-url:https://github.com}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = builder.baseUrl(baseUrl).build();
    }

    public List<FetchedItem> fetchTrending(String language) {
        String path = (language == null || language.isBlank()) ? "/trending" : "/trending/" + language.toLowerCase(Locale.ROOT);
        String html = http.get().uri(path).retrieve().body(String.class);
        if (html == null) return List.of();

        Document doc = Jsoup.parse(html);
        List<FetchedItem> out = new ArrayList<>();
        Instant now = Instant.now();
        for (Element card : doc.select("article.Box-row")) {
            Element link = card.selectFirst("h2 a");
            if (link == null) continue;
            String href = link.attr("href").trim();
            if (href.startsWith("/")) href = href.substring(1);
            String url = "https://github.com/" + href;

            Element langEl = card.selectFirst("[itemprop=programmingLanguage]");
            List<String> topics = new ArrayList<>();
            if (langEl != null) topics.add(langEl.text().toLowerCase(Locale.ROOT));

            out.add(new FetchedItem(href, url, href, null, now, null, topics));
        }
        return out;
    }
}
```

- [ ] **Step 5: Run — expect 2/2 PASS**

```bash
cd backend && mvn -Dtest=GitHubTrendingClientTest test
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/client/GitHubTrendingClient.java backend/src/test/java/com/devradar/ingest/client/GitHubTrendingClientTest.java backend/src/test/resources/github-trending-sample.html
git commit -m "feat(ingest): GitHubTrendingClient with Jsoup HTML parse + WireMock tests"
```

---

## Task 12: GHSAClient (GitHub Security Advisories) — TDD with WireMock

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/client/GHSAClient.java`
- Create: `backend/src/test/java/com/devradar/ingest/client/GHSAClientTest.java`

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/devradar/ingest/client/GHSAClientTest.java
package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GHSAClientTest {

    WireMockServer wm;
    GHSAClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new GHSAClient(RestClient.builder(), "http://localhost:" + wm.port());
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void fetchRecent_returnsParsedAdvisories() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/advisories"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "ghsa_id": "GHSA-xxxx-yyyy-zzzz",
                    "summary": "jackson-databind RCE in 2.16.x",
                    "html_url": "https://github.com/advisories/GHSA-xxxx-yyyy-zzzz",
                    "severity": "high",
                    "published_at": "2026-04-15T12:00:00Z",
                    "vulnerabilities": [{"package": {"ecosystem": "maven", "name": "com.fasterxml.jackson.core:jackson-databind"}}]
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchRecent();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).externalId()).isEqualTo("GHSA-xxxx-yyyy-zzzz");
        assertThat(items.get(0).title()).contains("jackson-databind");
        assertThat(items.get(0).url()).isEqualTo("https://github.com/advisories/GHSA-xxxx-yyyy-zzzz");
        assertThat(items.get(0).topics()).contains("security");
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=GHSAClientTest test
```

- [ ] **Step 3: Implement `GHSAClient.java`**

```java
package com.devradar.ingest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GHSAClient {

    private final RestClient http;

    public GHSAClient(RestClient.Builder builder,
                      @Value("${devradar.ghsa.base-url:https://api.github.com}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public List<FetchedItem> fetchRecent() {
        JsonNode arr = http.get()
            .uri(uri -> uri.path("/advisories").queryParam("per_page", "50").build())
            .retrieve()
            .body(JsonNode.class);

        List<FetchedItem> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode adv : arr) {
            String ghsaId = textOrNull(adv, "ghsa_id");
            String summary = textOrNull(adv, "summary");
            String url = textOrNull(adv, "html_url");
            String publishedAt = textOrNull(adv, "published_at");
            if (ghsaId == null || summary == null || url == null) continue;
            Instant posted = publishedAt != null ? Instant.parse(publishedAt) : Instant.now();
            // Always tag GHSA items with "security" topic so users with that interest see them.
            out.add(new FetchedItem(ghsaId, url, summary, null, posted, adv.toString(), List.of("security")));
        }
        return out;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
```

- [ ] **Step 4: Run — expect 1/1 PASS**

```bash
cd backend && mvn -Dtest=GHSAClientTest test
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/client/GHSAClient.java backend/src/test/java/com/devradar/ingest/client/GHSAClientTest.java
git commit -m "feat(ingest): GHSAClient for GitHub Security Advisories with WireMock tests"
```

---

## Task 13: HackerNewsIngestor (@Scheduled job)

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/job/HackerNewsIngestor.java`
- Modify: `backend/src/main/java/com/devradar/DevRadarApplication.java` (enable scheduling)

- [ ] **Step 1: Add `@EnableScheduling` to the main application class**

Replace `DevRadarApplication.java`:

```java
package com.devradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableScheduling
public class DevRadarApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevRadarApplication.class, args);
    }
}
```

- [ ] **Step 2: Create `HackerNewsIngestor.java`**

```java
package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.HackerNewsClient;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HackerNewsIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(HackerNewsIngestor.class);
    private static final String CODE = "HN";
    private static final int MIN_POINTS = 50;

    private final HackerNewsClient client;
    private final IngestionService ingestion;
    private final SourceRepository sources;

    public HackerNewsIngestor(HackerNewsClient client, IngestionService ingestion, SourceRepository sources) {
        this.client = client; this.ingestion = ingestion; this.sources = sources;
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.hn.fixed-delay-ms:3600000}", initialDelayString = "${devradar.ingest.hn.initial-delay-ms:30000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("HN source not active; skipping");
            return;
        }
        try {
            var items = client.fetchRecent(MIN_POINTS);
            ingestion.ingestBatch(src, items);
        } catch (Exception e) {
            LOG.warn("HN ingestion failed: {}", e.toString());
        }
    }
}
```

- [ ] **Step 3: Verify compile**

```bash
cd backend && mvn -DskipTests compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devradar/DevRadarApplication.java backend/src/main/java/com/devradar/ingest/job/HackerNewsIngestor.java
git commit -m "feat(ingest): add @Scheduled HackerNewsIngestor (1h cadence)"
```

---

## Task 14: GitHubTrendingIngestor (@Scheduled job)

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/job/GitHubTrendingIngestor.java`

- [ ] **Step 1: Create the ingestor**

```java
package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.GitHubTrendingClient;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class GitHubTrendingIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubTrendingIngestor.class);
    private static final String CODE = "GH_TRENDING";

    private final GitHubTrendingClient client;
    private final IngestionService ingestion;
    private final SourceRepository sources;
    private final List<String> trackedLanguages;

    public GitHubTrendingIngestor(
        GitHubTrendingClient client,
        IngestionService ingestion,
        SourceRepository sources,
        @Value("${devradar.ingest.gh-trending.languages:java,python,javascript,typescript,go,rust}") String csv
    ) {
        this.client = client; this.ingestion = ingestion; this.sources = sources;
        this.trackedLanguages = Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.gh-trending.fixed-delay-ms:21600000}", initialDelayString = "${devradar.ingest.gh-trending.initial-delay-ms:60000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("GH_TRENDING source not active; skipping");
            return;
        }
        List<FetchedItem> all = new ArrayList<>();
        for (String lang : trackedLanguages) {
            try {
                all.addAll(client.fetchTrending(lang));
            } catch (Exception e) {
                LOG.warn("GH trending fetch failed lang={}: {}", lang, e.toString());
            }
        }
        ingestion.ingestBatch(src, all);
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
cd backend && mvn -DskipTests compile
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/job/GitHubTrendingIngestor.java
git commit -m "feat(ingest): add @Scheduled GitHubTrendingIngestor (6h cadence, multi-language)"
```

---

## Task 15: GHSAIngestor (@Scheduled job)

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/job/GHSAIngestor.java`

- [ ] **Step 1: Create the ingestor**

```java
package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.GHSAClient;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GHSAIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(GHSAIngestor.class);
    private static final String CODE = "GHSA";

    private final GHSAClient client;
    private final IngestionService ingestion;
    private final SourceRepository sources;

    public GHSAIngestor(GHSAClient client, IngestionService ingestion, SourceRepository sources) {
        this.client = client; this.ingestion = ingestion; this.sources = sources;
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.ghsa.fixed-delay-ms:1800000}", initialDelayString = "${devradar.ingest.ghsa.initial-delay-ms:90000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("GHSA source not active; skipping");
            return;
        }
        try {
            var items = client.fetchRecent();
            ingestion.ingestBatch(src, items);
        } catch (Exception e) {
            LOG.warn("GHSA ingestion failed: {}", e.toString());
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
cd backend && mvn -DskipTests compile
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/job/GHSAIngestor.java
git commit -m "feat(ingest): add @Scheduled GHSAIngestor (30m cadence)"
```

---

## Task 16: Full pipeline integration test (WireMock + Testcontainers)

**Files:**
- Create: `backend/src/test/java/com/devradar/ingest/IngestionServiceIT.java`

- [ ] **Step 1: Write the test**

```java
// backend/src/test/java/com/devradar/ingest/IngestionServiceIT.java
package com.devradar.ingest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.Source;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceItemTagRepository;
import com.devradar.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionServiceIT extends AbstractIntegrationTest {

    @Autowired IngestionService ingestion;
    @Autowired SourceRepository sources;
    @Autowired SourceItemRepository items;
    @Autowired SourceItemTagRepository tags;

    @Test
    void ingestBatch_persistsItemsAndExtractsTags() {
        Source hn = sources.findByCode("HN").orElseThrow();

        List<FetchedItem> batch = List.of(
            new FetchedItem("hn-1001", "https://example.com/1", "Spring Boot 3.5 release notes", "alice", Instant.now(), "{}", List.of()),
            new FetchedItem("hn-1002", "https://example.com/2", "React 19 hooks deep dive", "bob", Instant.now(), "{}", List.of())
        );

        int inserted = ingestion.ingestBatch(hn, batch);

        assertThat(inserted).isEqualTo(2);
        assertThat(items.findBySourceIdAndExternalId(hn.getId(), "hn-1001")).isPresent();
        assertThat(tags.count()).isGreaterThanOrEqualTo(2); // spring_boot + react matched
    }

    @Test
    void ingestBatch_isIdempotent_onRepeatCall() {
        Source hn = sources.findByCode("HN").orElseThrow();

        FetchedItem dup = new FetchedItem("hn-2000", "https://example.com/x", "Python news", "x", Instant.now(), "{}", List.of());

        int firstRun = ingestion.ingestBatch(hn, List.of(dup));
        int secondRun = ingestion.ingestBatch(hn, List.of(dup));

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isEqualTo(0); // dedup short-circuits
    }

    @Test
    void ingestBatch_continuesPastFailingItems() {
        Source hn = sources.findByCode("HN").orElseThrow();

        // Title length > 1000 will violate column length, surfaced as a runtime exception per item.
        String tooLong = "x".repeat(2000);
        List<FetchedItem> batch = List.of(
            new FetchedItem("hn-3001", "https://example.com/a", tooLong, "x", Instant.now(), "{}", List.of()),
            new FetchedItem("hn-3002", "https://example.com/b", "MySQL 8 vs 9 benchmarks", "y", Instant.now(), "{}", List.of())
        );

        int inserted = ingestion.ingestBatch(hn, batch);

        assertThat(inserted).isEqualTo(1); // bad item skipped, good item persisted
        assertThat(items.findBySourceIdAndExternalId(hn.getId(), "hn-3002")).isPresent();
    }
}
```

- [ ] **Step 2: Run — expect 3/3 PASS**

```bash
cd backend && mvn -Dtest=IngestionServiceIT test
```

- [ ] **Step 3: Run full suite to confirm no regressions**

```bash
cd backend && mvn test
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/devradar/ingest/IngestionServiceIT.java
git commit -m "test(ingest): add full pipeline integration test (Testcontainers MySQL + Redis)"
```

---

## Task 17: Disable scheduling in tests

**Files:**
- Modify: `backend/src/test/resources/application-test.yml`

The `@Scheduled` jobs would otherwise fire during test runs and hit external APIs (which they shouldn't). Disable scheduling in the test profile.

- [ ] **Step 1: Append to `application-test.yml`**

```yaml
devradar:
  ingest:
    hn:
      fixed-delay-ms: 86400000          # 1 day — effectively disabled during tests
      initial-delay-ms: 86400000
    gh-trending:
      fixed-delay-ms: 86400000
      initial-delay-ms: 86400000
    ghsa:
      fixed-delay-ms: 86400000
      initial-delay-ms: 86400000
```

- [ ] **Step 2: Verify full suite still passes**

```bash
cd backend && mvn test
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/resources/application-test.yml
git commit -m "test(ingest): defer @Scheduled jobs in test profile to avoid external HTTP calls"
```

---

## Task 18: README + manual ingestion smoke test

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Append a "Plan 2 — Ingestion" section to `backend/README.md`**

```markdown
## Plan 2 — Ingestion

Three `@Scheduled` jobs continuously populate `source_items` from external sources.

| Source code | Job | Cadence (default) | Client |
|---|---|---|---|
| `HN` | HackerNewsIngestor | every 1h | Algolia REST API |
| `GH_TRENDING` | GitHubTrendingIngestor | every 6h | github.com/trending HTML scrape |
| `GHSA` | GHSAIngestor | every 30m | api.github.com/advisories |

Each item passes through:
1. Redis SETNX dedup lock (5 min TTL)
2. MySQL `(source_id, external_id)` unique constraint as backstop
3. `TagExtractor` matches the title against the `interest_tags` taxonomy + explicit topics
4. INSERT into `source_items` + `source_item_tags`

### Local manual ingestion check

```bash
cd backend
docker compose up -d
mvn spring-boot:run

# In another terminal, after ~30s wait, the HN ingestor will fire
mysql -h 127.0.0.1 -P ${DB_HOST_PORT:-3306} -udevradar -pdevradar devradar -e \
  "SELECT s.code, COUNT(*) FROM source_items i JOIN sources s ON s.id=i.source_id GROUP BY s.code;"
```

You should see rows accumulate over time. Stop the app: kill the `mvn` process.
```

- [ ] **Step 2: Commit**

```bash
git add backend/README.md
git commit -m "docs(backend): document Plan 2 ingestion sources, cadences, and pipeline"
```

---

## Plan 2 Done — End-to-End Verification

- [ ] **Step 1: Run the full test suite**

```bash
cd backend && mvn -B verify
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Optional manual smoke**

Bring up Docker, start the app, wait ~2 minutes, query MySQL to see real rows being ingested:

```bash
cd backend
DB_HOST_PORT=${DB_HOST_PORT:-3307} docker compose up -d
DB_HOST_PORT=${DB_HOST_PORT:-3307} mvn spring-boot:run &
APP=$!
sleep 120

docker compose exec mysql mysql -udevradar -pdevradar devradar -e \
  "SELECT s.code, COUNT(*) AS items FROM source_items i JOIN sources s ON s.id=i.source_id GROUP BY s.code;"

docker compose exec mysql mysql -udevradar -pdevradar devradar -e \
  "SELECT t.slug, COUNT(*) FROM source_item_tags st JOIN interest_tags t ON t.id=st.interest_tag_id GROUP BY t.slug ORDER BY 2 DESC LIMIT 10;"

kill $APP
```

You should see real HN rows (the first scheduled fire happens ~30s after startup) and tag distributions.

Plan 2 complete. Move to **Plan 3: AI Radar Generation**.
