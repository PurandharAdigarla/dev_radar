# RSS/Article Ingestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `ARTICLE` ingestion source that polls curated RSS/Atom feeds mapped to interest tags, so the AI orchestrator can build multi-source themes with blog/documentation citations alongside existing repo and HN citations.

**Architecture:** New `feed_subscription` table maps interest tag slugs to RSS feed URLs (seeded via Liquibase). A scheduled `RssFeedIngestor` job (every 2h) loads active subscriptions, calls `RssFeedClient` (Rome library) to parse each feed, and passes `FetchedItem`s through the existing `IngestionService` pipeline — same dedup, tagging, and persistence as all other sources. No orchestrator changes needed.

**Tech Stack:** Rome (`com.rometools:rome`) for RSS/Atom parsing, Spring `@Scheduled`, WireMock for testing, Liquibase for schema + seed data.

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `backend/src/main/java/com/devradar/domain/FeedSubscription.java` | JPA entity for the `feed_subscriptions` table |
| Create | `backend/src/main/java/com/devradar/repository/FeedSubscriptionRepository.java` | Spring Data JPA repository for FeedSubscription |
| Create | `backend/src/main/java/com/devradar/ingest/client/RssFeedClient.java` | Parses a single RSS/Atom feed URL into `List<FetchedItem>` |
| Create | `backend/src/main/java/com/devradar/ingest/job/RssFeedIngestor.java` | Scheduled job that iterates active feed subscriptions and ingests items |
| Create | `backend/src/main/resources/db/changelog/017-article-ingestion-schema.xml` | Creates `feed_subscriptions` table and seeds ARTICLE source + feed data |
| Create | `backend/src/test/java/com/devradar/ingest/client/RssFeedClientTest.java` | Unit tests for RSS/Atom parsing via WireMock |
| Create | `backend/src/test/java/com/devradar/ingest/job/RssFeedIngestorTest.java` | Unit test for ingestor scheduling and error isolation |
| Modify | `backend/pom.xml` | Add Rome dependency |
| Modify | `backend/src/main/resources/db/changelog/db.changelog-master.xml` | Include changeset 017 |
| Modify | `backend/src/main/resources/application-test.yml` | Disable article ingestor during tests |
| Modify | `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java` | Add ARTICLE source description to system prompt |
| Modify | `frontend/src/components/SourceCard.tsx` | Add `ARTICLE: "Article"` to SOURCE_LABELS |
| Modify | `frontend/src/test/mswHandlers.ts` | Add ARTICLE source to mock radar detail response |

---

### Task 1: Add Rome dependency to pom.xml

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Add Rome dependency**

Add after the `jsoup` dependency block in `backend/pom.xml`:

```xml
        <dependency>
            <groupId>com.rometools</groupId>
            <artifactId>rome</artifactId>
            <version>2.1.0</version>
        </dependency>
```

- [ ] **Step 2: Verify it compiles**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/pom.xml
git commit -m "chore: add Rome RSS/Atom parsing dependency"
```

---

### Task 2: Liquibase schema — feed_subscriptions table, ARTICLE source, and seed data

**Files:**
- Create: `backend/src/main/resources/db/changelog/017-article-ingestion-schema.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create changeset file**

