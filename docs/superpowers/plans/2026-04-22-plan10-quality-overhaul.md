# Radar Quality Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the entire data pipeline so radars produce specific, actionable themes with rich inline citations instead of generic category labels with meaningless number pills.

**Architecture:** Full-stack fix touching 7 layers: DB migration → entity → ingestion clients → tag extractor → AI tools → orchestrator prompt → DTOs → frontend. Each layer enriches the data flowing through it. ScoreRelevanceTool is removed (redundant with richer data).

**Tech Stack:** Java 21, Spring Boot 3.5, Liquibase, JSoup, Recharts, React 19, MUI 6

---

## File Plan

### New Files

```
backend/src/main/resources/db/changelog/014-add-description-column.xml
frontend/src/components/SourceCard.tsx
```

### Modified Files

```
backend/src/main/java/com/devradar/ingest/client/FetchedItem.java
backend/src/main/java/com/devradar/domain/SourceItem.java
backend/src/main/java/com/devradar/ingest/IngestionService.java
backend/src/main/java/com/devradar/ingest/TagExtractor.java
backend/src/main/java/com/devradar/ingest/client/HackerNewsClient.java
backend/src/main/java/com/devradar/ingest/client/GitHubTrendingClient.java
backend/src/main/java/com/devradar/ingest/client/GHSAClient.java
backend/src/main/java/com/devradar/ai/tools/SearchItemsTool.java
backend/src/main/java/com/devradar/ai/tools/GetItemDetailTool.java
backend/src/main/java/com/devradar/ai/tools/ToolRegistry.java
backend/src/main/java/com/devradar/ai/RadarOrchestrator.java
backend/src/main/java/com/devradar/web/rest/dto/RadarItemDTO.java
backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java
backend/src/main/java/com/devradar/mcp/dto/CitationMcpDTO.java
backend/src/main/resources/db/changelog/db.changelog-master.xml
backend/src/test/java/com/devradar/ingest/client/HackerNewsClientTest.java
backend/src/test/java/com/devradar/ingest/client/GitHubTrendingClientTest.java
backend/src/test/java/com/devradar/ingest/client/GHSAClientTest.java
backend/src/test/java/com/devradar/ingest/TagExtractorTest.java
backend/src/test/resources/github-trending-sample.html
frontend/src/api/types.ts
frontend/src/components/ThemeCard.tsx
```

### Deleted Files

```
backend/src/main/java/com/devradar/ai/tools/ScoreRelevanceTool.java
backend/src/test/java/com/devradar/ai/tools/ScoreRelevanceToolTest.java
frontend/src/components/CitationPill.tsx
```

---

### Task 1: Database migration + entity + FetchedItem record

**Files:**
- Create: `backend/src/main/resources/db/changelog/014-add-description-column.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Modify: `backend/src/main/java/com/devradar/domain/SourceItem.java`
- Modify: `backend/src/main/java/com/devradar/ingest/client/FetchedItem.java`
- Modify: `backend/src/main/java/com/devradar/ingest/IngestionService.java`

- [ ] **Step 1: Create Liquibase migration 014**

Create `backend/src/main/resources/db/changelog/014-add-description-column.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="014-1" author="devradar">
        <addColumn tableName="source_items">
            <column name="description" type="TEXT"/>
        </addColumn>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: Register migration in master changelog**

In `backend/src/main/resources/db/changelog/db.changelog-master.xml`, add before the closing `</databaseChangeLog>`:

```xml
    <include file="db/changelog/014-add-description-column.xml"/>
```

- [ ] **Step 3: Add description field to SourceItem entity**

In `backend/src/main/java/com/devradar/domain/SourceItem.java`, add after the `author` field (line 26):

```java
    @Column(columnDefinition = "TEXT")
    private String description;
```

Add getter/setter after the existing author getter/setter (after line 50):

```java
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
```

- [ ] **Step 4: Add description parameter to FetchedItem record**

Replace the entire `backend/src/main/java/com/devradar/ingest/client/FetchedItem.java`:

```java
package com.devradar.ingest.client;

import java.time.Instant;
import java.util.List;

public record FetchedItem(
    String externalId,
    String url,
    String title,
    String description,
    String author,
    Instant postedAt,
    String rawPayload,
    List<String> topics
) {}
```

- [ ] **Step 5: Update IngestionService to persist description**

In `backend/src/main/java/com/devradar/ingest/IngestionService.java`, in the `ingestOne` method, add after `si.setAuthor(item.author());` (after line 90):

```java
        si.setDescription(item.description());
```

- [ ] **Step 6: Fix all FetchedItem constructor calls in ingestion clients**

All three clients now have a compile error because FetchedItem has a new `description` parameter after `title`.

