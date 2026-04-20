# Dev Radar — Plan 5: Eval Harness + Observability Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an eval harness that scores radar quality (relevance, citations, distinctness, cost discipline) via LLM-as-judge and programmatic checks, plus a public observability dashboard exposing cost, latency, cache, and eval metrics — the two highest-signal portfolio artifacts for demonstrating production AI discipline.

**Architecture:** A `com.devradar.eval` module runs eval suites against existing radars — programmatic checkers for citations and cost, LLM-as-judge (Sonnet) for relevance and distinctness. A `com.devradar.observability` module aggregates daily metrics into a `metrics_daily_rollup` table via a nightly `@Scheduled` job and serves them through public REST endpoints. Micrometer instruments existing services for real-time Prometheus scraping. Redis daily counters track model calls and cache stats reliably across app restarts.

**Tech Stack:** Spring Boot Actuator, Micrometer + micrometer-registry-prometheus, existing AiClient interface (Sonnet as judge), Redis (daily counters), JUnit 5 + Testcontainers, Mockito + AssertJ.

---

## File Structure

```
backend/
├── pom.xml                                          (modify: + actuator, micrometer-prometheus)
├── src/main/
│   ├── java/com/devradar/
│   │   ├── config/
│   │   │   └── SecurityConfig.java                 (modify: permitAll observability + actuator)
│   │   ├── domain/
│   │   │   ├── EvalRun.java                        (entity)
│   │   │   ├── EvalRunStatus.java                  (enum: RUNNING, COMPLETED, FAILED)
│   │   │   ├── EvalScore.java                      (entity)
│   │   │   ├── EvalScoreCategory.java              (enum: RELEVANCE, CITATIONS, DISTINCTNESS, COST_DISCIPLINE)
│   │   │   └── MetricsDailyRollup.java             (entity)
│   │   ├── repository/
│   │   │   ├── EvalRunRepository.java
│   │   │   ├── EvalScoreRepository.java
│   │   │   ├── MetricsDailyRollupRepository.java
│   │   │   └── RadarRepository.java                (modify: + aggregation queries)
│   │   ├── eval/
│   │   │   ├── GoldenRadarCase.java                (record: interests, items, expectedThemes)
│   │   │   ├── GoldenDatasetLoader.java            (loads JSON fixtures from classpath)
│   │   │   ├── CitationChecker.java                (programmatic: claims backed by cited items?)
│   │   │   ├── CostDisciplineChecker.java          (programmatic: tokens/calls within budget?)
│   │   │   ├── LlmJudge.java                       (Sonnet-as-judge: relevance + distinctness)
│   │   │   ├── EvalService.java                    (orchestrates full eval run)
│   │   │   └── application/
│   │   │       └── EvalApplicationService.java     (admin-facing facade)
│   │   ├── observability/
│   │   │   ├── DailyMetricsCounter.java            (Redis daily INCR/GET for model + cache stats)
│   │   │   ├── MetricsAggregationJob.java          (@Scheduled nightly rollup)
│   │   │   ├── ObservabilityService.java           (reads metrics_daily_rollup for API)
│   │   │   └── application/
│   │   │       └── ObservabilityApplicationService.java
│   │   ├── ai/
│   │   │   ├── AnthropicAiClient.java              (modify: + Micrometer counters + Redis daily)
│   │   │   └── AiSummaryCache.java                 (modify: + hit/miss counters + Redis daily)
│   │   ├── radar/
│   │   │   ├── RadarService.java                   (modify: persist split token counts)
│   │   │   └── RadarGenerationService.java         (modify: + generation timer, pass split tokens)
│   │   ├── ingest/
│   │   │   └── IngestionService.java               (modify: + Micrometer batch counters)
│   │   └── web/rest/
│   │       ├── ObservabilityResource.java          (public: GET summary + timeseries)
│   │       ├── EvalResource.java                   (admin: POST run + GET runs)
│   │       └── dto/
│   │           ├── ObservabilitySummaryDTO.java
│   │           ├── MetricsDayDTO.java
│   │           ├── EvalRunDTO.java
│   │           └── EvalScoreDTO.java
│   └── resources/
│       ├── db/changelog/
│       │   ├── db.changelog-master.xml             (modify: + 009, 010, 011)
│       │   ├── 009-eval-schema.xml
│       │   ├── 010-metrics-daily-rollup.xml
│       │   └── 011-radar-token-split.xml
│       └── evals/
│           └── golden_radars/
│               ├── case_01_java_developer.json
│               └── case_02_frontend_developer.json
└── src/test/
    └── java/com/devradar/
        ├── eval/
        │   ├── GoldenDatasetLoaderTest.java
        │   ├── CitationCheckerTest.java
        │   ├── CostDisciplineCheckerTest.java
        │   ├── LlmJudgeTest.java
        │   └── EvalServiceTest.java
        ├── observability/
        │   ├── DailyMetricsCounterTest.java
        │   └── MetricsAggregationJobIT.java
        └── web/rest/
            ├── ObservabilityResourceIT.java
            └── EvalResourceIT.java
```

---

## Task 1: Dependencies + Actuator/Micrometer Config + SecurityConfig

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`
- Modify: `backend/src/main/java/com/devradar/config/SecurityConfig.java`

- [ ] **Step 1: Add Actuator + Micrometer dependencies to pom.xml**

Add to the `<dependencies>` section of `backend/pom.xml`:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
```

- [ ] **Step 2: Configure Actuator endpoints in application.yml**

Append to `backend/src/main/resources/application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
  endpoint:
    health:
      show-details: when-authorized
  prometheus:
    metrics:
      export:
        enabled: true
```

- [ ] **Step 3: Configure Actuator in application-test.yml**

Append to `backend/src/test/resources/application-test.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
```

- [ ] **Step 4: Update SecurityConfig to permit observability endpoints**

In `backend/src/main/java/com/devradar/config/SecurityConfig.java`, add to the `requestMatchers(...).permitAll()` chain:

```java
.requestMatchers("/api/auth/**").permitAll()
.requestMatchers("/api/interest-tags/**").permitAll()
.requestMatchers("/actuator/health").permitAll()
.requestMatchers("/actuator/prometheus").permitAll()
.requestMatchers("/api/observability/**").permitAll()
```

- [ ] **Step 5: Verify build compiles**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/test/resources/application-test.yml backend/src/main/java/com/devradar/config/SecurityConfig.java
git commit -m "chore(observability): add Actuator + Micrometer Prometheus dependencies and config"
```

---

## Task 2: Liquibase Migrations (Eval + Metrics + Radar Token Split)

**Files:**
- Create: `backend/src/main/resources/db/changelog/009-eval-schema.xml`
- Create: `backend/src/main/resources/db/changelog/010-metrics-daily-rollup.xml`
- Create: `backend/src/main/resources/db/changelog/011-radar-token-split.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create eval schema migration**

Create `backend/src/main/resources/db/changelog/009-eval-schema.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="009-01" author="devradar">
        <createTable tableName="eval_runs">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="status" type="VARCHAR(20)">
                <constraints nullable="false"/>
            </column>
            <column name="radar_count" type="INT">
                <constraints nullable="false"/>
            </column>
            <column name="started_at" type="TIMESTAMP">
                <constraints nullable="false"/>
            </column>
            <column name="completed_at" type="TIMESTAMP"/>
            <column name="error_message" type="VARCHAR(1000)"/>
            <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>

    <changeSet id="009-02" author="devradar">
        <createTable tableName="eval_scores">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="eval_run_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_eval_scores_eval_run"
                             references="eval_runs(id)"/>
            </column>
            <column name="category" type="VARCHAR(50)">
                <constraints nullable="false"/>
            </column>
            <column name="score" type="DECIMAL(5,3)">
                <constraints nullable="false"/>
            </column>
            <column name="details" type="JSON"/>
            <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
        </createTable>
        <createIndex tableName="eval_scores" indexName="idx_eval_scores_run_id">
            <column name="eval_run_id"/>
        </createIndex>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Create metrics daily rollup migration**

Create `backend/src/main/resources/db/changelog/010-metrics-daily-rollup.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="010-01" author="devradar">
        <createTable tableName="metrics_daily_rollup">
            <column name="date" type="DATE">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="total_radars" type="INT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="total_tokens_input" type="BIGINT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="total_tokens_output" type="BIGINT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="sonnet_calls" type="INT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="haiku_calls" type="INT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="cache_hits" type="INT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="cache_misses" type="INT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="p50_ms" type="BIGINT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="p95_ms" type="BIGINT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="avg_generation_ms" type="BIGINT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="items_ingested" type="INT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="items_deduped" type="INT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="eval_score_relevance" type="DECIMAL(5,3)"/>
            <column name="eval_score_citations" type="DECIMAL(5,3)"/>
            <column name="eval_score_distinctness" type="DECIMAL(5,3)"/>
            <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
            <column name="updated_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 3: Create radar token split migration**

Create `backend/src/main/resources/db/changelog/011-radar-token-split.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="011-01" author="devradar">
        <addColumn tableName="radars">
            <column name="input_token_count" type="INT"/>
            <column name="output_token_count" type="INT"/>
        </addColumn>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 4: Register new changelogs in master**

Add to `backend/src/main/resources/db/changelog/db.changelog-master.xml`, after the `008` include:

```xml
    <include file="db/changelog/009-eval-schema.xml"/>
    <include file="db/changelog/010-metrics-daily-rollup.xml"/>
    <include file="db/changelog/011-radar-token-split.xml"/>
```

- [ ] **Step 5: Verify migrations run**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS (Liquibase will run on next app start or test run)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/changelog/
git commit -m "feat(observability): add Liquibase migrations for eval, metrics rollup, and radar token split"
```

---