Create `backend/src/main/resources/db/changelog/017-article-ingestion-schema.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="017-create-feed-subscriptions" author="devradar">
        <createTable tableName="feed_subscriptions">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="tag_slug" type="VARCHAR(100)">
                <constraints nullable="false"/>
            </column>
            <column name="feed_url" type="VARCHAR(2048)">
                <constraints nullable="false"/>
            </column>
            <column name="title" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="active" type="TINYINT(1)" defaultValueBoolean="true">
                <constraints nullable="false"/>
            </column>
        </createTable>
        <addUniqueConstraint tableName="feed_subscriptions"
            columnNames="tag_slug, feed_url"
            constraintName="uk_feed_subscriptions_tag_url"/>
        <addForeignKeyConstraint
            baseTableName="feed_subscriptions" baseColumnNames="tag_slug"
            referencedTableName="interest_tags" referencedColumnNames="slug"
            constraintName="fk_feed_subscriptions_tag"/>
    </changeSet>

    <changeSet id="017-seed-article-source" author="devradar">
        <sql>
INSERT INTO sources (code, display_name, active, fetch_interval_minutes, created_at) VALUES
  ('ARTICLE', 'Article', true, 120, NOW());
        </sql>
    </changeSet>

    <changeSet id="017-seed-feed-subscriptions" author="devradar">
        <sql>
INSERT INTO feed_subscriptions (tag_slug, feed_url, title, active) VALUES
  ('java',        'https://inside.java/feed/',                                'Inside Java',            true),
  ('java',        'https://www.baeldung.com/feed',                            'Baeldung',               true),
  ('spring_boot', 'https://spring.io/blog.atom',                              'Spring Blog',            true),
  ('react',       'https://react.dev/rss.xml',                                'React Blog',             true),
  ('javascript',  'https://blog.nodejs.org/feed/',                            'Node.js Blog',           true),
  ('typescript',  'https://devblogs.microsoft.com/typescript/feed/',           'TypeScript Blog',        true),
  ('python',      'https://blog.python.org/feeds/posts/default',              'Python Blog',            true),
  ('go',          'https://go.dev/blog/feed.atom',                            'Go Blog',                true),
  ('rust',        'https://blog.rust-lang.org/feed.xml',                      'Rust Blog',              true),
  ('docker',      'https://www.docker.com/blog/feed/',                        'Docker Blog',            true),
  ('kubernetes',  'https://kubernetes.io/feed.xml',                           'Kubernetes Blog',        true),
  ('security',    'https://blog.cloudflare.com/rss/',                         'Cloudflare Blog',        true),
  ('security',    'https://security.googleblog.com/feeds/posts/default',      'Google Security Blog',   true),
  ('postgresql',  'https://www.postgresql.org/news/feed/',                    'PostgreSQL News',        true);
        </sql>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: Add include to master changelog**

Add to `backend/src/main/resources/db/changelog/db.changelog-master.xml`, after the line `<include file="db/changelog/016-seed-gh-stars-source.xml"/>`:

```xml
    <include file="db/changelog/017-article-ingestion-schema.xml"/>
```

- [ ] **Step 3: Verify schema applies**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS (Liquibase validates at app start, but compilation catches XML parse errors)

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/017-article-ingestion-schema.xml backend/src/main/resources/db/changelog/db.changelog-master.xml
git commit -m "feat(backend): add feed_subscriptions table and seed ARTICLE source with 14 curated feeds"
```

---

### Task 3: FeedSubscription entity and repository

**Files:**
- Create: `backend/src/main/java/com/devradar/domain/FeedSubscription.java`
- Create: `backend/src/main/java/com/devradar/repository/FeedSubscriptionRepository.java`

- [ ] **Step 1: Create FeedSubscription entity**

Create `backend/src/main/java/com/devradar/domain/FeedSubscription.java`:

```java
package com.devradar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "feed_subscriptions",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_feed_subscriptions_tag_url",
           columnNames = {"tag_slug", "feed_url"}))
public class FeedSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_slug", nullable = false, length = 100)
    private String tagSlug;

    @Column(name = "feed_url", nullable = false, length = 2048)
    private String feedUrl;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean active = true;

    public Long getId() { return id; }
    public String getTagSlug() { return tagSlug; }
    public String getFeedUrl() { return feedUrl; }
    public String getTitle() { return title; }
    public boolean isActive() { return active; }
}
```

- [ ] **Step 2: Create FeedSubscriptionRepository**

Create `backend/src/main/java/com/devradar/repository/FeedSubscriptionRepository.java`:

```java
package com.devradar.repository;

import com.devradar.domain.FeedSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedSubscriptionRepository extends JpaRepository<FeedSubscription, Long> {
    List<FeedSubscription> findByActiveTrue();
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devradar/domain/FeedSubscription.java backend/src/main/java/com/devradar/repository/FeedSubscriptionRepository.java
git commit -m "feat(backend): add FeedSubscription entity and repository"
```

---