In `backend/src/main/java/com/devradar/ingest/client/HackerNewsClient.java`, line 41, change:

```java
            out.add(new FetchedItem(externalId, url, title, author, posted, hit.toString(), List.of()));
```

to (temporarily pass null — we'll enrich in Task 2):

```java
            out.add(new FetchedItem(externalId, url, title, null, author, posted, hit.toString(), List.of()));
```

In `backend/src/main/java/com/devradar/ingest/client/GitHubTrendingClient.java`, line 44, change:

```java
            out.add(new FetchedItem(href, url, href, null, now, null, topics));
```

to:

```java
            out.add(new FetchedItem(href, url, href, null, null, now, null, topics));
```

In `backend/src/main/java/com/devradar/ingest/client/GHSAClient.java`, line 37, change:

```java
            out.add(new FetchedItem(ghsaId, url, summary, null, posted, adv.toString(), List.of("security")));
```

to:

```java
            out.add(new FetchedItem(ghsaId, url, summary, null, null, posted, adv.toString(), List.of("security")));
```

- [ ] **Step 7: Verify backend compiles**

```bash
cd backend && mvn -DskipTests compile
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/changelog/014-add-description-column.xml backend/src/main/resources/db/changelog/db.changelog-master.xml backend/src/main/java/com/devradar/domain/SourceItem.java backend/src/main/java/com/devradar/ingest/client/FetchedItem.java backend/src/main/java/com/devradar/ingest/IngestionService.java backend/src/main/java/com/devradar/ingest/client/HackerNewsClient.java backend/src/main/java/com/devradar/ingest/client/GitHubTrendingClient.java backend/src/main/java/com/devradar/ingest/client/GHSAClient.java
git commit -m "feat(backend): add description column to source_items + FetchedItem record"
```

---

### Task 2: Enrich HackerNews client

**Files:**
- Modify: `backend/src/main/java/com/devradar/ingest/client/HackerNewsClient.java`
- Modify: `backend/src/test/java/com/devradar/ingest/client/HackerNewsClientTest.java`

- [ ] **Step 1: Update test to assert description field**

In `backend/src/test/java/com/devradar/ingest/client/HackerNewsClientTest.java`, update the WireMock JSON in `fetchRecent_returnsParsedItems` to include `num_comments` and `story_text`, and add description assertions.

Replace the entire test class:

```java
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
    void fetchRecent_returnsParsedItems_withDescription() {
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
                      "points": 280,
                      "num_comments": 142
                    },
                    {
                      "objectID": "12346",
                      "title": "Ask HN: Best practices for Java 21?",
                      "url": "https://news.ycombinator.com/item?id=12346",
                      "author": "javadev",
                      "created_at_i": 1755103600,
                      "points": 95,
                      "num_comments": 67,
                      "story_text": "I recently started using virtual threads and pattern matching. What other Java 21 features are you using in production?"
                    }
                  ]
                }
                """)));

        List<FetchedItem> items = client.fetchRecent(50);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("Spring Boot 3.5 released");
        assertThat(items.get(0).description()).isEqualTo("280 points, 142 comments on Hacker News");
        assertThat(items.get(1).description()).startsWith("I recently started using virtual threads");
    }

    @Test
    void fetchRecent_returnsEmpty_onEmptyHits() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/search_by_date"))
            .willReturn(WireMock.okJson("{\"hits\": []}")));

        assertThat(client.fetchRecent(50)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn test -pl . -Dtest=HackerNewsClientTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because description is still null.

- [ ] **Step 3: Implement HN description extraction**

Replace `backend/src/main/java/com/devradar/ingest/client/HackerNewsClient.java`:

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

            String description = buildDescription(hit);
            out.add(new FetchedItem(externalId, url, title, description, author, posted, hit.toString(), List.of()));
        }
        return out;
    }

    private static String buildDescription(JsonNode hit) {
        String storyText = textOrNull(hit, "story_text");
        if (storyText != null && !storyText.isBlank()) {
            return storyText.length() > 500 ? storyText.substring(0, 500) : storyText;
        }
        int points = hit.path("points").asInt(0);
        int comments = hit.path("num_comments").asInt(0);
        if (points > 0) {
            return points + " points, " + comments + " comments on Hacker News";
        }
        return null;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend && mvn test -pl . -Dtest=HackerNewsClientTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/client/HackerNewsClient.java backend/src/test/java/com/devradar/ingest/client/HackerNewsClientTest.java
git commit -m "feat(backend): enrich HN ingestion with points, comments, and story_text"
```

---

### Task 3: Enrich GitHub Trending client

**Files:**
- Modify: `backend/src/main/java/com/devradar/ingest/client/GitHubTrendingClient.java`
- Modify: `backend/src/test/java/com/devradar/ingest/client/GitHubTrendingClientTest.java`
- Modify: `backend/src/test/resources/github-trending-sample.html`

- [ ] **Step 1: Update sample HTML to include star counts**

Replace `backend/src/test/resources/github-trending-sample.html`:

```html
<html><body>
<article class="Box-row">
  <h2 class="h3 lh-condensed"><a href="/spring-projects/spring-boot">spring-projects / spring-boot</a></h2>
  <p class="col-9 color-fg-muted my-1 pr-4">Spring Boot makes it easy to create Spring-powered applications</p>
  <span itemprop="programmingLanguage">Java</span>
  <a class="Link--muted d-inline-block mr-3" href="/spring-projects/spring-boot/stargazers">78,200</a>
</article>
<article class="Box-row">
  <h2 class="h3 lh-condensed"><a href="/bigskysoftware/htmx">bigskysoftware / htmx</a></h2>
  <p class="col-9 color-fg-muted my-1 pr-4">&lt;/&gt; htmx - high power tools for HTML</p>
  <span itemprop="programmingLanguage">JavaScript</span>
  <a class="Link--muted d-inline-block mr-3" href="/bigskysoftware/htmx/stargazers">42,100</a>
</article>
</body></html>
```

- [ ] **Step 2: Update test to assert description and proper title**

Replace the entire `backend/src/test/java/com/devradar/ingest/client/GitHubTrendingClientTest.java`:

```java
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
    void fetchTrending_parsesRepoCards_withDescriptionAndTitle() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/trending"))
            .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "text/html").withBody(sampleHtml)));

        List<FetchedItem> items = client.fetchTrending(null);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).externalId()).isEqualTo("spring-projects/spring-boot");
        assertThat(items.get(0).title()).isEqualTo("spring-boot");
        assertThat(items.get(0).description()).contains("Spring Boot makes it easy");
        assertThat(items.get(0).description()).contains("78,200 stars");
        assertThat(items.get(0).url()).isEqualTo("https://github.com/spring-projects/spring-boot");
        assertThat(items.get(0).topics()).contains("java");
        assertThat(items.get(1).title()).isEqualTo("htmx");
        assertThat(items.get(1).description()).contains("htmx - high power tools for HTML");
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

- [ ] **Step 3: Run test to verify it fails**

```bash
cd backend && mvn test -pl . -Dtest=GitHubTrendingClientTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL — title is still full path, description is null.

- [ ] **Step 4: Implement enriched GitHub Trending client**

Replace `backend/src/main/java/com/devradar/ingest/client/GitHubTrendingClient.java`:

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

    public GitHubTrendingClient(RestClient.Builder builder,
                                @Value("${devradar.gh-trending.base-url:https://github.com}") String baseUrl) {
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

            String repoName = href.contains("/") ? href.substring(href.lastIndexOf('/') + 1) : href;

            Element descEl = card.selectFirst("p");
            String repoDesc = descEl != null ? descEl.text().trim() : "";

            Element starsEl = card.selectFirst("a[href$=/stargazers]");
            String stars = starsEl != null ? starsEl.text().trim() : "";

            String description = buildDescription(repoDesc, stars);

            Element langEl = card.selectFirst("[itemprop=programmingLanguage]");
            List<String> topics = new ArrayList<>();
            if (langEl != null) topics.add(langEl.text().toLowerCase(Locale.ROOT));

            out.add(new FetchedItem(href, url, repoName, description, null, now, null, topics));
        }
        return out;
    }

    private static String buildDescription(String repoDesc, String stars) {
        StringBuilder sb = new StringBuilder();
        if (!repoDesc.isEmpty()) sb.append(repoDesc);
        if (!stars.isEmpty()) {
            if (sb.length() > 0) sb.append(". ");
            sb.append(stars).append(" stars on GitHub");
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
cd backend && mvn test -pl . -Dtest=GitHubTrendingClientTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/client/GitHubTrendingClient.java backend/src/test/java/com/devradar/ingest/client/GitHubTrendingClientTest.java backend/src/test/resources/github-trending-sample.html
git commit -m "feat(backend): enrich GitHub Trending with repo description and star count"
```

---

### Task 4: Enrich GHSA client

**Files:**
- Modify: `backend/src/main/java/com/devradar/ingest/client/GHSAClient.java`
- Modify: `backend/src/test/java/com/devradar/ingest/client/GHSAClientTest.java`

- [ ] **Step 1: Update test to assert structured description**

Replace the entire `backend/src/test/java/com/devradar/ingest/client/GHSAClientTest.java`:

```java
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
    void fetchRecent_returnsParsedAdvisories_withStructuredDescription() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/advisories"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "ghsa_id": "GHSA-xxxx-yyyy-zzzz",
                    "cve_id": "CVE-2026-12345",
                    "summary": "jackson-databind RCE in 2.16.x",
                    "html_url": "https://github.com/advisories/GHSA-xxxx-yyyy-zzzz",
                    "severity": "high",
                    "published_at": "2026-04-15T12:00:00Z",
                    "vulnerabilities": [{
                      "package": {"ecosystem": "maven", "name": "com.fasterxml.jackson.core:jackson-databind"},
                      "vulnerable_version_range": "< 2.16.3",
                      "patched_versions": "2.16.3"
                    }]
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchRecent();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).contains("jackson-databind");
        assertThat(items.get(0).description()).contains("HIGH");
        assertThat(items.get(0).description()).contains("jackson-databind");
        assertThat(items.get(0).description()).contains("2.16.3");
        assertThat(items.get(0).description()).contains("CVE-2026-12345");
        assertThat(items.get(0).topics()).contains("security");
    }

    @Test
    void fetchRecent_handlesAdvisoryWithoutVulnerabilities() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/advisories"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "ghsa_id": "GHSA-aaaa-bbbb-cccc",
                    "summary": "Some generic advisory",
                    "html_url": "https://github.com/advisories/GHSA-aaaa-bbbb-cccc",
                    "severity": "low",
                    "published_at": "2026-04-16T12:00:00Z"
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchRecent();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).description()).contains("LOW");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn test -pl . -Dtest=GHSAClientTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL — description is null.