## Task 3: Eval Domain Entities + Repositories (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/domain/EvalRunStatus.java`
- Create: `backend/src/main/java/com/devradar/domain/EvalScoreCategory.java`
- Create: `backend/src/main/java/com/devradar/domain/EvalRun.java`
- Create: `backend/src/main/java/com/devradar/domain/EvalScore.java`
- Create: `backend/src/main/java/com/devradar/repository/EvalRunRepository.java`
- Create: `backend/src/main/java/com/devradar/repository/EvalScoreRepository.java`
- Create: `backend/src/test/java/com/devradar/eval/EvalRepositoryIT.java`

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/com/devradar/eval/EvalRepositoryIT.java`:

```java
package com.devradar.eval;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.EvalRun;
import com.devradar.domain.EvalRunStatus;
import com.devradar.domain.EvalScore;
import com.devradar.domain.EvalScoreCategory;
import com.devradar.repository.EvalRunRepository;
import com.devradar.repository.EvalScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private EvalRunRepository evalRunRepository;

    @Autowired
    private EvalScoreRepository evalScoreRepository;

    @Test
    void shouldPersistEvalRunAndScores() {
        var run = new EvalRun();
        run.setStatus(EvalRunStatus.RUNNING);
        run.setRadarCount(5);
        run.setStartedAt(Instant.now());
        run = evalRunRepository.save(run);

        assertThat(run.getId()).isNotNull();
        assertThat(run.getCreatedAt()).isNotNull();

        var score = new EvalScore();
        score.setEvalRunId(run.getId());
        score.setCategory(EvalScoreCategory.RELEVANCE);
        score.setScore(new BigDecimal("0.850"));
        score.setDetails("{\"avg\": 0.85, \"cases\": 5}");
        score = evalScoreRepository.save(score);

        assertThat(score.getId()).isNotNull();

        List<EvalScore> scores = evalScoreRepository.findByEvalRunId(run.getId());
        assertThat(scores).hasSize(1);
        assertThat(scores.getFirst().getCategory()).isEqualTo(EvalScoreCategory.RELEVANCE);
    }

    @Test
    void shouldFindRecentEvalRuns() {
        var run = new EvalRun();
        run.setStatus(EvalRunStatus.COMPLETED);
        run.setRadarCount(3);
        run.setStartedAt(Instant.now().minusSeconds(60));
        run.setCompletedAt(Instant.now());
        evalRunRepository.save(run);

        var runs = evalRunRepository.findAllByOrderByCreatedAtDesc();
        assertThat(runs).isNotEmpty();
        assertThat(runs.getFirst().getStatus()).isEqualTo(EvalRunStatus.COMPLETED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=EvalRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (classes not found)

- [ ] **Step 3: Create EvalRunStatus enum**

Create `backend/src/main/java/com/devradar/domain/EvalRunStatus.java`:

```java
package com.devradar.domain;

public enum EvalRunStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
```

- [ ] **Step 4: Create EvalScoreCategory enum**

Create `backend/src/main/java/com/devradar/domain/EvalScoreCategory.java`:

```java
package com.devradar.domain;

public enum EvalScoreCategory {
    RELEVANCE,
    CITATIONS,
    DISTINCTNESS,
    COST_DISCIPLINE
}
```

- [ ] **Step 5: Create EvalRun entity**

Create `backend/src/main/java/com/devradar/domain/EvalRun.java`:

```java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "eval_runs")
public class EvalRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvalRunStatus status;

    @Column(name = "radar_count", nullable = false)
    private int radarCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false,
            insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EvalRunStatus getStatus() { return status; }
    public void setStatus(EvalRunStatus status) { this.status = status; }

    public int getRadarCount() { return radarCount; }
    public void setRadarCount(int radarCount) { this.radarCount = radarCount; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 6: Create EvalScore entity**

Create `backend/src/main/java/com/devradar/domain/EvalScore.java`:

```java
package com.devradar.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "eval_scores")
public class EvalScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "eval_run_id", nullable = false)
    private Long evalRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EvalScoreCategory category;

    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal score;

    @Column(columnDefinition = "JSON")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false,
            insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEvalRunId() { return evalRunId; }
    public void setEvalRunId(Long evalRunId) { this.evalRunId = evalRunId; }

    public EvalScoreCategory getCategory() { return category; }
    public void setCategory(EvalScoreCategory category) { this.category = category; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 7: Create EvalRunRepository**

Create `backend/src/main/java/com/devradar/repository/EvalRunRepository.java`:

```java
package com.devradar.repository;

import com.devradar.domain.EvalRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalRunRepository extends JpaRepository<EvalRun, Long> {
    List<EvalRun> findAllByOrderByCreatedAtDesc();
}
```

- [ ] **Step 8: Create EvalScoreRepository**

Create `backend/src/main/java/com/devradar/repository/EvalScoreRepository.java`:

```java
package com.devradar.repository;

import com.devradar.domain.EvalScore;
import com.devradar.domain.EvalScoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvalScoreRepository extends JpaRepository<EvalScore, Long> {
    List<EvalScore> findByEvalRunId(Long evalRunId);
    Optional<EvalScore> findTopByCategory(EvalScoreCategory category);
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=EvalRepositoryIT`
Expected: PASS (both tests green)

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/devradar/domain/EvalRun.java backend/src/main/java/com/devradar/domain/EvalRunStatus.java backend/src/main/java/com/devradar/domain/EvalScore.java backend/src/main/java/com/devradar/domain/EvalScoreCategory.java backend/src/main/java/com/devradar/repository/EvalRunRepository.java backend/src/main/java/com/devradar/repository/EvalScoreRepository.java backend/src/test/java/com/devradar/eval/EvalRepositoryIT.java
git commit -m "feat(eval): add EvalRun and EvalScore domain entities with repositories"
```

---

## Task 4: MetricsDailyRollup Entity + Repository (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/domain/MetricsDailyRollup.java`
- Create: `backend/src/main/java/com/devradar/repository/MetricsDailyRollupRepository.java`
- Create: `backend/src/test/java/com/devradar/observability/MetricsDailyRollupRepositoryIT.java`

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/com/devradar/observability/MetricsDailyRollupRepositoryIT.java`:

```java
package com.devradar.observability;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.MetricsDailyRollup;
import com.devradar.repository.MetricsDailyRollupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsDailyRollupRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private MetricsDailyRollupRepository repository;

    @Test
    void shouldUpsertAndRetrieveRollup() {
        var rollup = new MetricsDailyRollup();
        rollup.setDate(LocalDate.of(2026, 4, 20));
        rollup.setTotalRadars(10);
        rollup.setTotalTokensInput(50000L);
        rollup.setTotalTokensOutput(12000L);
        rollup.setSonnetCalls(40);
        rollup.setHaikuCalls(80);
        rollup.setCacheHits(15);
        rollup.setCacheMisses(5);
        rollup.setP50Ms(2500L);
        rollup.setP95Ms(8000L);
        rollup.setAvgGenerationMs(3200L);
        rollup.setItemsIngested(150);
        rollup.setItemsDeduped(30);
        rollup.setEvalScoreRelevance(new BigDecimal("0.850"));
        rollup.setEvalScoreCitations(new BigDecimal("0.920"));
        rollup.setEvalScoreDistinctness(new BigDecimal("0.780"));
        repository.save(rollup);

        var found = repository.findById(LocalDate.of(2026, 4, 20));
        assertThat(found).isPresent();
        assertThat(found.get().getTotalRadars()).isEqualTo(10);
        assertThat(found.get().getSonnetCalls()).isEqualTo(40);
    }

    @Test
    void shouldFindRecentDays() {
        for (int i = 0; i < 3; i++) {
            var rollup = new MetricsDailyRollup();
            rollup.setDate(LocalDate.of(2026, 4, 18 + i));
            rollup.setTotalRadars(i + 1);
            rollup.setTotalTokensInput(0L);
            rollup.setTotalTokensOutput(0L);
            repository.save(rollup);
        }

        List<MetricsDailyRollup> recent = repository.findByDateBetweenOrderByDateDesc(
                LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 20));
        assertThat(recent).hasSizeGreaterThanOrEqualTo(3);
        assertThat(recent.getFirst().getDate()).isAfterOrEqualTo(recent.getLast().getDate());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=MetricsDailyRollupRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (classes not found)

- [ ] **Step 3: Create MetricsDailyRollup entity**

Create `backend/src/main/java/com/devradar/domain/MetricsDailyRollup.java`:

```java
package com.devradar.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "metrics_daily_rollup")
public class MetricsDailyRollup {

    @Id
    private LocalDate date;

    @Column(name = "total_radars", nullable = false)
    private int totalRadars;

    @Column(name = "total_tokens_input", nullable = false)
    private long totalTokensInput;

    @Column(name = "total_tokens_output", nullable = false)
    private long totalTokensOutput;

    @Column(name = "sonnet_calls", nullable = false)
    private int sonnetCalls;

    @Column(name = "haiku_calls", nullable = false)
    private int haikuCalls;

    @Column(name = "cache_hits", nullable = false)
    private int cacheHits;

    @Column(name = "cache_misses", nullable = false)
    private int cacheMisses;

    @Column(name = "p50_ms", nullable = false)
    private long p50Ms;

    @Column(name = "p95_ms", nullable = false)
    private long p95Ms;

    @Column(name = "avg_generation_ms", nullable = false)
    private long avgGenerationMs;

    @Column(name = "items_ingested", nullable = false)
    private int itemsIngested;

    @Column(name = "items_deduped", nullable = false)
    private int itemsDeduped;

    @Column(name = "eval_score_relevance", precision = 5, scale = 3)
    private BigDecimal evalScoreRelevance;

    @Column(name = "eval_score_citations", precision = 5, scale = 3)
    private BigDecimal evalScoreCitations;

    @Column(name = "eval_score_distinctness", precision = 5, scale = 3)
    private BigDecimal evalScoreDistinctness;

    @Column(name = "created_at", nullable = false, updatable = false,
            insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false,
            insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant updatedAt;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getTotalRadars() { return totalRadars; }
    public void setTotalRadars(int totalRadars) { this.totalRadars = totalRadars; }

    public long getTotalTokensInput() { return totalTokensInput; }
    public void setTotalTokensInput(long totalTokensInput) { this.totalTokensInput = totalTokensInput; }

    public long getTotalTokensOutput() { return totalTokensOutput; }
    public void setTotalTokensOutput(long totalTokensOutput) { this.totalTokensOutput = totalTokensOutput; }

    public int getSonnetCalls() { return sonnetCalls; }
    public void setSonnetCalls(int sonnetCalls) { this.sonnetCalls = sonnetCalls; }

    public int getHaikuCalls() { return haikuCalls; }
    public void setHaikuCalls(int haikuCalls) { this.haikuCalls = haikuCalls; }

    public int getCacheHits() { return cacheHits; }
    public void setCacheHits(int cacheHits) { this.cacheHits = cacheHits; }

    public int getCacheMisses() { return cacheMisses; }
    public void setCacheMisses(int cacheMisses) { this.cacheMisses = cacheMisses; }

    public long getP50Ms() { return p50Ms; }
    public void setP50Ms(long p50Ms) { this.p50Ms = p50Ms; }

    public long getP95Ms() { return p95Ms; }
    public void setP95Ms(long p95Ms) { this.p95Ms = p95Ms; }

    public long getAvgGenerationMs() { return avgGenerationMs; }
    public void setAvgGenerationMs(long avgGenerationMs) { this.avgGenerationMs = avgGenerationMs; }

    public int getItemsIngested() { return itemsIngested; }
    public void setItemsIngested(int itemsIngested) { this.itemsIngested = itemsIngested; }

    public int getItemsDeduped() { return itemsDeduped; }
    public void setItemsDeduped(int itemsDeduped) { this.itemsDeduped = itemsDeduped; }

    public BigDecimal getEvalScoreRelevance() { return evalScoreRelevance; }
    public void setEvalScoreRelevance(BigDecimal evalScoreRelevance) { this.evalScoreRelevance = evalScoreRelevance; }

    public BigDecimal getEvalScoreCitations() { return evalScoreCitations; }
    public void setEvalScoreCitations(BigDecimal evalScoreCitations) { this.evalScoreCitations = evalScoreCitations; }

    public BigDecimal getEvalScoreDistinctness() { return evalScoreDistinctness; }
    public void setEvalScoreDistinctness(BigDecimal evalScoreDistinctness) { this.evalScoreDistinctness = evalScoreDistinctness; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 4: Create MetricsDailyRollupRepository**

Create `backend/src/main/java/com/devradar/repository/MetricsDailyRollupRepository.java`:

```java
package com.devradar.repository;

import com.devradar.domain.MetricsDailyRollup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MetricsDailyRollupRepository extends JpaRepository<MetricsDailyRollup, LocalDate> {
    List<MetricsDailyRollup> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=MetricsDailyRollupRepositoryIT`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/domain/MetricsDailyRollup.java backend/src/main/java/com/devradar/repository/MetricsDailyRollupRepository.java backend/src/test/java/com/devradar/observability/MetricsDailyRollupRepositoryIT.java
git commit -m "feat(observability): add MetricsDailyRollup entity and repository"
```

---

## Task 5: Radar Token Split — Persist Input/Output Separately

**Files:**
- Modify: `backend/src/main/java/com/devradar/domain/Radar.java`
- Modify: `backend/src/main/java/com/devradar/radar/RadarService.java`
- Modify: `backend/src/main/java/com/devradar/radar/RadarGenerationService.java`
- Modify: `backend/src/main/java/com/devradar/radar/event/RadarCompleteEvent.java`
- Modify: `backend/src/main/java/com/devradar/repository/RadarRepository.java`

- [ ] **Step 1: Add input/output token fields to Radar entity**

Add to `backend/src/main/java/com/devradar/domain/Radar.java`:

```java
    @Column(name = "input_token_count")
    private Integer inputTokenCount;

    @Column(name = "output_token_count")
    private Integer outputTokenCount;
```

Add getters/setters:

```java
    public Integer getInputTokenCount() { return inputTokenCount; }
    public void setInputTokenCount(Integer inputTokenCount) { this.inputTokenCount = inputTokenCount; }

    public Integer getOutputTokenCount() { return outputTokenCount; }
    public void setOutputTokenCount(Integer outputTokenCount) { this.outputTokenCount = outputTokenCount; }
```

- [ ] **Step 2: Update RadarService.markReady to accept split tokens**

Modify `RadarService.markReady` in `backend/src/main/java/com/devradar/radar/RadarService.java` to accept and persist split token counts. Change the signature from:

```java
public void markReady(Long radarId, long generationMs, int tokenCount) {
```

to:

```java
public void markReady(Long radarId, long generationMs, int tokenCount, int inputTokens, int outputTokens) {
```

Inside the method, after setting `tokenCount`, add:

```java
        radar.setInputTokenCount(inputTokens);
        radar.setOutputTokenCount(outputTokens);
```

- [ ] **Step 3: Update RadarGenerationService to pass split tokens**

In `backend/src/main/java/com/devradar/radar/RadarGenerationService.java`, locate the `markReady` call and update it:

Change from:

```java
radarService.markReady(radarId, elapsed, tokens);
```

to:

```java
radarService.markReady(radarId, elapsed, tokens, result.totalInputTokens(), result.totalOutputTokens());
```

- [ ] **Step 4: Add aggregation queries to RadarRepository**

Add these methods to `backend/src/main/java/com/devradar/repository/RadarRepository.java`:

```java
    @Query("SELECT COUNT(r) FROM Radar r WHERE r.status = 'READY' AND FUNCTION('DATE', r.generatedAt) = :date")
    int countReadyByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(r.inputTokenCount), 0) FROM Radar r WHERE r.status = 'READY' AND FUNCTION('DATE', r.generatedAt) = :date")
    long sumInputTokensByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(r.outputTokenCount), 0) FROM Radar r WHERE r.status = 'READY' AND FUNCTION('DATE', r.generatedAt) = :date")
    long sumOutputTokensByDate(@Param("date") LocalDate date);

    @Query("SELECT r.generationMs FROM Radar r WHERE r.status = 'READY' AND FUNCTION('DATE', r.generatedAt) = :date ORDER BY r.generationMs ASC")
    List<Long> findGenerationMsByDate(@Param("date") LocalDate date);
```

Add the necessary imports:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
```

- [ ] **Step 5: Verify existing tests still pass**

Run: `cd backend && mvn test`
Expected: All existing tests PASS (existing callers of markReady need the two new args — update any test that calls markReady directly, e.g. in `RadarGenerationServiceIT`, adding `, 100, 50` for input/output tokens)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/domain/Radar.java backend/src/main/java/com/devradar/radar/RadarService.java backend/src/main/java/com/devradar/radar/RadarGenerationService.java backend/src/main/java/com/devradar/repository/RadarRepository.java
git commit -m "feat(radar): persist input and output token counts separately for observability"
```

---

## Task 6: DailyMetricsCounter — Redis Daily Counters (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/observability/DailyMetricsCounter.java`
- Create: `backend/src/test/java/com/devradar/observability/DailyMetricsCounterTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/devradar/observability/DailyMetricsCounterTest.java`:

```java
package com.devradar.observability;

import com.devradar.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyMetricsCounterTest extends AbstractIntegrationTest {

    @Autowired
    private DailyMetricsCounter counter;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void cleanRedis() {
        var keys = redis.keys("metrics:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void shouldIncrementAndReadSonnetCalls() {
        var today = LocalDate.of(2026, 4, 20);
        counter.incrementSonnetCalls(today);
        counter.incrementSonnetCalls(today);
        counter.incrementSonnetCalls(today);

        assertThat(counter.getSonnetCalls(today)).isEqualTo(3);
    }

    @Test
    void shouldIncrementAndReadHaikuCalls() {
        var today = LocalDate.of(2026, 4, 20);
        counter.incrementHaikuCalls(today);

        assertThat(counter.getHaikuCalls(today)).isEqualTo(1);
    }

    @Test
    void shouldTrackCacheHitsAndMisses() {
        var today = LocalDate.of(2026, 4, 20);
        counter.incrementCacheHit(today);
        counter.incrementCacheHit(today);
        counter.incrementCacheMiss(today);

        assertThat(counter.getCacheHits(today)).isEqualTo(2);
        assertThat(counter.getCacheMisses(today)).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroForMissingDate() {
        var missing = LocalDate.of(2020, 1, 1);
        assertThat(counter.getSonnetCalls(missing)).isZero();
        assertThat(counter.getCacheHits(missing)).isZero();
    }

    @Test
    void shouldTrackItemsIngestedAndDeduped() {
        var today = LocalDate.of(2026, 4, 20);
        counter.incrementItemsIngested(today, 25);
        counter.incrementItemsDeduped(today, 5);

        assertThat(counter.getItemsIngested(today)).isEqualTo(25);
        assertThat(counter.getItemsDeduped(today)).isEqualTo(5);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=DailyMetricsCounterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (class not found)

- [ ] **Step 3: Implement DailyMetricsCounter**

Create `backend/src/main/java/com/devradar/observability/DailyMetricsCounter.java`:

```java
package com.devradar.observability;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

@Component
public class DailyMetricsCounter {

    private static final Duration TTL = Duration.ofDays(7);
    private final StringRedisTemplate redis;

    public DailyMetricsCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void incrementSonnetCalls(LocalDate date) {
        increment(key(date, "sonnet_calls"), 1);
    }

    public void incrementHaikuCalls(LocalDate date) {
        increment(key(date, "haiku_calls"), 1);
    }

    public void incrementCacheHit(LocalDate date) {
        increment(key(date, "cache_hits"), 1);
    }

    public void incrementCacheMiss(LocalDate date) {
        increment(key(date, "cache_misses"), 1);
    }

    public void incrementItemsIngested(LocalDate date, int count) {
        increment(key(date, "items_ingested"), count);
    }

    public void incrementItemsDeduped(LocalDate date, int count) {
        increment(key(date, "items_deduped"), count);
    }

    public int getSonnetCalls(LocalDate date) { return get(key(date, "sonnet_calls")); }
    public int getHaikuCalls(LocalDate date) { return get(key(date, "haiku_calls")); }
    public int getCacheHits(LocalDate date) { return get(key(date, "cache_hits")); }
    public int getCacheMisses(LocalDate date) { return get(key(date, "cache_misses")); }
    public int getItemsIngested(LocalDate date) { return get(key(date, "items_ingested")); }
    public int getItemsDeduped(LocalDate date) { return get(key(date, "items_deduped")); }

    private void increment(String key, int delta) {
        redis.opsForValue().increment(key, delta);
        redis.expire(key, TTL);
    }

    private int get(String key) {
        String val = redis.opsForValue().get(key);
        return val == null ? 0 : Integer.parseInt(val);
    }

    private String key(LocalDate date, String metric) {
        return "metrics:" + date + ":" + metric;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=DailyMetricsCounterTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/observability/DailyMetricsCounter.java backend/src/test/java/com/devradar/observability/DailyMetricsCounterTest.java
git commit -m "feat(observability): add Redis-backed DailyMetricsCounter for model calls and cache stats"
```

---

## Task 7: Micrometer Instrumentation on Existing Services

**Files:**
- Modify: `backend/src/main/java/com/devradar/ai/AnthropicAiClient.java`
- Modify: `backend/src/main/java/com/devradar/ai/AiSummaryCache.java`
- Modify: `backend/src/main/java/com/devradar/radar/RadarGenerationService.java`
- Modify: `backend/src/main/java/com/devradar/ingest/IngestionService.java`

- [ ] **Step 1: Instrument AnthropicAiClient**

In `backend/src/main/java/com/devradar/ai/AnthropicAiClient.java`, inject `MeterRegistry` and `DailyMetricsCounter` via constructor, then in the `generate` method, after the API call completes:

```java
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.devradar.observability.DailyMetricsCounter;
import java.time.LocalDate;
```

Add fields:

```java
    private final MeterRegistry meterRegistry;
    private final DailyMetricsCounter dailyMetrics;
```

Update the constructor to accept these two new parameters.

In the `generate` method, wrap the API call with timing and counters:

```java
        var sample = Timer.start(meterRegistry);
        // ... existing API call ...
        sample.stop(Timer.builder("ai.client.duration")
                .tag("model", model)
                .register(meterRegistry));

        Counter.builder("ai.client.tokens")
                .tag("model", model)
                .tag("direction", "input")
                .register(meterRegistry)
                .increment(response.inputTokens());

        Counter.builder("ai.client.tokens")
                .tag("model", model)
                .tag("direction", "output")
                .register(meterRegistry)
                .increment(response.outputTokens());

        var today = LocalDate.now();
        if (model.contains("sonnet")) {
            dailyMetrics.incrementSonnetCalls(today);
        } else if (model.contains("haiku")) {
            dailyMetrics.incrementHaikuCalls(today);
        }
```

- [ ] **Step 2: Instrument AiSummaryCache**

In `backend/src/main/java/com/devradar/ai/AiSummaryCache.java`, inject `MeterRegistry` and `DailyMetricsCounter` via constructor. In the `get` method, after the Redis lookup:

```java
        var today = LocalDate.now();
        if (result.isPresent()) {
            meterRegistry.counter("ai.summary.cache", "result", "hit").increment();
            dailyMetrics.incrementCacheHit(today);
        } else {
            meterRegistry.counter("ai.summary.cache", "result", "miss").increment();
            dailyMetrics.incrementCacheMiss(today);
        }
```

- [ ] **Step 3: Instrument RadarGenerationService**

In `backend/src/main/java/com/devradar/radar/RadarGenerationService.java`, inject `MeterRegistry`. In `runGeneration`, wrap the orchestrator call:

```java
        var sample = Timer.start(meterRegistry);
        // ... existing orchestrator.generate() call ...
        sample.stop(Timer.builder("radar.generation.duration").register(meterRegistry));
        meterRegistry.counter("radar.generation", "status", "success").increment();
```

In the catch block:

```java
        meterRegistry.counter("radar.generation", "status", "failure").increment();
```

- [ ] **Step 4: Instrument IngestionService**

In `backend/src/main/java/com/devradar/ingest/IngestionService.java`, inject `MeterRegistry` and `DailyMetricsCounter`. In `ingestBatch`, after the batch completes:

```java
        var today = LocalDate.now();
        meterRegistry.counter("ingest.items", "source", source.getCode(), "result", "inserted").increment(inserted);
        dailyMetrics.incrementItemsIngested(today, inserted);
        dailyMetrics.incrementItemsDeduped(today, items.size() - inserted);
```

- [ ] **Step 5: Verify all existing tests still pass**

Run: `cd backend && mvn test`
Expected: All tests PASS (MeterRegistry is auto-configured in tests via Actuator; DailyMetricsCounter is available since Redis Testcontainer is already running)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/ai/AnthropicAiClient.java backend/src/main/java/com/devradar/ai/AiSummaryCache.java backend/src/main/java/com/devradar/radar/RadarGenerationService.java backend/src/main/java/com/devradar/ingest/IngestionService.java
git commit -m "feat(observability): instrument AI client, summary cache, radar generation, and ingestion with Micrometer"
```

---

## Task 8: MetricsAggregationJob — Nightly Rollup (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/observability/MetricsAggregationJob.java`
- Create: `backend/src/test/java/com/devradar/observability/MetricsAggregationJobIT.java`

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/com/devradar/observability/MetricsAggregationJobIT.java`:

```java
package com.devradar.observability;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.repository.MetricsDailyRollupRepository;
import com.devradar.repository.RadarRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsAggregationJobIT extends AbstractIntegrationTest {

    @Autowired
    private MetricsAggregationJob job;

    @Autowired
    private RadarRepository radarRepository;

    @Autowired
    private MetricsDailyRollupRepository rollupRepository;

    @Autowired
    private DailyMetricsCounter dailyMetrics;

    @Test
    void shouldAggregateYesterdayMetrics() {
        var yesterday = LocalDate.now().minusDays(1);

        var radar1 = createReadyRadar(1L, 3000L, 500, 100, 400);
        var radar2 = createReadyRadar(2L, 5000L, 800, 200, 600);
        radarRepository.save(radar1);
        radarRepository.save(radar2);

        dailyMetrics.incrementSonnetCalls(yesterday);
        dailyMetrics.incrementSonnetCalls(yesterday);
        dailyMetrics.incrementHaikuCalls(yesterday);
        dailyMetrics.incrementCacheHit(yesterday);
        dailyMetrics.incrementCacheMiss(yesterday);
        dailyMetrics.incrementCacheMiss(yesterday);
        dailyMetrics.incrementItemsIngested(yesterday, 50);
        dailyMetrics.incrementItemsDeduped(yesterday, 10);

        job.aggregateForDate(yesterday);

        var rollup = rollupRepository.findById(yesterday);
        assertThat(rollup).isPresent();
        var r = rollup.get();
        assertThat(r.getTotalRadars()).isEqualTo(2);
        assertThat(r.getSonnetCalls()).isEqualTo(2);
        assertThat(r.getHaikuCalls()).isEqualTo(1);
        assertThat(r.getCacheHits()).isEqualTo(1);
        assertThat(r.getCacheMisses()).isEqualTo(2);
        assertThat(r.getItemsIngested()).isEqualTo(50);
        assertThat(r.getItemsDeduped()).isEqualTo(10);
    }

    private Radar createReadyRadar(Long userId, long generationMs, int tokens, int inputTokens, int outputTokens) {
        var radar = new Radar();
        radar.setUserId(userId);
        radar.setPeriodStart(Instant.now().minusSeconds(604800));
        radar.setPeriodEnd(Instant.now());
        radar.setStatus(RadarStatus.READY);
        radar.setGeneratedAt(Instant.now());
        radar.setGenerationMs(generationMs);
        radar.setTokenCount(tokens);
        radar.setInputTokenCount(inputTokens);
        radar.setOutputTokenCount(outputTokens);
        return radar;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=MetricsAggregationJobIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (MetricsAggregationJob not found)

- [ ] **Step 3: Implement MetricsAggregationJob**

Create `backend/src/main/java/com/devradar/observability/MetricsAggregationJob.java`:

```java
package com.devradar.observability;

import com.devradar.domain.MetricsDailyRollup;
import com.devradar.repository.MetricsDailyRollupRepository;
import com.devradar.repository.RadarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class MetricsAggregationJob {

    private static final Logger log = LoggerFactory.getLogger(MetricsAggregationJob.class);

    private final RadarRepository radarRepository;
    private final MetricsDailyRollupRepository rollupRepository;
    private final DailyMetricsCounter dailyMetrics;

    public MetricsAggregationJob(RadarRepository radarRepository,
                                  MetricsDailyRollupRepository rollupRepository,
                                  DailyMetricsCounter dailyMetrics) {
        this.radarRepository = radarRepository;
        this.rollupRepository = rollupRepository;
        this.dailyMetrics = dailyMetrics;
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void aggregateYesterday() {
        aggregateForDate(LocalDate.now().minusDays(1));
    }

    @Transactional
    public void aggregateForDate(LocalDate date) {
        log.info("aggregating metrics for date={}", date);

        var rollup = rollupRepository.findById(date).orElseGet(() -> {
            var r = new MetricsDailyRollup();
            r.setDate(date);
            return r;
        });

        rollup.setTotalRadars(radarRepository.countReadyByDate(date));
        rollup.setTotalTokensInput(radarRepository.sumInputTokensByDate(date));
        rollup.setTotalTokensOutput(radarRepository.sumOutputTokensByDate(date));

        List<Long> latencies = radarRepository.findGenerationMsByDate(date);
        if (!latencies.isEmpty()) {
            rollup.setP50Ms(percentile(latencies, 50));
            rollup.setP95Ms(percentile(latencies, 95));
            long sum = latencies.stream().mapToLong(Long::longValue).sum();
            rollup.setAvgGenerationMs(sum / latencies.size());
        }

        rollup.setSonnetCalls(dailyMetrics.getSonnetCalls(date));
        rollup.setHaikuCalls(dailyMetrics.getHaikuCalls(date));
        rollup.setCacheHits(dailyMetrics.getCacheHits(date));
        rollup.setCacheMisses(dailyMetrics.getCacheMisses(date));
        rollup.setItemsIngested(dailyMetrics.getItemsIngested(date));
        rollup.setItemsDeduped(dailyMetrics.getItemsDeduped(date));

        rollupRepository.save(rollup);
        log.info("metrics aggregation complete for date={} radars={}", date, rollup.getTotalRadars());
    }

    static long percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
```

- [ ] **Step 4: Add scheduling config to application-test.yml**

Ensure the nightly cron does NOT fire during tests. In `application-test.yml`, add (or verify that existing scheduling config disables it). The `@Scheduled(cron=...)` won't fire in tests since we call `aggregateForDate()` directly. No config change needed.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=MetricsAggregationJobIT`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/observability/MetricsAggregationJob.java backend/src/test/java/com/devradar/observability/MetricsAggregationJobIT.java
git commit -m "feat(observability): add nightly MetricsAggregationJob for daily rollup"
```

---

## Task 9: ObservabilityService + REST Endpoints + IT (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/dto/ObservabilitySummaryDTO.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/MetricsDayDTO.java`
- Create: `backend/src/main/java/com/devradar/observability/ObservabilityService.java`
- Create: `backend/src/main/java/com/devradar/observability/application/ObservabilityApplicationService.java`
- Create: `backend/src/main/java/com/devradar/web/rest/ObservabilityResource.java`
- Create: `backend/src/test/java/com/devradar/web/rest/ObservabilityResourceIT.java`

- [ ] **Step 1: Write the failing IT**

Create `backend/src/test/java/com/devradar/web/rest/ObservabilityResourceIT.java`:

```java
package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.MetricsDailyRollup;
import com.devradar.repository.MetricsDailyRollupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ObservabilityResourceIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MetricsDailyRollupRepository rollupRepository;

    @Test
    void summaryShouldBePublicAndReturn200() throws Exception {
        seedRollup(LocalDate.now().minusDays(1), 5, 10000L, 2500L, 3000L, 7500L, 3500L);

        mockMvc.perform(get("/api/observability/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRadars24h").value(5))
                .andExpect(jsonPath("$.totalTokens24h").value(12500))
                .andExpect(jsonPath("$.p50Ms24h").value(3000))
                .andExpect(jsonPath("$.cacheHitRate24h").exists());
    }

    @Test
    void timeseriesShouldReturnDailyMetrics() throws Exception {
        seedRollup(LocalDate.now().minusDays(2), 3, 5000L, 1000L, 2000L, 4000L, 2500L);
        seedRollup(LocalDate.now().minusDays(1), 7, 15000L, 3000L, 3500L, 8000L, 4000L);

        mockMvc.perform(get("/api/observability/timeseries").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void summaryShouldNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/observability/summary"))
                .andExpect(status().isOk());
    }

    private void seedRollup(LocalDate date, int radars, long tokensIn, long tokensOut,
                             long p50, long p95, long avg) {
        var rollup = new MetricsDailyRollup();
        rollup.setDate(date);
        rollup.setTotalRadars(radars);
        rollup.setTotalTokensInput(tokensIn);
        rollup.setTotalTokensOutput(tokensOut);
        rollup.setSonnetCalls(radars * 4);
        rollup.setHaikuCalls(radars * 8);
        rollup.setCacheHits(radars * 2);
        rollup.setCacheMisses(radars);
        rollup.setP50Ms(p50);
        rollup.setP95Ms(p95);
        rollup.setAvgGenerationMs(avg);
        rollup.setItemsIngested(radars * 10);
        rollup.setItemsDeduped(radars * 2);
        rollup.setEvalScoreRelevance(new BigDecimal("0.850"));
        rollup.setEvalScoreCitations(new BigDecimal("0.920"));
        rollup.setEvalScoreDistinctness(new BigDecimal("0.780"));
        rollupRepository.save(rollup);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=ObservabilityResourceIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (classes not found)

- [ ] **Step 3: Create ObservabilitySummaryDTO**

Create `backend/src/main/java/com/devradar/web/rest/dto/ObservabilitySummaryDTO.java`:

```java
package com.devradar.web.rest.dto;

import java.math.BigDecimal;

public record ObservabilitySummaryDTO(
        int totalRadars24h,
        long totalTokens24h,
        long totalTokensInput24h,
        long totalTokensOutput24h,
        int sonnetCalls24h,
        int haikuCalls24h,
        long p50Ms24h,
        long p95Ms24h,
        long avgGenerationMs24h,
        double cacheHitRate24h,
        int itemsIngested24h,
        BigDecimal evalScoreRelevance,
        BigDecimal evalScoreCitations,
        BigDecimal evalScoreDistinctness
) {}
```

- [ ] **Step 4: Create MetricsDayDTO**

Create `backend/src/main/java/com/devradar/web/rest/dto/MetricsDayDTO.java`:

```java
package com.devradar.web.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MetricsDayDTO(
        LocalDate date,
        int totalRadars,
        long totalTokensInput,
        long totalTokensOutput,
        int sonnetCalls,
        int haikuCalls,
        int cacheHits,
        int cacheMisses,
        long p50Ms,
        long p95Ms,
        long avgGenerationMs,
        int itemsIngested,
        int itemsDeduped,
        BigDecimal evalScoreRelevance,
        BigDecimal evalScoreCitations,
        BigDecimal evalScoreDistinctness
) {}
```

- [ ] **Step 5: Create ObservabilityService**

Create `backend/src/main/java/com/devradar/observability/ObservabilityService.java`:

```java
package com.devradar.observability;

import com.devradar.domain.MetricsDailyRollup;
import com.devradar.repository.MetricsDailyRollupRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ObservabilityService {

    private final MetricsDailyRollupRepository repository;

    public ObservabilityService(MetricsDailyRollupRepository repository) {
        this.repository = repository;
    }

    public Optional<MetricsDailyRollup> getForDate(LocalDate date) {
        return repository.findById(date);
    }

    public List<MetricsDailyRollup> getTimeseries(int days) {
        var to = LocalDate.now();
        var from = to.minusDays(days);
        return repository.findByDateBetweenOrderByDateDesc(from, to);
    }
}
```

- [ ] **Step 6: Create ObservabilityApplicationService**

Create `backend/src/main/java/com/devradar/observability/application/ObservabilityApplicationService.java`:

```java
package com.devradar.observability.application;

import com.devradar.domain.MetricsDailyRollup;
import com.devradar.observability.ObservabilityService;
import com.devradar.web.rest.dto.MetricsDayDTO;
import com.devradar.web.rest.dto.ObservabilitySummaryDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ObservabilityApplicationService {

    private final ObservabilityService observabilityService;

    public ObservabilityApplicationService(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    public ObservabilitySummaryDTO getSummary() {
        var yesterday = LocalDate.now().minusDays(1);
        var rollup = observabilityService.getForDate(yesterday).orElse(emptyRollup(yesterday));
        return toSummary(rollup);
    }

    public List<MetricsDayDTO> getTimeseries(int days) {
        return observabilityService.getTimeseries(days).stream()
                .map(this::toDay)
                .toList();
    }

    private ObservabilitySummaryDTO toSummary(MetricsDailyRollup r) {
        int totalCacheOps = r.getCacheHits() + r.getCacheMisses();
        double hitRate = totalCacheOps > 0 ? (double) r.getCacheHits() / totalCacheOps : 0.0;

        return new ObservabilitySummaryDTO(
                r.getTotalRadars(),
                r.getTotalTokensInput() + r.getTotalTokensOutput(),
                r.getTotalTokensInput(),
                r.getTotalTokensOutput(),
                r.getSonnetCalls(),
                r.getHaikuCalls(),
                r.getP50Ms(),
                r.getP95Ms(),
                r.getAvgGenerationMs(),
                Math.round(hitRate * 1000.0) / 1000.0,
                r.getItemsIngested(),
                r.getEvalScoreRelevance(),
                r.getEvalScoreCitations(),
                r.getEvalScoreDistinctness()
        );
    }

    private MetricsDayDTO toDay(MetricsDailyRollup r) {
        return new MetricsDayDTO(
                r.getDate(),
                r.getTotalRadars(),
                r.getTotalTokensInput(),
                r.getTotalTokensOutput(),
                r.getSonnetCalls(),
                r.getHaikuCalls(),
                r.getCacheHits(),
                r.getCacheMisses(),
                r.getP50Ms(),
                r.getP95Ms(),
                r.getAvgGenerationMs(),
                r.getItemsIngested(),
                r.getItemsDeduped(),
                r.getEvalScoreRelevance(),
                r.getEvalScoreCitations(),
                r.getEvalScoreDistinctness()
        );
    }

    private MetricsDailyRollup emptyRollup(LocalDate date) {
        var r = new MetricsDailyRollup();
        r.setDate(date);
        r.setTotalRadars(0);
        r.setTotalTokensInput(0L);
        r.setTotalTokensOutput(0L);
        return r;
    }
}
```

- [ ] **Step 7: Create ObservabilityResource**

Create `backend/src/main/java/com/devradar/web/rest/ObservabilityResource.java`:

```java
package com.devradar.web.rest;

import com.devradar.observability.application.ObservabilityApplicationService;
import com.devradar.web.rest.dto.MetricsDayDTO;
import com.devradar.web.rest.dto.ObservabilitySummaryDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityResource {

    private final ObservabilityApplicationService service;

    public ObservabilityResource(ObservabilityApplicationService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ResponseEntity<ObservabilitySummaryDTO> summary() {
        return ResponseEntity.ok(service.getSummary());
    }

    @GetMapping("/timeseries")
    public ResponseEntity<List<MetricsDayDTO>> timeseries(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(service.getTimeseries(Math.min(days, 90)));
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=ObservabilityResourceIT`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/devradar/observability/ObservabilityService.java backend/src/main/java/com/devradar/observability/application/ObservabilityApplicationService.java backend/src/main/java/com/devradar/web/rest/ObservabilityResource.java backend/src/main/java/com/devradar/web/rest/dto/ObservabilitySummaryDTO.java backend/src/main/java/com/devradar/web/rest/dto/MetricsDayDTO.java backend/src/test/java/com/devradar/web/rest/ObservabilityResourceIT.java
git commit -m "feat(observability): add public ObservabilityResource with summary and timeseries endpoints"
```

---

## Task 10: Golden Dataset Fixtures + GoldenDatasetLoader (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/eval/GoldenRadarCase.java`
- Create: `backend/src/main/java/com/devradar/eval/GoldenDatasetLoader.java`
- Create: `backend/src/main/resources/evals/golden_radars/case_01_java_developer.json`
- Create: `backend/src/main/resources/evals/golden_radars/case_02_frontend_developer.json`
- Create: `backend/src/test/java/com/devradar/eval/GoldenDatasetLoaderTest.java`

- [ ] **Step 1: Create golden dataset fixture — case 1**

Create `backend/src/main/resources/evals/golden_radars/case_01_java_developer.json`:

```json
{
  "name": "Java backend developer — Spring Boot focus",
  "userInterests": ["java", "spring_boot", "docker", "postgresql", "security"],
  "sourceItems": [
    {
      "id": 1001,
      "title": "Spring Boot 3.5 Released with Virtual Threads Support",
      "url": "https://spring.io/blog/2026/04/spring-boot-3-5",
      "tags": ["java", "spring_boot"],
      "postedAt": "2026-04-18T10:00:00Z"
    },
    {
      "id": 1002,
      "title": "Critical CVE in Jackson Databind 2.16.x",
      "url": "https://github.com/advisories/GHSA-xxxx-jackson",
      "tags": ["java", "security"],
      "postedAt": "2026-04-17T08:00:00Z"
    },
    {
      "id": 1003,
      "title": "Docker Desktop 5.0 Brings Native Kubernetes Integration",
      "url": "https://docker.com/blog/desktop-5",
      "tags": ["docker"],
      "postedAt": "2026-04-16T14:00:00Z"
    },
    {
      "id": 1004,
      "title": "React 20 Announced with Server Components GA",
      "url": "https://react.dev/blog/2026/react-20",
      "tags": ["react", "javascript"],
      "postedAt": "2026-04-19T12:00:00Z"
    }
  ],
  "expectedThemes": [
    {
      "title": "Spring Boot ecosystem updates",
      "expectedItemIds": [1001],
      "shouldNotInclude": [1004]
    },
    {
      "title": "Security advisories affecting Java",
      "expectedItemIds": [1002]
    }
  ],
  "tokenBudget": 5000
}
```

- [ ] **Step 2: Create golden dataset fixture — case 2**

Create `backend/src/main/resources/evals/golden_radars/case_02_frontend_developer.json`:

```json
{
  "name": "Frontend developer — React focus",
  "userInterests": ["react", "typescript", "css", "nextjs"],
  "sourceItems": [
    {
      "id": 2001,
      "title": "React 20 Announced with Server Components GA",
      "url": "https://react.dev/blog/2026/react-20",
      "tags": ["react", "javascript"],
      "postedAt": "2026-04-19T12:00:00Z"
    },
    {
      "id": 2002,
      "title": "TypeScript 6.0 Release Candidate",
      "url": "https://devblogs.microsoft.com/typescript/announcing-typescript-6-0-rc/",
      "tags": ["typescript"],
      "postedAt": "2026-04-18T09:00:00Z"
    },
    {
      "id": 2003,
      "title": "Spring Boot 3.5 Released with Virtual Threads Support",
      "url": "https://spring.io/blog/2026/04/spring-boot-3-5",
      "tags": ["java", "spring_boot"],
      "postedAt": "2026-04-18T10:00:00Z"
    }
  ],
  "expectedThemes": [
    {
      "title": "Frontend framework updates",
      "expectedItemIds": [2001, 2002],
      "shouldNotInclude": [2003]
    }
  ],
  "tokenBudget": 5000
}
```

- [ ] **Step 3: Write the failing test**

Create `backend/src/test/java/com/devradar/eval/GoldenDatasetLoaderTest.java`:

```java
package com.devradar.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenDatasetLoaderTest {

    private final GoldenDatasetLoader loader = new GoldenDatasetLoader();

    @Test
    void shouldLoadAllGoldenCases() {
        List<GoldenRadarCase> cases = loader.loadAll();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(2);
        assertThat(cases.getFirst().name()).contains("Java");
        assertThat(cases.getFirst().userInterests()).contains("java", "spring_boot");
        assertThat(cases.getFirst().sourceItems()).isNotEmpty();
        assertThat(cases.getFirst().expectedThemes()).isNotEmpty();
    }

    @Test
    void shouldParseSourceItemsCorrectly() {
        var cases = loader.loadAll();
        var firstItem = cases.getFirst().sourceItems().getFirst();

        assertThat(firstItem.id()).isEqualTo(1001);
        assertThat(firstItem.title()).isNotBlank();
        assertThat(firstItem.url()).startsWith("https://");
        assertThat(firstItem.tags()).isNotEmpty();
    }

    @Test
    void shouldParseExpectedThemesCorrectly() {
        var cases = loader.loadAll();
        var firstTheme = cases.getFirst().expectedThemes().getFirst();

        assertThat(firstTheme.title()).isNotBlank();
        assertThat(firstTheme.expectedItemIds()).isNotEmpty();
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=GoldenDatasetLoaderTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (classes not found)

- [ ] **Step 5: Create GoldenRadarCase record**

Create `backend/src/main/java/com/devradar/eval/GoldenRadarCase.java`:

```java
package com.devradar.eval;

import java.util.List;

public record GoldenRadarCase(
        String name,
        List<String> userInterests,
        List<SourceItemFixture> sourceItems,
        List<ExpectedTheme> expectedThemes,
        int tokenBudget
) {
    public record SourceItemFixture(
            long id,
            String title,
            String url,
            List<String> tags,
            String postedAt
    ) {}

    public record ExpectedTheme(
            String title,
            List<Long> expectedItemIds,
            List<Long> shouldNotInclude
    ) {}
}
```

- [ ] **Step 6: Create GoldenDatasetLoader**

Create `backend/src/main/java/com/devradar/eval/GoldenDatasetLoader.java`:

```java
package com.devradar.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Component
public class GoldenDatasetLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<GoldenRadarCase> loadAll() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            var resources = resolver.getResources("classpath:evals/golden_radars/*.json");
            Arrays.sort(resources, Comparator.comparing(r -> r.getFilename() != null ? r.getFilename() : ""));

            List<GoldenRadarCase> cases = new ArrayList<>();
            for (var resource : resources) {
                cases.add(objectMapper.readValue(resource.getInputStream(), GoldenRadarCase.class));
            }
            return cases;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load golden datasets", e);
        }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=GoldenDatasetLoaderTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/devradar/eval/GoldenRadarCase.java backend/src/main/java/com/devradar/eval/GoldenDatasetLoader.java backend/src/main/resources/evals/ backend/src/test/java/com/devradar/eval/GoldenDatasetLoaderTest.java
git commit -m "feat(eval): add golden dataset fixtures and GoldenDatasetLoader"
```

---

## Task 11: CitationChecker + CostDisciplineChecker (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/eval/CitationChecker.java`
- Create: `backend/src/main/java/com/devradar/eval/CostDisciplineChecker.java`
- Create: `backend/src/test/java/com/devradar/eval/CitationCheckerTest.java`
- Create: `backend/src/test/java/com/devradar/eval/CostDisciplineCheckerTest.java`

- [ ] **Step 1: Write the failing CitationChecker test**

Create `backend/src/test/java/com/devradar/eval/CitationCheckerTest.java`:

```java
package com.devradar.eval;

import com.devradar.domain.RadarTheme;
import com.devradar.domain.RadarThemeItem;
import com.devradar.domain.SourceItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationCheckerTest {

    private final CitationChecker checker = new CitationChecker();

    @Test
    void shouldScorePerfectWhenAllClaimsAreCited() {
        var theme = buildTheme(
                "Spring Boot 3.5 brings virtual thread support, improving throughput.",
                List.of(buildItem("Spring Boot 3.5 Released with Virtual Threads Support",
                        "https://spring.io/blog/spring-boot-3-5"))
        );

        BigDecimal score = checker.score(List.of(theme));
        assertThat(score).isGreaterThanOrEqualTo(new BigDecimal("0.800"));
    }

    @Test
    void shouldScoreLowWhenNoCitations() {
        var theme = buildTheme(
                "Spring Boot 3.5 brings virtual thread support.",
                List.of()
        );

        BigDecimal score = checker.score(List.of(theme));
        assertThat(score).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void shouldScoreAcrossMultipleThemes() {
        var theme1 = buildTheme("Good summary.", List.of(buildItem("Title A", "https://a.com")));
        var theme2 = buildTheme("No sources.", List.of());

        BigDecimal score = checker.score(List.of(theme1, theme2));
        assertThat(score).isBetween(new BigDecimal("0.400"), new BigDecimal("0.600"));
    }

    private CitationChecker.ThemeWithItems buildTheme(String summary, List<CitationChecker.CitedItem> items) {
        return new CitationChecker.ThemeWithItems(summary, items);
    }

    private CitationChecker.CitedItem buildItem(String title, String url) {
        return new CitationChecker.CitedItem(title, url);
    }
}
```

- [ ] **Step 2: Write the failing CostDisciplineChecker test**

Create `backend/src/test/java/com/devradar/eval/CostDisciplineCheckerTest.java`:

```java
package com.devradar.eval;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CostDisciplineCheckerTest {

    private final CostDisciplineChecker checker = new CostDisciplineChecker(5000);

    @Test
    void shouldScorePerfectWhenUnderBudget() {
        BigDecimal score = checker.score(2000);
        assertThat(score).isEqualTo(new BigDecimal("1.000"));
    }

    @Test
    void shouldScoreZeroWhenDoubleBudget() {
        BigDecimal score = checker.score(10000);
        assertThat(score).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void shouldScorePartialWhenSlightlyOver() {
        BigDecimal score = checker.score(6000);
        assertThat(score).isBetween(new BigDecimal("0.500"), new BigDecimal("0.900"));
    }

    @Test
    void shouldScoreAcrossMultipleRadars() {
        BigDecimal score = checker.scoreMultiple(new int[]{2000, 3000, 8000});
        assertThat(score).isBetween(new BigDecimal("0.500"), new BigDecimal("0.900"));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend && mvn test -pl . -Dtest="CitationCheckerTest,CostDisciplineCheckerTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (classes not found)

- [ ] **Step 4: Implement CitationChecker**

Create `backend/src/main/java/com/devradar/eval/CitationChecker.java`:

```java
package com.devradar.eval;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class CitationChecker {

    public record CitedItem(String title, String url) {}
    public record ThemeWithItems(String summary, List<CitedItem> items) {}

    public BigDecimal score(List<ThemeWithItems> themes) {
        if (themes.isEmpty()) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;
        for (var theme : themes) {
            total = total.add(scoreTheme(theme));
        }
        return total.divide(BigDecimal.valueOf(themes.size()), 3, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreTheme(ThemeWithItems theme) {
        if (theme.items().isEmpty()) return BigDecimal.ZERO;

        int matched = 0;
        String summaryLower = theme.summary().toLowerCase();
        for (var item : theme.items()) {
            String[] titleWords = item.title().toLowerCase().split("\\s+");
            int significantWords = 0;
            int matchedWords = 0;
            for (String word : titleWords) {
                if (word.length() > 3) {
                    significantWords++;
                    if (summaryLower.contains(word)) {
                        matchedWords++;
                    }
                }
            }
            if (significantWords > 0 && (double) matchedWords / significantWords >= 0.3) {
                matched++;
            }
        }

        return BigDecimal.valueOf(matched)
                .divide(BigDecimal.valueOf(theme.items().size()), 3, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 5: Implement CostDisciplineChecker**

Create `backend/src/main/java/com/devradar/eval/CostDisciplineChecker.java`:

```java
package com.devradar.eval;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

@Component
public class CostDisciplineChecker {

    private final int tokenBudget;

    public CostDisciplineChecker(@Value("${devradar.eval.token-budget:5000}") int tokenBudget) {
        this.tokenBudget = tokenBudget;
    }

    public BigDecimal score(int actualTokens) {
        if (actualTokens <= tokenBudget) return new BigDecimal("1.000");
        if (actualTokens >= tokenBudget * 2) return BigDecimal.ZERO;

        double ratio = 1.0 - ((double) (actualTokens - tokenBudget) / tokenBudget);
        return BigDecimal.valueOf(ratio).setScale(3, RoundingMode.HALF_UP);
    }

    public BigDecimal scoreMultiple(int[] tokenCounts) {
        if (tokenCounts.length == 0) return BigDecimal.ZERO;
        BigDecimal sum = Arrays.stream(tokenCounts)
                .mapToObj(this::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(tokenCounts.length), 3, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && mvn test -pl . -Dtest="CitationCheckerTest,CostDisciplineCheckerTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/devradar/eval/CitationChecker.java backend/src/main/java/com/devradar/eval/CostDisciplineChecker.java backend/src/test/java/com/devradar/eval/CitationCheckerTest.java backend/src/test/java/com/devradar/eval/CostDisciplineCheckerTest.java
git commit -m "feat(eval): add CitationChecker and CostDisciplineChecker with programmatic scoring"
```

---

## Task 12: LlmJudge — Sonnet-as-Judge for Relevance + Distinctness (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/eval/LlmJudge.java`
- Create: `backend/src/test/java/com/devradar/eval/LlmJudgeTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/devradar/eval/LlmJudgeTest.java`:

```java
package com.devradar.eval;

import com.devradar.ai.AiClient;
import com.devradar.ai.AiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmJudgeTest {

    @Mock
    private AiClient aiClient;

    @InjectMocks
    private LlmJudge judge;

    @Test
    void shouldParseRelevanceScoreFromLlmResponse() {
        when(aiClient.generate(anyString(), anyString(), anyList(), anyList(), anyInt()))
                .thenReturn(new AiResponse(
                        "{\"score\": 0.85, \"reasoning\": \"Themes closely match user interests\"}",
                        List.of(), "end_turn", 200, 50));

        BigDecimal score = judge.scoreRelevance(
                List.of("java", "spring_boot"),
                List.of("Spring Boot 3.5 updates", "Java security advisories")
        );

        assertThat(score).isEqualTo(new BigDecimal("0.850"));
    }

    @Test
    void shouldParseDistinctnessScoreFromLlmResponse() {
        when(aiClient.generate(anyString(), anyString(), anyList(), anyList(), anyInt()))
                .thenReturn(new AiResponse(
                        "{\"score\": 0.90, \"reasoning\": \"All themes cover different topics\"}",
                        List.of(), "end_turn", 200, 50));

        BigDecimal score = judge.scoreDistinctness(
                List.of("Spring Boot releases", "Security vulnerabilities", "Docker tooling")
        );

        assertThat(score).isEqualTo(new BigDecimal("0.900"));
    }

    @Test
    void shouldReturnZeroOnMalformedResponse() {
        when(aiClient.generate(anyString(), anyString(), anyList(), anyList(), anyInt()))
                .thenReturn(new AiResponse("not valid json", List.of(), "end_turn", 100, 20));

        BigDecimal score = judge.scoreRelevance(List.of("java"), List.of("Theme A"));
        assertThat(score).isEqualTo(BigDecimal.ZERO);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=LlmJudgeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (LlmJudge not found)

- [ ] **Step 3: Implement LlmJudge**

Create `backend/src/main/java/com/devradar/eval/LlmJudge.java`:

```java
package com.devradar.eval;

import com.devradar.ai.AiClient;
import com.devradar.ai.AiMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class LlmJudge {

    private static final Logger log = LoggerFactory.getLogger(LlmJudge.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiClient aiClient;
    private final String judgeModel;

    public LlmJudge(AiClient aiClient,
                     @Value("${anthropic.orchestrator-model:claude-sonnet-4-6}") String judgeModel) {
        this.aiClient = aiClient;
        this.judgeModel = judgeModel;
    }

    public BigDecimal scoreRelevance(List<String> userInterests, List<String> themeTitles) {
        String prompt = String.format("""
                You are an eval judge. Rate how relevant these radar themes are to the user's declared interests.
                
                User interests: %s
                Theme titles: %s
                
                Respond with JSON only: {"score": <0.0 to 1.0>, "reasoning": "<brief explanation>"}
                Score 1.0 means all themes directly match user interests. Score 0.0 means none are relevant.""",
                userInterests, themeTitles);

        return callJudge(prompt);
    }

    public BigDecimal scoreDistinctness(List<String> themeTitles) {
        String prompt = String.format("""
                You are an eval judge. Rate how distinct these radar themes are from each other.
                
                Theme titles: %s
                
                Respond with JSON only: {"score": <0.0 to 1.0>, "reasoning": "<brief explanation>"}
                Score 1.0 means all themes cover completely different topics. Score 0.0 means they are all duplicates.""",
                themeTitles);

        return callJudge(prompt);
    }

    private BigDecimal callJudge(String prompt) {
        try {
            var response = aiClient.generate(
                    judgeModel,
                    "You are a strict evaluation judge. Always respond with valid JSON.",
                    List.of(AiMessage.user(prompt)),
                    List.of(),
                    256
            );

            String text = response.text().trim();
            if (text.startsWith("```")) {
                text = text.replaceAll("```json?\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            JsonNode node = objectMapper.readTree(text);
            double score = node.get("score").asDouble();
            return BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("LLM judge failed to produce a valid score: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=LlmJudgeTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/eval/LlmJudge.java backend/src/test/java/com/devradar/eval/LlmJudgeTest.java
git commit -m "feat(eval): add LlmJudge for Sonnet-as-judge relevance and distinctness scoring"
```

---

## Task 13: EvalService — Full Eval Run Orchestration (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/eval/EvalService.java`
- Create: `backend/src/test/java/com/devradar/eval/EvalServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/devradar/eval/EvalServiceTest.java`:

```java
package com.devradar.eval;

import com.devradar.domain.*;
import com.devradar.repository.EvalRunRepository;
import com.devradar.repository.EvalScoreRepository;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.repository.RadarThemeItemRepository;
import com.devradar.repository.SourceItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalServiceTest {

    @Mock private RadarRepository radarRepository;
    @Mock private RadarThemeRepository themeRepository;
    @Mock private RadarThemeItemRepository themeItemRepository;
    @Mock private SourceItemRepository sourceItemRepository;
    @Mock private EvalRunRepository evalRunRepository;
    @Mock private EvalScoreRepository evalScoreRepository;
    @Mock private CitationChecker citationChecker;
    @Mock private CostDisciplineChecker costDisciplineChecker;
    @Mock private LlmJudge llmJudge;

    @InjectMocks
    private EvalService evalService;

    @Test
    void shouldCreateEvalRunAndPersistScores() {
        var radar = buildRadar(1L, 3000);
        var theme = buildTheme(1L, "Spring Boot updates", "Spring Boot 3.5 released with virtual threads.");

        when(radarRepository.findByStatusOrderByGeneratedAtDesc(eq(RadarStatus.READY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(radar)));
        when(themeRepository.findByRadarId(1L)).thenReturn(List.of(theme));
        when(themeItemRepository.findByThemeId(anyLong())).thenReturn(List.of());
        when(evalRunRepository.save(any(EvalRun.class))).thenAnswer(inv -> {
            var run = inv.getArgument(0, EvalRun.class);
            run.setId(100L);
            return run;
        });
        when(evalScoreRepository.save(any(EvalScore.class))).thenAnswer(inv -> inv.getArgument(0));

        when(citationChecker.score(anyList())).thenReturn(new BigDecimal("0.900"));
        when(costDisciplineChecker.scoreMultiple(any(int[].class))).thenReturn(new BigDecimal("1.000"));
        when(llmJudge.scoreRelevance(anyList(), anyList())).thenReturn(new BigDecimal("0.850"));
        when(llmJudge.scoreDistinctness(anyList())).thenReturn(new BigDecimal("0.800"));

        EvalRun result = evalService.runEval(10);

        assertThat(result.getStatus()).isEqualTo(EvalRunStatus.COMPLETED);
        verify(evalScoreRepository, times(4)).save(any(EvalScore.class));
    }

    @Test
    void shouldHandleEmptyRadarsGracefully() {
        when(radarRepository.findByStatusOrderByGeneratedAtDesc(eq(RadarStatus.READY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(evalRunRepository.save(any(EvalRun.class))).thenAnswer(inv -> {
            var run = inv.getArgument(0, EvalRun.class);
            run.setId(101L);
            return run;
        });

        EvalRun result = evalService.runEval(10);

        assertThat(result.getStatus()).isEqualTo(EvalRunStatus.COMPLETED);
        assertThat(result.getRadarCount()).isZero();
    }

    private Radar buildRadar(Long id, int tokenCount) {
        var radar = new Radar();
        radar.setId(id);
        radar.setUserId(1L);
        radar.setStatus(RadarStatus.READY);
        radar.setTokenCount(tokenCount);
        radar.setGenerationMs(4000L);
        radar.setGeneratedAt(Instant.now());
        radar.setPeriodStart(Instant.now().minusSeconds(604800));
        radar.setPeriodEnd(Instant.now());
        return radar;
    }

    private RadarTheme buildTheme(Long radarId, String title, String summary) {
        var theme = new RadarTheme();
        theme.setId(1L);
        theme.setRadarId(radarId);
        theme.setTitle(title);
        theme.setSummary(summary);
        theme.setDisplayOrder(1);
        return theme;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=EvalServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (EvalService not found)

- [ ] **Step 3: Implement EvalService**

Create `backend/src/main/java/com/devradar/eval/EvalService.java`:

```java
package com.devradar.eval;

import com.devradar.domain.*;
import com.devradar.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    private final RadarRepository radarRepository;
    private final RadarThemeRepository themeRepository;
    private final RadarThemeItemRepository themeItemRepository;
    private final SourceItemRepository sourceItemRepository;
    private final EvalRunRepository evalRunRepository;
    private final EvalScoreRepository evalScoreRepository;
    private final CitationChecker citationChecker;
    private final CostDisciplineChecker costDisciplineChecker;
    private final LlmJudge llmJudge;

    public EvalService(RadarRepository radarRepository,
                       RadarThemeRepository themeRepository,
                       RadarThemeItemRepository themeItemRepository,
                       SourceItemRepository sourceItemRepository,
                       EvalRunRepository evalRunRepository,
                       EvalScoreRepository evalScoreRepository,
                       CitationChecker citationChecker,
                       CostDisciplineChecker costDisciplineChecker,
                       LlmJudge llmJudge) {
        this.radarRepository = radarRepository;
        this.themeRepository = themeRepository;
        this.themeItemRepository = themeItemRepository;
        this.sourceItemRepository = sourceItemRepository;
        this.evalRunRepository = evalRunRepository;
        this.evalScoreRepository = evalScoreRepository;
        this.citationChecker = citationChecker;
        this.costDisciplineChecker = costDisciplineChecker;
        this.llmJudge = llmJudge;
    }

    @Transactional
    public EvalRun runEval(int radarCount) {
        var run = new EvalRun();
        run.setStatus(EvalRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setRadarCount(0);
        run = evalRunRepository.save(run);

        try {
            var radars = radarRepository.findByStatusOrderByGeneratedAtDesc(
                    RadarStatus.READY, PageRequest.of(0, radarCount)).getContent();
            run.setRadarCount(radars.size());

            if (radars.isEmpty()) {
                run.setStatus(EvalRunStatus.COMPLETED);
                run.setCompletedAt(Instant.now());
                return evalRunRepository.save(run);
            }

            List<CitationChecker.ThemeWithItems> allThemesWithItems = new ArrayList<>();
            List<String> allThemeTitles = new ArrayList<>();
            List<String> allUserInterests = new ArrayList<>();
            int[] tokenCounts = radars.stream().mapToInt(Radar::getTokenCount).toArray();

            for (var radar : radars) {
                var themes = themeRepository.findByRadarId(radar.getId());
                for (var theme : themes) {
                    allThemeTitles.add(theme.getTitle());
                    var themeItems = themeItemRepository.findByThemeId(theme.getId());
                    var citedItems = themeItems.stream()
                            .map(ti -> {
                                var si = sourceItemRepository.findById(ti.getSourceItemId()).orElse(null);
                                if (si == null) return new CitationChecker.CitedItem("", "");
                                return new CitationChecker.CitedItem(si.getTitle(), si.getUrl());
                            })
                            .toList();
                    allThemesWithItems.add(new CitationChecker.ThemeWithItems(theme.getSummary(), citedItems));
                }
            }

            BigDecimal citationScore = citationChecker.score(allThemesWithItems);
            persistScore(run.getId(), EvalScoreCategory.CITATIONS, citationScore);

            BigDecimal costScore = costDisciplineChecker.scoreMultiple(tokenCounts);
            persistScore(run.getId(), EvalScoreCategory.COST_DISCIPLINE, costScore);

            BigDecimal relevanceScore = llmJudge.scoreRelevance(allUserInterests, allThemeTitles);
            persistScore(run.getId(), EvalScoreCategory.RELEVANCE, relevanceScore);

            BigDecimal distinctnessScore = llmJudge.scoreDistinctness(allThemeTitles);
            persistScore(run.getId(), EvalScoreCategory.DISTINCTNESS, distinctnessScore);

            run.setStatus(EvalRunStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            log.info("eval run {} completed: citations={} cost={} relevance={} distinctness={}",
                    run.getId(), citationScore, costScore, relevanceScore, distinctnessScore);

        } catch (Exception e) {
            log.error("eval run {} failed: {}", run.getId(), e.getMessage(), e);
            run.setStatus(EvalRunStatus.FAILED);
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(Instant.now());
        }

        return evalRunRepository.save(run);
    }

    private void persistScore(Long runId, EvalScoreCategory category, BigDecimal score) {
        var evalScore = new EvalScore();
        evalScore.setEvalRunId(runId);
        evalScore.setCategory(category);
        evalScore.setScore(score);
        evalScoreRepository.save(evalScore);
    }
}
```

- [ ] **Step 4: Add missing repository query methods**

If not already present, add to `RadarRepository.java`:

```java
    Page<Radar> findByStatusOrderByGeneratedAtDesc(RadarStatus status, Pageable pageable);
```

Add to `RadarThemeRepository.java`:

```java
    List<RadarTheme> findByRadarId(Long radarId);
```

Add to `RadarThemeItemRepository.java`:

```java
    List<RadarThemeItem> findByThemeId(Long themeId);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=EvalServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/eval/EvalService.java backend/src/test/java/com/devradar/eval/EvalServiceTest.java backend/src/main/java/com/devradar/repository/
git commit -m "feat(eval): add EvalService orchestrating full eval runs with all four scoring categories"
```

---

## Task 14: EvalApplicationService + EvalResource + IT (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/dto/EvalRunDTO.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/EvalScoreDTO.java`
- Create: `backend/src/main/java/com/devradar/eval/application/EvalApplicationService.java`
- Create: `backend/src/main/java/com/devradar/web/rest/EvalResource.java`
- Create: `backend/src/test/java/com/devradar/web/rest/EvalResourceIT.java`

- [ ] **Step 1: Write the failing IT**

Create `backend/src/test/java/com/devradar/web/rest/EvalResourceIT.java`:

```java
package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.*;
import com.devradar.repository.EvalRunRepository;
import com.devradar.repository.EvalScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class EvalResourceIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EvalRunRepository evalRunRepository;

    @Autowired
    private EvalScoreRepository evalScoreRepository;

    @Test
    void getRunsShouldReturnEvalHistory() throws Exception {
        var run = new EvalRun();
        run.setStatus(EvalRunStatus.COMPLETED);
        run.setRadarCount(5);
        run.setStartedAt(Instant.now().minusSeconds(120));
        run.setCompletedAt(Instant.now());
        run = evalRunRepository.save(run);

        var score = new EvalScore();
        score.setEvalRunId(run.getId());
        score.setCategory(EvalScoreCategory.RELEVANCE);
        score.setScore(new BigDecimal("0.850"));
        evalScoreRepository.save(score);

        mockMvc.perform(get("/api/evals/runs")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].radarCount").value(5))
                .andExpect(jsonPath("$[0].scores[0].category").value("RELEVANCE"))
                .andExpect(jsonPath("$[0].scores[0].score").value(0.85));
    }

    @Test
    void postRunShouldTriggerEvalAndReturn201() throws Exception {
        mockMvc.perform(post("/api/evals/run")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"radarCount\": 5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void evalEndpointsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/evals/runs"))
                .andExpect(status().isUnauthorized());
    }

    private String getAdminToken() {
        // Use existing test auth helper — register a test user and get their JWT token.
        // This follows the pattern used in other IT classes (e.g., RadarResourceIT).
        // The eval endpoints require authentication but not a specific admin role for MVP.
        return createTestUserAndGetToken();
    }

    private String createTestUserAndGetToken() {
        // Reuse the pattern from AbstractIntegrationTest or existing ITs
        // POST /api/auth/register → POST /api/auth/login → extract token
        try {
            var registerResult = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"eval-test@test.com\",\"password\":\"Password1!\",\"displayName\":\"Eval Tester\"}"))
                    .andReturn();

            var loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"eval-test@test.com\",\"password\":\"Password1!\"}"))
                    .andReturn();

            var body = loginResult.getResponse().getContentAsString();
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(body).get("token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test user", e);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=EvalResourceIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (classes not found)

- [ ] **Step 3: Create EvalScoreDTO**

Create `backend/src/main/java/com/devradar/web/rest/dto/EvalScoreDTO.java`:

```java
package com.devradar.web.rest.dto;

import java.math.BigDecimal;

public record EvalScoreDTO(
        String category,
        BigDecimal score
) {}
```

- [ ] **Step 4: Create EvalRunDTO**

Create `backend/src/main/java/com/devradar/web/rest/dto/EvalRunDTO.java`:

```java
package com.devradar.web.rest.dto;

import java.time.Instant;
import java.util.List;

public record EvalRunDTO(
        Long id,
        String status,
        int radarCount,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        List<EvalScoreDTO> scores
) {}
```

- [ ] **Step 5: Create EvalApplicationService**

Create `backend/src/main/java/com/devradar/eval/application/EvalApplicationService.java`:

```java
package com.devradar.eval.application;

import com.devradar.domain.EvalRun;
import com.devradar.eval.EvalService;
import com.devradar.repository.EvalRunRepository;
import com.devradar.repository.EvalScoreRepository;
import com.devradar.web.rest.dto.EvalRunDTO;
import com.devradar.web.rest.dto.EvalScoreDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EvalApplicationService {

    private final EvalService evalService;
    private final EvalRunRepository evalRunRepository;
    private final EvalScoreRepository evalScoreRepository;

    public EvalApplicationService(EvalService evalService,
                                   EvalRunRepository evalRunRepository,
                                   EvalScoreRepository evalScoreRepository) {
        this.evalService = evalService;
        this.evalRunRepository = evalRunRepository;
        this.evalScoreRepository = evalScoreRepository;
    }

    public EvalRunDTO triggerRun(int radarCount) {
        EvalRun run = evalService.runEval(radarCount);
        return toDto(run);
    }

    public List<EvalRunDTO> listRuns() {
        return evalRunRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    private EvalRunDTO toDto(EvalRun run) {
        var scores = evalScoreRepository.findByEvalRunId(run.getId()).stream()
                .map(s -> new EvalScoreDTO(s.getCategory().name(), s.getScore()))
                .toList();

        return new EvalRunDTO(
                run.getId(),
                run.getStatus().name(),
                run.getRadarCount(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getErrorMessage(),
                scores
        );
    }
}
```

- [ ] **Step 6: Create EvalResource**

Create `backend/src/main/java/com/devradar/web/rest/EvalResource.java`:

```java
package com.devradar.web.rest;

import com.devradar.eval.application.EvalApplicationService;
import com.devradar.web.rest.dto.EvalRunDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evals")
public class EvalResource {

    private final EvalApplicationService service;

    public EvalResource(EvalApplicationService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public ResponseEntity<EvalRunDTO> run(@RequestBody Map<String, Integer> body) {
        int radarCount = body.getOrDefault("radarCount", 10);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.triggerRun(radarCount));
    }

    @GetMapping("/runs")
    public ResponseEntity<List<EvalRunDTO>> runs() {
        return ResponseEntity.ok(service.listRuns());
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=EvalResourceIT`
Expected: PASS

- [ ] **Step 8: Run the full test suite**

Run: `cd backend && mvn test`
Expected: All tests PASS

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/devradar/eval/application/EvalApplicationService.java backend/src/main/java/com/devradar/web/rest/EvalResource.java backend/src/main/java/com/devradar/web/rest/dto/EvalRunDTO.java backend/src/main/java/com/devradar/web/rest/dto/EvalScoreDTO.java backend/src/test/java/com/devradar/web/rest/EvalResourceIT.java
git commit -m "feat(eval): add EvalResource with POST /api/evals/run and GET /api/evals/runs admin endpoints"
```