### Task 4: RssFeedClient — RSS/Atom parser

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/client/RssFeedClient.java`
- Create: `backend/src/test/java/com/devradar/ingest/client/RssFeedClientTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/devradar/ingest/client/RssFeedClientTest.java`:

```java
package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RssFeedClientTest {

    WireMockServer wm;
    RssFeedClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new RssFeedClient();
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void fetch_parsesRss20Feed() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/feed"))
            .willReturn(WireMock.okXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Spring Blog</title>
                    <item>
                      <title>Spring Boot 3.5 Released</title>
                      <link>https://spring.io/blog/2026/04/20/spring-boot-3-5</link>
                      <description>Major release with virtual thread support.</description>
                      <author>Spring Team</author>
                      <pubDate>Sun, 20 Apr 2026 10:00:00 GMT</pubDate>
                      <guid>https://spring.io/blog/2026/04/20/spring-boot-3-5</guid>
                    </item>
                  </channel>
                </rss>
                """)));

        List<FetchedItem> items = client.fetch("http://localhost:" + wm.port() + "/feed");

        assertThat(items).hasSize(1);
        FetchedItem item = items.get(0);
        assertThat(item.title()).isEqualTo("Spring Boot 3.5 Released");
        assertThat(item.url()).isEqualTo("https://spring.io/blog/2026/04/20/spring-boot-3-5");
        assertThat(item.description()).isEqualTo("Major release with virtual thread support.");
        assertThat(item.author()).isEqualTo("Spring Team");
        assertThat(item.externalId()).isEqualTo("https://spring.io/blog/2026/04/20/spring-boot-3-5");
        assertThat(item.postedAt()).isNotNull();
    }

    @Test
    void fetch_parsesAtomFeed() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/atom"))
            .willReturn(WireMock.okXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Go Blog</title>
                  <entry>
                    <title>Go 1.23 is released</title>
                    <link href="https://go.dev/blog/go1.23"/>
                    <id>tag:go.dev,2026:go1.23</id>
                    <summary>New features in Go 1.23.</summary>
                    <author><name>Go Team</name></author>
                    <published>2026-04-18T12:00:00Z</published>
                  </entry>
                </feed>
                """)));

        List<FetchedItem> items = client.fetch("http://localhost:" + wm.port() + "/atom");

        assertThat(items).hasSize(1);
        FetchedItem item = items.get(0);
        assertThat(item.title()).isEqualTo("Go 1.23 is released");
        assertThat(item.url()).isEqualTo("https://go.dev/blog/go1.23");
        assertThat(item.externalId()).isEqualTo("tag:go.dev,2026:go1.23");
        assertThat(item.description()).isEqualTo("New features in Go 1.23.");
        assertThat(item.author()).isEqualTo("Go Team");
    }

    @Test
    void fetch_returnsEmpty_onMalformedFeed() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/bad"))
            .willReturn(WireMock.ok("this is not xml")));

        List<FetchedItem> items = client.fetch("http://localhost:" + wm.port() + "/bad");

        assertThat(items).isEmpty();
    }

    @Test
    void fetch_truncatesLongDescription() {
        String longDesc = "x".repeat(3000);
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/long"))
            .willReturn(WireMock.okXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Long Post</title>
                      <link>https://example.com/long</link>
                      <description>%s</description>
                      <pubDate>Sun, 20 Apr 2026 10:00:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """.formatted(longDesc))));

        List<FetchedItem> items = client.fetch("http://localhost:" + wm.port() + "/long");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).description()).hasSize(2048);
    }

    @Test
    void fetch_skipsEntriesWithoutLink() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/nolink"))
            .willReturn(WireMock.okXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>No Link Item</title>
                      <pubDate>Sun, 20 Apr 2026 10:00:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """)));

        assertThat(client.fetch("http://localhost:" + wm.port() + "/nolink")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=RssFeedClientTest -DfailIfNoTests=false`
Expected: FAIL — `RssFeedClient` does not exist yet

- [ ] **Step 3: Implement RssFeedClient**

Create `backend/src/main/java/com/devradar/ingest/client/RssFeedClient.java`:

```java
package com.devradar.ingest.client;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class RssFeedClient {

    private static final Logger LOG = LoggerFactory.getLogger(RssFeedClient.class);
    private static final int MAX_DESCRIPTION_LENGTH = 2048;

    public List<FetchedItem> fetch(String feedUrl) {
        List<FetchedItem> out = new ArrayList<>();
        try {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(URI.create(feedUrl).toURL()));
            for (SyndEntry entry : feed.getEntries()) {
                String link = entry.getLink();
                String title = entry.getTitle();
                if (link == null || link.isBlank() || title == null || title.isBlank()) continue;

                String externalId = entry.getUri() != null ? entry.getUri() : link;
                String description = extractDescription(entry);
                String author = extractAuthor(entry);
                Instant postedAt = extractDate(entry);

                out.add(new FetchedItem(
                    externalId, link, title, description, author,
                    postedAt, null, List.of()
                ));
            }
        } catch (Exception e) {
            LOG.warn("failed to parse feed {}: {}", feedUrl, e.toString());
        }
        return out;
    }

    private static String extractDescription(SyndEntry entry) {
        String desc = null;
        if (entry.getDescription() != null) {
            desc = entry.getDescription().getValue();
        }
        if (desc == null || desc.isBlank()) {
            desc = entry.getContents().isEmpty() ? null
                : entry.getContents().get(0).getValue();
        }
        if (desc == null) return null;
        desc = desc.replaceAll("<[^>]+>", "").trim();
        return desc.length() > MAX_DESCRIPTION_LENGTH
            ? desc.substring(0, MAX_DESCRIPTION_LENGTH)
            : desc;
    }

    private static String extractAuthor(SyndEntry entry) {
        if (entry.getAuthor() != null && !entry.getAuthor().isBlank()) {
            return entry.getAuthor();
        }
        if (!entry.getAuthors().isEmpty()) {
            return entry.getAuthors().get(0).getName();
        }
        return null;
    }

    private static Instant extractDate(SyndEntry entry) {
        Date pub = entry.getPublishedDate();
        if (pub != null) return pub.toInstant();
        Date upd = entry.getUpdatedDate();
        if (upd != null) return upd.toInstant();
        return Instant.now();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl . -Dtest=RssFeedClientTest`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/client/RssFeedClient.java backend/src/test/java/com/devradar/ingest/client/RssFeedClientTest.java
git commit -m "feat(backend): add RssFeedClient for parsing RSS/Atom feeds into FetchedItems"
```

---

### Task 5: RssFeedIngestor — scheduled job

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/job/RssFeedIngestor.java`
- Create: `backend/src/test/java/com/devradar/ingest/job/RssFeedIngestorTest.java`
- Modify: `backend/src/main/resources/application-test.yml`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/devradar/ingest/job/RssFeedIngestorTest.java`:

```java
package com.devradar.ingest.job;

import com.devradar.domain.FeedSubscription;
import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.RssFeedClient;
import com.devradar.repository.FeedSubscriptionRepository;
import com.devradar.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RssFeedIngestorTest {

    @Mock SourceRepository sources;
    @Mock FeedSubscriptionRepository feedSubRepo;
    @Mock RssFeedClient client;
    @Mock IngestionService ingestion;
    @InjectMocks RssFeedIngestor ingestor;

    @Test
    void run_skipsWhenSourceNotActive() {
        when(sources.findByCode("ARTICLE")).thenReturn(Optional.empty());

        ingestor.run();

        verifyNoInteractions(feedSubRepo, client, ingestion);
    }

    @Test
    void run_ingestsItemsWithTagSlugInjected() {
        Source src = mock(Source.class);
        when(src.isActive()).thenReturn(true);
        when(sources.findByCode("ARTICLE")).thenReturn(Optional.of(src));

        FeedSubscription sub = mock(FeedSubscription.class);
        when(sub.getFeedUrl()).thenReturn("https://spring.io/blog.atom");
        when(sub.getTagSlug()).thenReturn("spring_boot");
        when(feedSubRepo.findByActiveTrue()).thenReturn(List.of(sub));

        FetchedItem rawItem = new FetchedItem(
            "guid-1", "https://spring.io/blog/post1", "Spring Boot 3.5",
            "Summary", "author", Instant.now(), null, List.of()
        );
        when(client.fetch("https://spring.io/blog.atom")).thenReturn(List.of(rawItem));

        ingestor.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FetchedItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestion).ingestBatch(eq(src), captor.capture());
        List<FetchedItem> ingested = captor.getValue();
        assertThat(ingested).hasSize(1);
        assertThat(ingested.get(0).topics()).containsExactly("spring_boot");
        assertThat(ingested.get(0).title()).isEqualTo("Spring Boot 3.5");
    }

    @Test
    void run_continuesOnFeedFailure() {
        Source src = mock(Source.class);
        when(src.isActive()).thenReturn(true);
        when(sources.findByCode("ARTICLE")).thenReturn(Optional.of(src));

        FeedSubscription goodSub = mock(FeedSubscription.class);
        when(goodSub.getFeedUrl()).thenReturn("https://good.com/feed");
        when(goodSub.getTagSlug()).thenReturn("java");

        FeedSubscription badSub = mock(FeedSubscription.class);
        when(badSub.getFeedUrl()).thenReturn("https://bad.com/feed");
        when(badSub.getTagSlug()).thenReturn("python");

        when(feedSubRepo.findByActiveTrue()).thenReturn(List.of(badSub, goodSub));
        when(client.fetch("https://bad.com/feed")).thenThrow(new RuntimeException("network error"));
        when(client.fetch("https://good.com/feed")).thenReturn(List.of(
            new FetchedItem("g1", "https://good.com/1", "Good Post", null, null, Instant.now(), null, List.of())
        ));

        ingestor.run();

        verify(ingestion).ingestBatch(eq(src), any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=RssFeedIngestorTest -DfailIfNoTests=false`
Expected: FAIL — `RssFeedIngestor` does not exist yet

- [ ] **Step 3: Implement RssFeedIngestor**

Create `backend/src/main/java/com/devradar/ingest/job/RssFeedIngestor.java`:

```java
package com.devradar.ingest.job;

import com.devradar.domain.FeedSubscription;
import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.RssFeedClient;
import com.devradar.repository.FeedSubscriptionRepository;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RssFeedIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(RssFeedIngestor.class);
    private static final String CODE = "ARTICLE";

    private final RssFeedClient client;
    private final IngestionService ingestion;
    private final SourceRepository sources;
    private final FeedSubscriptionRepository feedSubRepo;

    public RssFeedIngestor(RssFeedClient client, IngestionService ingestion,
                           SourceRepository sources, FeedSubscriptionRepository feedSubRepo) {
        this.client = client;
        this.ingestion = ingestion;
        this.sources = sources;
        this.feedSubRepo = feedSubRepo;
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.article.fixed-delay-ms:7200000}",
               initialDelayString = "${devradar.ingest.article.initial-delay-ms:60000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("ARTICLE source not active; skipping");
            return;
        }

        List<FeedSubscription> subs = feedSubRepo.findByActiveTrue();
        LOG.info("article ingestion starting; {} active feeds", subs.size());

        List<FetchedItem> allItems = new ArrayList<>();
        for (FeedSubscription sub : subs) {
            try {
                List<FetchedItem> raw = client.fetch(sub.getFeedUrl());
                for (FetchedItem item : raw) {
                    allItems.add(new FetchedItem(
                        item.externalId(), item.url(), item.title(),
                        item.description(), item.author(), item.postedAt(),
                        item.rawPayload(), List.of(sub.getTagSlug())
                    ));
                }
            } catch (Exception e) {
                LOG.warn("feed fetch failed url={}: {}", sub.getFeedUrl(), e.toString());
            }
        }

        ingestion.ingestBatch(src, allItems);
    }
}
```

- [ ] **Step 4: Add test config to disable during tests**

Add to `backend/src/main/resources/application-test.yml`, inside the `devradar.ingest` block, after the `gh-releases` section:

```yaml
    article:
      fixed-delay-ms: 86400000
      initial-delay-ms: 86400000
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && mvn test -pl . -Dtest=RssFeedIngestorTest`
Expected: All 3 tests PASS

- [ ] **Step 6: Run full test suite**

Run: `cd backend && mvn test`
Expected: All tests PASS (no regressions)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/job/RssFeedIngestor.java backend/src/test/java/com/devradar/ingest/job/RssFeedIngestorTest.java backend/src/main/resources/application-test.yml
git commit -m "feat(backend): add RssFeedIngestor scheduled job for article ingestion"
```

---

### Task 6: Update orchestrator prompt with ARTICLE source description

**Files:**
- Modify: `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java`

- [ ] **Step 1: Add ARTICLE source to the CITATION PRIORITY section**

In `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java`, find the `CITATION PRIORITY:` section in `SYSTEM_PROMPT` (starts around line 37). After the paragraph ending with `"trending items only link to repo homepages with no context about what changed."`, add:

```
        - ARTICLE items are blog posts and official documentation from authoritative sources.
          They provide valuable context for WHY a change matters. When building a theme about a
          release or trending project, prefer to include an ARTICLE citation alongside the release
          item to give the user both the changelog and expert analysis.
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devradar/ai/RadarOrchestrator.java
git commit -m "feat(backend): add ARTICLE source description to orchestrator prompt"
```

---

### Task 7: Frontend — add ARTICLE label to SourceCard

**Files:**
- Modify: `frontend/src/components/SourceCard.tsx`
- Modify: `frontend/src/test/mswHandlers.ts`

- [ ] **Step 1: Add ARTICLE to SOURCE_LABELS**

In `frontend/src/components/SourceCard.tsx`, update the `SOURCE_LABELS` record to add the `ARTICLE` entry:

```typescript
const SOURCE_LABELS: Record<string, string> = {
  HN: "HN",
  GH_TRENDING: "GitHub",
  GH_RELEASES: "Release",
  GH_STARS: "Starred",
  GHSA: "GHSA",
  ARTICLE: "Article",
};
```

- [ ] **Step 2: Add an ARTICLE item to the mock radar detail handler**

In `frontend/src/test/mswHandlers.ts`, find the `http.get("/api/radars/:id"` handler's `themes` array in the default (non-404, non-43) response. Add a second item to the first theme's `items` array:

```typescript
items: [
  { id: 1001, title: "Spring Boot 3.5 released", description: "Major release with virtual thread support.", url: "https://spring.io/3.5", author: "spring-io", sourceName: "GH_RELEASES" },
  { id: 1002, title: "What's New in Spring Boot 3.5", description: "Deep dive into the new features.", url: "https://baeldung.com/spring-boot-3-5", author: "Baeldung", sourceName: "ARTICLE" },
],
```

- [ ] **Step 3: Run frontend tests**

Run: `cd frontend && npx vitest run`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/SourceCard.tsx frontend/src/test/mswHandlers.ts
git commit -m "feat(frontend): add ARTICLE source label to SourceCard"
```

---

### Task 8: Integration smoke test — verify end-to-end

**Files:** None (manual verification)

- [ ] **Step 1: Start the backend with local MySQL**

Run: `cd backend && DB_HOST_PORT=3307 mvn spring-boot:run`
Expected: Application starts, Liquibase applies changeset 017, logs show "article ingestion starting; 14 active feeds" within 60 seconds

- [ ] **Step 2: Verify feed_subscriptions table was seeded**

Run: `docker exec backend-mysql-1 mysql -u devradar -pdevradar devradar -e "SELECT tag_slug, title, active FROM feed_subscriptions ORDER BY id;"`
Expected: 14 rows with all curated feeds

- [ ] **Step 3: Verify ARTICLE source was created**

Run: `docker exec backend-mysql-1 mysql -u devradar -pdevradar devradar -e "SELECT code, display_name, active FROM sources WHERE code='ARTICLE';"`
Expected: One row: `ARTICLE | Article | 1`

- [ ] **Step 4: Check that articles are being ingested**

Wait for the initial ingestor run (60s initial delay), then:

Run: `docker exec backend-mysql-1 mysql -u devradar -pdevradar devradar -e "SELECT si.title, s.code FROM source_items si JOIN sources s ON si.source_id = s.id WHERE s.code = 'ARTICLE' ORDER BY si.fetched_at DESC LIMIT 10;"`
Expected: Recent articles from the curated feeds with source code `ARTICLE`

- [ ] **Step 5: Verify articles are tagged**

Run: `docker exec backend-mysql-1 mysql -u devradar -pdevradar devradar -e "SELECT si.title, it.slug FROM source_items si JOIN sources s ON si.source_id = s.id JOIN source_item_tags sit ON si.id = sit.source_item_id JOIN interest_tags it ON sit.interest_tag_id = it.id WHERE s.code = 'ARTICLE' LIMIT 10;"`
Expected: Articles tagged with their corresponding interest tags (e.g., Spring Blog articles tagged with `spring_boot`)

- [ ] **Step 6: Run full backend test suite**

Run: `cd backend && mvn test`
Expected: All tests PASS

- [ ] **Step 7: Run full frontend test suite**

Run: `cd frontend && npx vitest run`
Expected: All tests PASS