- [ ] **Step 3: Implement enriched GHSA client**

Replace `backend/src/main/java/com/devradar/ingest/client/GHSAClient.java`:

```java
package com.devradar.ingest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

            String description = buildDescription(adv);
            out.add(new FetchedItem(ghsaId, url, summary, description, null, posted, adv.toString(), List.of("security")));
        }
        return out;
    }

    private static String buildDescription(JsonNode adv) {
        StringBuilder sb = new StringBuilder();
        String severity = textOrNull(adv, "severity");
        if (severity != null) sb.append(severity.toUpperCase(Locale.ROOT)).append(" severity");

        JsonNode vulns = adv.path("vulnerabilities");
        if (vulns.isArray() && vulns.size() > 0) {
            JsonNode first = vulns.get(0);
            String pkgName = first.path("package").path("name").asText(null);
            String ecosystem = first.path("package").path("ecosystem").asText(null);
            String vulnRange = textOrNull(first, "vulnerable_version_range");
            String patched = textOrNull(first, "patched_versions");

            if (pkgName != null) {
                sb.append(". Affects ").append(pkgName);
                if (ecosystem != null) sb.append(" (").append(ecosystem).append(")");
                if (vulnRange != null) sb.append(" ").append(vulnRange);
            }
            if (patched != null) sb.append(". Fix: upgrade to ").append(patched);
        }

        String cveId = textOrNull(adv, "cve_id");
        if (cveId != null) sb.append(". ").append(cveId);

        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend && mvn test -pl . -Dtest=GHSAClientTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/client/GHSAClient.java backend/src/test/java/com/devradar/ingest/client/GHSAClientTest.java
git commit -m "feat(backend): enrich GHSA ingestion with severity, affected package, and fix version"
```

---

### Task 5: Update TagExtractor to use description

**Files:**
- Modify: `backend/src/main/java/com/devradar/ingest/TagExtractor.java`
- Modify: `backend/src/main/java/com/devradar/ingest/IngestionService.java`
- Modify: `backend/src/test/java/com/devradar/ingest/TagExtractorTest.java`

- [ ] **Step 1: Add test for description-based tag matching**

In `backend/src/test/java/com/devradar/ingest/TagExtractorTest.java`, add a new test after the existing tests:

```java
    @Test
    void extracts_fromDescription() {
        Set<Long> ids = extractor.extract("jextract", "Extract Java bindings from C headers using Panama FFI", List.of());
        assertThat(ids).isNotEmpty(); // "Java" in description should match
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn test -pl . -Dtest=TagExtractorTest#extracts_fromDescription -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL — current `extract` method doesn't accept description parameter.

- [ ] **Step 3: Update TagExtractor signature and implementation**

Replace `backend/src/main/java/com/devradar/ingest/TagExtractor.java`:

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

    public Set<Long> extract(String title, String description, List<String> explicitTopics) {
        String hay = normalize(title) + " " + normalize(description);
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

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
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

- [ ] **Step 4: Update existing tests to use new 3-param signature**

In `backend/src/test/java/com/devradar/ingest/TagExtractorTest.java`, update all existing `extract` calls from 2-param to 3-param.

Change every `extractor.extract("...", List.of(...))` to `extractor.extract("...", null, List.of(...))`:

```java
    @Test
    void extracts_displayName_caseInsensitive() {
        Set<Long> ids = extractor.extract("Spring Boot 3.5 just shipped", null, List.of());
        assertThat(ids).hasSize(1);
    }

    @Test
    void extracts_slugWordBoundary() {
        Set<Long> ids = extractor.extract("Why I'm switching from React to Svelte", null, List.of());
        assertThat(ids).hasSize(1);
    }

    @Test
    void extracts_fromExplicitTopics() {
        Set<Long> ids = extractor.extract("Some unrelated title", null, List.of("rust", "mysql"));
        assertThat(ids).hasSize(2);
    }

    @Test
    void noMatch_returnsEmpty() {
        Set<Long> ids = extractor.extract("Nothing relevant here at all", null, List.of());
        assertThat(ids).isEmpty();
    }

    @Test
    void deduplicates_acrossTextAndTopics() {
        Set<Long> ids = extractor.extract("React is great", null, List.of("react"));
        assertThat(ids).hasSize(1);
    }
```

- [ ] **Step 5: Update IngestionService to pass description to TagExtractor**

In `backend/src/main/java/com/devradar/ingest/IngestionService.java`, line 95, change:

```java
        Set<Long> tagIds = tagExtractor.extract(item.title(), item.topics());
```

to:

```java
        Set<Long> tagIds = tagExtractor.extract(item.title(), item.description(), item.topics());
```

- [ ] **Step 6: Run all tests to verify**

```bash
cd backend && mvn test -pl . -Dtest=TagExtractorTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/TagExtractor.java backend/src/test/java/com/devradar/ingest/TagExtractorTest.java backend/src/main/java/com/devradar/ingest/IngestionService.java
git commit -m "feat(backend): TagExtractor matches on title + description for better tag coverage"
```

---

### Task 6: Update AI tools + remove ScoreRelevanceTool

**Files:**
- Modify: `backend/src/main/java/com/devradar/ai/tools/SearchItemsTool.java`
- Modify: `backend/src/main/java/com/devradar/ai/tools/GetItemDetailTool.java`
- Modify: `backend/src/main/java/com/devradar/ai/tools/ToolRegistry.java`
- Delete: `backend/src/main/java/com/devradar/ai/tools/ScoreRelevanceTool.java`
- Delete: `backend/src/test/java/com/devradar/ai/tools/ScoreRelevanceToolTest.java`

- [ ] **Step 1: Add description to SearchItemsTool output**

In `backend/src/main/java/com/devradar/ai/tools/SearchItemsTool.java`, in the `execute` method, add after `n.put("title", si.getTitle());` (line 70):

```java
                n.put("description", si.getDescription());
```

- [ ] **Step 2: Update GetItemDetailTool to return description and source_name, drop raw_payload**

Replace `backend/src/main/java/com/devradar/ai/tools/GetItemDetailTool.java`:

```java
package com.devradar.ai.tools;

import com.devradar.domain.Source;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GetItemDetailTool {

    public static final String NAME = "getItemDetail";
    public static final String DESCRIPTION = "Get structured details (title, description, url, author, source_name) of one source_item by id.";
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
    private final SourceRepository sourceRepo;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Long, String> sourceNameCache = new ConcurrentHashMap<>();

    public GetItemDetailTool(SourceItemRepository repo, SourceRepository sourceRepo) {
        this.repo = repo;
        this.sourceRepo = sourceRepo;
    }

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
            n.put("title", si.getTitle());
            n.put("description", si.getDescription());
            n.put("url", si.getUrl());
            n.put("author", si.getAuthor());
            n.put("source_name", resolveSourceName(si.getSourceId()));
            n.put("posted_at", si.getPostedAt().toString());
            return json.writeValueAsString(n);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String resolveSourceName(Long sourceId) {
        return sourceNameCache.computeIfAbsent(sourceId, id ->
            sourceRepo.findById(id).map(Source::getCode).orElse("unknown"));
    }
}
```

- [ ] **Step 3: Remove ScoreRelevanceTool and its test**

Delete `backend/src/main/java/com/devradar/ai/tools/ScoreRelevanceTool.java` and `backend/src/test/java/com/devradar/ai/tools/ScoreRelevanceToolTest.java`.

- [ ] **Step 4: Update ToolRegistry to remove ScoreRelevanceTool**

Replace `backend/src/main/java/com/devradar/ai/tools/ToolRegistry.java`:

```java
package com.devradar.ai.tools;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolRegistry {

    private final SearchItemsTool search;
    private final GetItemDetailTool detail;
    private final CheckRepoForVulnerabilityTool repoCheck;

    public ToolRegistry(SearchItemsTool search, GetItemDetailTool detail, CheckRepoForVulnerabilityTool repoCheck) {
        this.search = search; this.detail = detail; this.repoCheck = repoCheck;
    }

    public List<ToolDefinition> definitions() {
        return List.of(search.definition(), detail.definition(), repoCheck.definition());
    }

    public String dispatch(String name, String inputJson, ToolContext ctx) {
        return switch (name) {
            case SearchItemsTool.NAME -> search.execute(inputJson);
            case GetItemDetailTool.NAME -> detail.execute(inputJson);
            case CheckRepoForVulnerabilityTool.NAME -> repoCheck.execute(inputJson, ctx);
            default -> "{\"error\":\"unknown tool: " + name + "\"}";
        };
    }
}
```

- [ ] **Step 5: Verify backend compiles**

```bash
cd backend && mvn -DskipTests compile
```

- [ ] **Step 6: Commit**

```bash
git rm backend/src/main/java/com/devradar/ai/tools/ScoreRelevanceTool.java backend/src/test/java/com/devradar/ai/tools/ScoreRelevanceToolTest.java
git add backend/src/main/java/com/devradar/ai/tools/SearchItemsTool.java backend/src/main/java/com/devradar/ai/tools/GetItemDetailTool.java backend/src/main/java/com/devradar/ai/tools/ToolRegistry.java
git commit -m "feat(backend): enrich AI tools with description, remove ScoreRelevanceTool"
```

---

### Task 7: Rewrite orchestrator system prompt

**Files:**
- Modify: `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java`

- [ ] **Step 1: Replace the SYSTEM_PROMPT constant**

In `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java`, replace the entire `SYSTEM_PROMPT` string (lines 21-38) with:

```java
    private static final String SYSTEM_PROMPT = """
        You are a tech radar analyst. Given a user's interest tags and a pool of recently ingested items,
        identify 3-5 themes that matter to this user THIS WEEK.

        QUALITY RULES:
        - Every theme title must reference a SPECIFIC technology, event, release, or vulnerability.
          BAD: "Java Ecosystem & Frameworks"
          GOOD: "Spring Boot 3.5 drops native GraalVM support for WebFlux"
          GOOD: "CVE-2026-12345: RCE in Spring Framework < 6.1.5"
        - Every summary must explain WHY this matters to the user specifically and WHAT they should do.
        - Do not create themes that could apply to any random week. Each theme must be tied to
          something that happened in the last 7 days.
        - If an item is a GitHub trending repo, explain what it does and why it's trending,
          not just that it exists.
        - If an item is a security advisory, include the severity, affected package, and fix version.

        Use the provided tools to search items by tag, fetch item details, and investigate.
        When you encounter a CVE-related item, call checkRepoForVulnerability to see if
        the user's repos are affected.

        Output a single JSON object with NO PROSE around it:
        {"themes": [
          {"title": "...", "summary": "...", "item_ids": [<source_item ids cited>]},
          ...
        ]}

        Each theme should:
        - Have a specific, concrete title under 120 chars.
        - Have a summary of 2-4 sentences citing why it matters to THIS user and what action to take.
        - Reference 1-5 source_item ids from your search results.
        - Do not invent ids — only cite ids you've seen in tool results.
        """;
```

- [ ] **Step 2: Verify backend compiles**

```bash
cd backend && mvn -DskipTests compile
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devradar/ai/RadarOrchestrator.java
git commit -m "feat(backend): rewrite orchestrator prompt to demand specific actionable themes"
```

---

### Task 8: Update backend DTOs and RadarApplicationService

**Files:**
- Modify: `backend/src/main/java/com/devradar/web/rest/dto/RadarItemDTO.java`
- Modify: `backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java`
- Modify: `backend/src/main/java/com/devradar/mcp/dto/CitationMcpDTO.java`

- [ ] **Step 1: Update RadarItemDTO to include description and sourceName**

Replace `backend/src/main/java/com/devradar/web/rest/dto/RadarItemDTO.java`:

```java
package com.devradar.web.rest.dto;

public record RadarItemDTO(Long id, String title, String description, String url, String author, String sourceName) {}
```

- [ ] **Step 2: Update RadarApplicationService to populate new fields**

In `backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java`:

Add import at top:

```java
import com.devradar.domain.Source;
```

Add a field and constructor parameter for `SourceRepository`:

In the constructor, add `SourceRepository sourceRepo` parameter and field. Then add a `resolveSourceName` helper.

Replace the field declarations and constructor (lines 34-60) with:

```java
    private final RadarService radarService;
    private final RadarGenerationService generation;
    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeItemRepository themeItemRepo;
    private final SourceItemRepository sourceItemRepo;
    private final SourceRepository sourceRepo;
    private final UserInterestService interests;
    private final java.util.Map<Long, String> sourceNameCache = new java.util.concurrent.ConcurrentHashMap<>();

    @PersistenceContext private EntityManager em;

    public RadarApplicationService(
        RadarService radarService,
        RadarGenerationService generation,
        RadarRepository radarRepo,
        RadarThemeRepository themeRepo,
        RadarThemeItemRepository themeItemRepo,
        SourceItemRepository sourceItemRepo,
        SourceRepository sourceRepo,
        UserInterestService interests
    ) {
        this.radarService = radarService;
        this.generation = generation;
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.themeItemRepo = themeItemRepo;
        this.sourceItemRepo = sourceItemRepo;
        this.sourceRepo = sourceRepo;
        this.interests = interests;
    }
```

Update the `RadarItemDTO` construction in the `get()` method (line 89). Change:

```java
                itemDtos.add(new RadarItemDTO(si.getId(), si.getTitle(), si.getUrl(), si.getAuthor()));
```

to:

```java
                itemDtos.add(new RadarItemDTO(si.getId(), si.getTitle(), si.getDescription(), si.getUrl(), si.getAuthor(), resolveSourceName(si.getSourceId())));
```

Add the helper method at the bottom of the class (before the closing `}`):

```java
    private String resolveSourceName(Long sourceId) {
        return sourceNameCache.computeIfAbsent(sourceId, id ->
            sourceRepo.findById(id).map(Source::getCode).orElse("unknown"));
    }
```

Also update the `toThemeMcp` method's citation construction. Change:

```java
            .map(si -> new CitationMcpDTO(si.getTitle(), si.getUrl()))
```

to:

```java
            .map(si -> new CitationMcpDTO(si.getTitle(), si.getDescription(), si.getUrl()))
```

- [ ] **Step 3: Update CitationMcpDTO**

Replace `backend/src/main/java/com/devradar/mcp/dto/CitationMcpDTO.java`:

```java
package com.devradar.mcp.dto;

public record CitationMcpDTO(String title, String description, String url) {}
```

- [ ] **Step 4: Verify backend compiles**

```bash
cd backend && mvn -DskipTests compile
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/web/rest/dto/RadarItemDTO.java backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java backend/src/main/java/com/devradar/mcp/dto/CitationMcpDTO.java
git commit -m "feat(backend): add description and sourceName to RadarItemDTO and CitationMcpDTO"
```

---

### Task 9: Frontend — update types, create SourceCard, update ThemeCard

**Files:**
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/components/SourceCard.tsx`
- Modify: `frontend/src/components/ThemeCard.tsx`
- Delete: `frontend/src/components/CitationPill.tsx`

- [ ] **Step 1: Update RadarItem type**

In `frontend/src/api/types.ts`, replace the `RadarItem` interface:

```typescript
export interface RadarItem {
  id: number;
  title: string;
  url: string;
  author: string | null;
}
```

with:

```typescript
export interface RadarItem {
  id: number;
  title: string;
  description: string | null;
  url: string;
  author: string | null;
  sourceName: string;
}
```

- [ ] **Step 2: Create SourceCard component**

Create `frontend/src/components/SourceCard.tsx`:

```typescript
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { monoStack } from "../theme";
import type { RadarItem } from "../api/types";

const SOURCE_LABELS: Record<string, string> = {
  HN: "HN",
  GH_TRENDING: "GitHub",
  GHSA: "GHSA",
};

function sourceBadgeColor(sourceName: string): string {
  switch (sourceName) {
    case "GHSA": return "rgba(179,38,30,0.08)";
    default: return "rgba(45,42,38,0.05)";
  }
}

interface SourceCardProps {
  item: RadarItem;
}

export function SourceCard({ item }: SourceCardProps) {
  const label = SOURCE_LABELS[item.sourceName] ?? item.sourceName;

  return (
    <Box
      component="a"
      href={item.url}
      target="_blank"
      rel="noreferrer noopener"
      sx={{
        display: "flex",
        gap: "12px",
        alignItems: "flex-start",
        py: "10px",
        px: "12px",
        textDecoration: "none",
        borderBottom: "1px solid",
        borderColor: "divider",
        "&:hover": { bgcolor: "rgba(45,42,38,0.02)" },
        transition: "background 120ms",
      }}
    >
      <Box
        sx={{
          flexShrink: 0,
          mt: "2px",
          px: "6px",
          py: "2px",
          borderRadius: "4px",
          bgcolor: sourceBadgeColor(item.sourceName),
          fontFamily: monoStack,
          fontSize: "0.6875rem",
          fontWeight: 500,
          lineHeight: "16px",
          color: "text.secondary",
          whiteSpace: "nowrap",
        }}
      >
        {label}
      </Box>
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Typography
          sx={{
            fontSize: "0.875rem",
            lineHeight: "20px",
            fontWeight: 500,
            color: "text.primary",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {item.title}
        </Typography>
        {item.description && (
          <Typography
            sx={{
              fontSize: "0.8125rem",
              lineHeight: "18px",
              color: "text.secondary",
              mt: "2px",
              overflow: "hidden",
              textOverflow: "ellipsis",
              display: "-webkit-box",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical",
            }}
          >
            {item.description}
          </Typography>
        )}
      </Box>
    </Box>
  );
}
```

- [ ] **Step 3: Update ThemeCard to use SourceCard instead of CitationPill**

Replace `frontend/src/components/ThemeCard.tsx`:

```typescript
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { keyframes } from "@mui/system";
import { serifStack } from "../theme";
import { SourceCard } from "./SourceCard";
import type { RadarTheme } from "../api/types";

const fadeIn = keyframes`
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
`;

export interface ThemeCardProps {
  theme: RadarTheme;
}

export function ThemeCard({ theme }: ThemeCardProps) {
  return (
    <Box component="article" sx={{ mb: 6, animation: `${fadeIn} 400ms ease-out` }}>
      <Typography
        component="h2"
        sx={{
          m: 0,
          mb: 2,
          fontSize: "1.5rem",
          lineHeight: "32px",
          fontWeight: 500,
          letterSpacing: "-0.01em",
          color: "text.primary",
        }}
      >
        {theme.title}
      </Typography>

      <Box
        sx={{
          fontFamily: serifStack,
          fontSize: "1.0625rem",
          lineHeight: "28px",
          color: "text.primary",
          whiteSpace: "pre-line",
          textWrap: "pretty",
        }}
      >
        {theme.summary}
      </Box>

      {theme.items.length > 0 && (
        <Box
          sx={{
            mt: 3,
            border: "1px solid",
            borderColor: "divider",
            borderRadius: 2,
            overflow: "hidden",
            "& > a:last-child": { borderBottom: "none" },
          }}
        >
          {theme.items.map((item) => (
            <SourceCard key={item.id} item={item} />
          ))}
        </Box>
      )}
    </Box>
  );
}
```

- [ ] **Step 4: Delete CitationPill**

Delete `frontend/src/components/CitationPill.tsx`.

- [ ] **Step 5: Check for any remaining CitationPill imports**

Search for `CitationPill` in frontend source and remove any remaining imports.

- [ ] **Step 6: Verify TypeScript compiles and lint passes**

```bash
cd frontend && npx tsc --noEmit && npm run lint
```

- [ ] **Step 7: Commit**

```bash
git rm frontend/src/components/CitationPill.tsx
git add frontend/src/api/types.ts frontend/src/components/SourceCard.tsx frontend/src/components/ThemeCard.tsx
git commit -m "feat(frontend): replace CitationPill with SourceCard showing title, description, and source badge"
```

---

### Task 10: Backend test suite pass + full verification

- [ ] **Step 1: Run full backend test suite**

```bash
cd backend && mvn test
```

Fix any failures. Common issues:
- Integration tests creating FetchedItem with old constructor (check `IngestionServiceIT.java`)
- Any test referencing ScoreRelevanceTool

- [ ] **Step 2: Run full frontend checks**

```bash
cd frontend && npx tsc --noEmit && npm run lint
```

- [ ] **Step 3: Start backend and verify migration runs**

```bash
cd backend && DB_HOST_PORT=3307 mvn spring-boot:run -Dspring-boot.run.profiles=gemini
```

Check logs for "014-add-description-column" migration success.

- [ ] **Step 4: Trigger ingestion and verify descriptions are populated**

Wait for the scheduled ingestion jobs to run (HN: 30s initial delay, GHSA: 90s), then query:

```bash
curl -s "http://localhost:8080/api/observability/summary" | python3 -m json.tool
```

Check MySQL directly for descriptions:

```bash
mysql -u root -p -P 3307 -e "SELECT id, title, LEFT(description, 80) as descr FROM devradar.source_items ORDER BY id DESC LIMIT 5;"
```

- [ ] **Step 5: Generate a radar and verify quality**

Log in, generate a new radar, check the output for:
- Specific theme titles (not generic categories)
- Source cards with descriptions visible
- Source badges showing HN/GitHub/GHSA

- [ ] **Step 6: Commit any test fixes**

```bash
git add -A && git commit -m "fix(backend): update tests for description field changes"
```
