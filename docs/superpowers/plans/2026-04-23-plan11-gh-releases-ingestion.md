# GitHub Releases Ingestion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `GH_RELEASES` ingestion source that polls the GitHub Releases API for curated repos, producing actionable citations with version numbers, changelogs, and release page URLs.

**Architecture:** New client (`GitHubReleasesClient`) calls `GET /repos/{owner}/{repo}/releases?per_page=3` for each repo in a curated config list. New ingestor job (`GitHubReleasesIngestor`) runs every 2 hours, parsing the config, iterating repos, and feeding `FetchedItem`s into the existing `IngestionService`. A Liquibase changeset seeds the `GH_RELEASES` source row.

**Tech Stack:** Java 21, Spring Boot 3.5, RestClient, Jackson, WireMock (tests), JUnit 5, Liquibase

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/com/devradar/ingest/client/GitHubReleasesClient.java` | REST client: fetch releases for one repo, map JSON → `FetchedItem` |
| Create | `src/main/java/com/devradar/ingest/job/GitHubReleasesIngestor.java` | Scheduled job: parse config, iterate repos, call client → `IngestionService` |
| Create | `src/main/resources/db/changelog/015-seed-gh-releases-source.xml` | Liquibase: seed `GH_RELEASES` row in `sources` table |
| Modify | `src/main/resources/db/changelog/db.changelog-master.xml` | Add include for `015-seed-gh-releases-source.xml` |
| Modify | `src/main/resources/application.yml` | Add `devradar.ingest.gh-releases.*` config properties |
| Modify | `src/test/resources/application-test.yml` | Add scheduling override to disable in tests |
| Create | `src/test/java/com/devradar/ingest/client/GitHubReleasesClientTest.java` | WireMock test: verify JSON → `FetchedItem` mapping |
| Create | `src/test/java/com/devradar/ingest/job/GitHubReleasesIngestorTest.java` | Unit test: config parsing, per-repo error handling |

---

### Task 1: Liquibase Migration — Seed `GH_RELEASES` Source

**Files:**
- Create: `src/main/resources/db/changelog/015-seed-gh-releases-source.xml`
- Modify: `src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create the changeset file**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="015-seed-gh-releases-source" author="devradar">
        <sql>
INSERT INTO sources (code, display_name, active, fetch_interval_minutes, created_at) VALUES
  ('GH_RELEASES', 'GitHub Releases', true, 120, NOW());
        </sql>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: Add include to master changelog**

In `db.changelog-master.xml`, add after the line including `014-add-description-column.xml`:

```xml
    <include file="db/changelog/015-seed-gh-releases-source.xml"/>
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && mvn -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/015-seed-gh-releases-source.xml backend/src/main/resources/db/changelog/db.changelog-master.xml
git commit -m "chore(backend): seed GH_RELEASES source in Liquibase"
```

---

### Task 2: GitHubReleasesClient — REST Client with Tests (TDD)

**Files:**
- Create: `src/test/java/com/devradar/ingest/client/GitHubReleasesClientTest.java`
- Create: `src/main/java/com/devradar/ingest/client/GitHubReleasesClient.java`

- [ ] **Step 1: Write the test — happy path**

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

class GitHubReleasesClientTest {

    WireMockServer wm;
    GitHubReleasesClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new GitHubReleasesClient(RestClient.builder(), "http://localhost:" + wm.port());
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void fetchReleases_returnsParsedReleases() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/facebook/react/releases"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "tag_name": "v19.1.0",
                    "name": "Compiler improvements",
                    "html_url": "https://github.com/facebook/react/releases/tag/v19.1.0",
                    "body": "## What's Changed\\n- Improved compiler performance\\n- Fixed hydration bugs",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-20T10:00:00Z",
                    "author": { "login": "gaearon" }
                  },
                  {
                    "tag_name": "v19.0.0",
                    "name": "v19.0.0",
                    "html_url": "https://github.com/facebook/react/releases/tag/v19.0.0",
                    "body": "Major release",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-10T10:00:00Z",
                    "author": { "login": "gaearon" }
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchReleases("facebook/react", List.of("react", "frontend"));

        assertThat(items).hasSize(2);

        FetchedItem first = items.get(0);
        assertThat(first.externalId()).isEqualTo("facebook/react:v19.1.0");
        assertThat(first.url()).isEqualTo("https://github.com/facebook/react/releases/tag/v19.1.0");
        assertThat(first.title()).isEqualTo("react v19.1.0 — Compiler improvements");
        assertThat(first.description()).contains("Improved compiler performance");
        assertThat(first.author()).isEqualTo("gaearon");
        assertThat(first.topics()).containsExactlyInAnyOrder("react", "frontend");
        assertThat(first.rawPayload()).isNotNull();
    }

    @Test
    void fetchReleases_skipsDraftsAndPrereleases() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/owner/repo/releases"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "tag_name": "v2.0.0-beta.1",
                    "name": "Beta",
                    "html_url": "https://github.com/owner/repo/releases/tag/v2.0.0-beta.1",
                    "body": "Beta release",
                    "draft": false,
                    "prerelease": true,
                    "published_at": "2026-04-20T10:00:00Z",
                    "author": { "login": "dev" }
                  },
                  {
                    "tag_name": "v2.0.0-draft",
                    "name": "Draft",
                    "html_url": "https://github.com/owner/repo/releases/tag/v2.0.0-draft",
                    "body": "Draft release",
                    "draft": true,
                    "prerelease": false,
                    "published_at": "2026-04-20T10:00:00Z",
                    "author": { "login": "dev" }
                  },
                  {
                    "tag_name": "v1.5.0",
                    "name": "v1.5.0",
                    "html_url": "https://github.com/owner/repo/releases/tag/v1.5.0",
                    "body": "Stable release",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-18T10:00:00Z",
                    "author": { "login": "dev" }
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchReleases("owner/repo", List.of("tool"));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).externalId()).isEqualTo("owner/repo:v1.5.0");
    }

    @Test
    void fetchReleases_titleUsesTagNameWhenNameMatches() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/owner/repo/releases"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "tag_name": "v3.0.0",
                    "name": "v3.0.0",
                    "html_url": "https://github.com/owner/repo/releases/tag/v3.0.0",
                    "body": "Release notes",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-20T10:00:00Z",
                    "author": { "login": "dev" }
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchReleases("owner/repo", List.of());

        assertThat(items.get(0).title()).isEqualTo("repo v3.0.0");
    }

    @Test
    void fetchReleases_truncatesLongBody() {
        String longBody = "x".repeat(3000);
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/owner/repo/releases"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "tag_name": "v1.0.0",
                    "name": "v1.0.0",
                    "html_url": "https://github.com/owner/repo/releases/tag/v1.0.0",
                    "body": "%s",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-20T10:00:00Z",
                    "author": { "login": "dev" }
                  }
                ]
                """.formatted(longBody))));

        List<FetchedItem> items = client.fetchReleases("owner/repo", List.of());

        assertThat(items.get(0).description()).hasSize(2000);
    }

    @Test
    void fetchReleases_returnsEmptyListOnNullOrEmptyResponse() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/owner/repo/releases"))
            .willReturn(WireMock.okJson("[]")));

        List<FetchedItem> items = client.fetchReleases("owner/repo", List.of());

        assertThat(items).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -pl . -Dtest=GitHubReleasesClientTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation failure — `GitHubReleasesClient` doesn't exist yet

- [ ] **Step 3: Implement GitHubReleasesClient**

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
public class GitHubReleasesClient {

    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final RestClient http;

    public GitHubReleasesClient(RestClient.Builder builder,
                                @Value("${devradar.gh-releases.base-url:https://api.github.com}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public List<FetchedItem> fetchReleases(String repoFullName, List<String> topics) {
        JsonNode arr = http.get()
            .uri(uri -> uri.path("/repos/" + repoFullName + "/releases")
                .queryParam("per_page", "3").build())
            .retrieve()
            .body(JsonNode.class);

        List<FetchedItem> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;

        String repoShortName = repoFullName.contains("/")
            ? repoFullName.substring(repoFullName.lastIndexOf('/') + 1)
            : repoFullName;

        for (JsonNode rel : arr) {
            if (rel.path("draft").asBoolean(false)) continue;
            if (rel.path("prerelease").asBoolean(false)) continue;

            String tagName = textOrNull(rel, "tag_name");
            String htmlUrl = textOrNull(rel, "html_url");
            String publishedAt = textOrNull(rel, "published_at");
            if (tagName == null || htmlUrl == null) continue;

            String releaseName = textOrNull(rel, "name");
            String title = buildTitle(repoShortName, tagName, releaseName);
            String body = textOrNull(rel, "body");
            String description = body != null && body.length() > MAX_DESCRIPTION_LENGTH
                ? body.substring(0, MAX_DESCRIPTION_LENGTH)
                : body;

            String author = rel.path("author").path("login").asText(null);
            Instant posted = publishedAt != null ? Instant.parse(publishedAt) : Instant.now();
            String externalId = repoFullName + ":" + tagName;

            out.add(new FetchedItem(externalId, htmlUrl, title, description, author, posted, rel.toString(), topics));
        }
        return out;
    }

    private static String buildTitle(String repoShortName, String tagName, String releaseName) {
        String base = repoShortName + " " + tagName;
        if (releaseName != null && !releaseName.isBlank()
                && !releaseName.equals(tagName) && !releaseName.equals("v" + tagName)
                && !tagName.equals("v" + releaseName)) {
            base += " — " + releaseName;
        }
        return base;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl . -Dtest=GitHubReleasesClientTest`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/client/GitHubReleasesClient.java backend/src/test/java/com/devradar/ingest/client/GitHubReleasesClientTest.java
git commit -m "feat(backend): add GitHubReleasesClient with WireMock tests"
```

---

### Task 3: GitHubReleasesIngestor — Scheduled Job with Tests (TDD)

**Files:**
- Create: `src/test/java/com/devradar/ingest/job/GitHubReleasesIngestorTest.java`
- Create: `src/main/java/com/devradar/ingest/job/GitHubReleasesIngestor.java`

- [ ] **Step 1: Write the test**

```java
package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.GitHubReleasesClient;
import com.devradar.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitHubReleasesIngestorTest {

    GitHubReleasesClient client;
    IngestionService ingestion;
    SourceRepository sources;

    @BeforeEach
    void setup() {
        client = mock(GitHubReleasesClient.class);
        ingestion = mock(IngestionService.class);
        sources = mock(SourceRepository.class);
    }

    @Test
    void run_parsesConfigAndCallsClientPerRepo() {
        String config = "facebook/react:react,frontend;spring-projects/spring-boot:spring_boot,java";
        var ingestor = new GitHubReleasesIngestor(client, ingestion, sources, config);

        Source src = new Source();
        src.setId(99L);
        src.setCode("GH_RELEASES");
        src.setActive(true);
        when(sources.findByCode("GH_RELEASES")).thenReturn(Optional.of(src));

        var item = new FetchedItem("facebook/react:v19.0.0", "https://example.com", "react v19.0.0",
            "notes", "author", Instant.now(), null, List.of("react", "frontend"));
        when(client.fetchReleases(eq("facebook/react"), eq(List.of("react", "frontend")))).thenReturn(List.of(item));
        when(client.fetchReleases(eq("spring-projects/spring-boot"), eq(List.of("spring_boot", "java")))).thenReturn(List.of());

        ingestor.run();

        verify(client).fetchReleases("facebook/react", List.of("react", "frontend"));
        verify(client).fetchReleases("spring-projects/spring-boot", List.of("spring_boot", "java"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FetchedItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestion, times(2)).ingestBatch(eq(src), captor.capture());

        List<List<FetchedItem>> batches = captor.getAllValues();
        assertThat(batches.get(0)).hasSize(1);
        assertThat(batches.get(1)).isEmpty();
    }

    @Test
    void run_skipsWhenSourceNotActive() {
        var ingestor = new GitHubReleasesIngestor(client, ingestion, sources, "facebook/react:react");

        Source src = new Source();
        src.setActive(false);
        when(sources.findByCode("GH_RELEASES")).thenReturn(Optional.of(src));

        ingestor.run();

        verifyNoInteractions(client);
        verifyNoInteractions(ingestion);
    }

    @Test
    void run_continuesOnPerRepoFailure() {
        String config = "bad/repo:tag;good/repo:tag";
        var ingestor = new GitHubReleasesIngestor(client, ingestion, sources, config);

        Source src = new Source();
        src.setId(1L);
        src.setCode("GH_RELEASES");
        src.setActive(true);
        when(sources.findByCode("GH_RELEASES")).thenReturn(Optional.of(src));

        when(client.fetchReleases(eq("bad/repo"), any())).thenThrow(new RuntimeException("API error"));
        when(client.fetchReleases(eq("good/repo"), any())).thenReturn(List.of());

        ingestor.run();

        verify(client).fetchReleases(eq("good/repo"), any());
        verify(ingestion).ingestBatch(eq(src), any());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -pl . -Dtest=GitHubReleasesIngestorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation failure — `GitHubReleasesIngestor` doesn't exist yet

- [ ] **Step 3: Implement GitHubReleasesIngestor**

```java
package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.GitHubReleasesClient;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
public class GitHubReleasesIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubReleasesIngestor.class);
    static final String CODE = "GH_RELEASES";

    private final GitHubReleasesClient client;
    private final IngestionService ingestion;
    private final SourceRepository sources;
    private final Map<String, List<String>> repoTags;

    public GitHubReleasesIngestor(
        GitHubReleasesClient client,
        IngestionService ingestion,
        SourceRepository sources,
        @Value("${devradar.ingest.gh-releases.repos:}") String reposCsv
    ) {
        this.client = client;
        this.ingestion = ingestion;
        this.sources = sources;
        this.repoTags = parseConfig(reposCsv);
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.gh-releases.fixed-delay-ms:7200000}",
               initialDelayString = "${devradar.ingest.gh-releases.initial-delay-ms:120000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("GH_RELEASES source not active; skipping");
            return;
        }
        for (var entry : repoTags.entrySet()) {
            try {
                var items = client.fetchReleases(entry.getKey(), entry.getValue());
                ingestion.ingestBatch(src, items);
            } catch (Exception e) {
                LOG.warn("GH releases fetch failed repo={}: {}", entry.getKey(), e.toString());
            }
        }
    }

    static Map<String, List<String>> parseConfig(String csv) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (csv == null || csv.isBlank()) return map;
        for (String entry : csv.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":", 2);
            String repo = parts[0].trim();
            List<String> tags = parts.length > 1
                ? Arrays.stream(parts[1].split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();
            if (!repo.isEmpty()) map.put(repo, tags);
        }
        return map;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl . -Dtest=GitHubReleasesIngestorTest`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/job/GitHubReleasesIngestor.java backend/src/test/java/com/devradar/ingest/job/GitHubReleasesIngestorTest.java
git commit -m "feat(backend): add GitHubReleasesIngestor with config parsing and tests"
```

---

### Task 4: Configuration — Wire Everything Together

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

- [ ] **Step 1: Add config to application.yml**

Add the following under the `devradar:` key (create the key if it doesn't exist at the root level — it currently doesn't exist in `application.yml`, but the ingestor reads from `devradar.ingest.gh-releases.*` via `@Value` defaults, so this is for the curated repo list):

```yaml
devradar:
  ingest:
    gh-releases:
      repos: >-
        facebook/react:react,frontend,javascript;
        vercel/next.js:next_js,react,frontend;
        sveltejs/svelte:svelte,frontend;
        vuejs/core:vue,frontend,javascript;
        angular/angular:angular,frontend,typescript;
        spring-projects/spring-boot:spring_boot,java,backend;
        spring-projects/spring-framework:spring_boot,java,backend;
        django/django:django,python,backend;
        tiangolo/fastapi:fastapi,python,backend;
        rails/rails:rails,backend;
        golang/go:go;
        rust-lang/rust:rust;
        JetBrains/kotlin:kotlin;
        apple/swift:swift;
        microsoft/TypeScript:typescript;
        moby/moby:docker,devops;
        kubernetes/kubernetes:kubernetes,devops;
        hashicorp/terraform:terraform,devops;
        redis/redis:redis,database;
        tailwindlabs/tailwindcss:frontend;
        vitejs/vite:frontend,javascript;
        modelcontextprotocol/specification:mcp,ai_tooling;
        langchain-ai/langchain:ai_tooling,llm,python;
        ollama/ollama:ai_tooling,llm
```

Note: Used correct GitHub org/repo names (`tiangolo/fastapi` not `fastapi/timerboard`, `moby/moby` not `docker/docker-ce`). Removed repos that don't use GitHub Releases (mysql, postgres, mongodb, elasticsearch — they publish via other channels).

- [ ] **Step 2: Add test scheduling override**

In `src/test/resources/application-test.yml`, under `devradar.ingest`, add:

```yaml
    gh-releases:
      fixed-delay-ms: 86400000
      initial-delay-ms: 86400000
      repos: ""
```

- [ ] **Step 3: Verify full build compiles**

Run: `cd backend && mvn -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Run all tests to verify nothing is broken**

Run: `cd backend && mvn test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/application.yml backend/src/test/resources/application-test.yml
git commit -m "feat(backend): configure GH_RELEASES curated repo list and test overrides"
```

---

### Task 5: Integration Smoke Test

No new files — verify the full stack works end-to-end.

- [ ] **Step 1: Start the application locally**

Run: `cd backend && DB_HOST_PORT=3307 mvn spring-boot:run`

Check logs for:
- Liquibase applying `015-seed-gh-releases-source` changeset
- No startup errors

- [ ] **Step 2: Verify the source was seeded**

Run (in another terminal): `docker exec backend-mysql-1 mysql -u devradar -pdevradar devradar -e "SELECT * FROM sources WHERE code = 'GH_RELEASES';"`

Expected: One row with `code=GH_RELEASES`, `active=1`, `fetch_interval_minutes=120`

- [ ] **Step 3: Wait for initial ingest (2 minutes) and verify items**

After ~2 minutes, check logs for `ingest source=GH_RELEASES fetched=N inserted=N` lines.

Then verify: `docker exec backend-mysql-1 mysql -u devradar -pdevradar devradar -e "SELECT si.title, si.url, si.posted_at FROM source_items si JOIN sources s ON si.source_id = s.id WHERE s.code = 'GH_RELEASES' ORDER BY si.posted_at DESC LIMIT 10;"`

Expected: Recent releases with specific version numbers in titles and release page URLs (not repo homepages).

- [ ] **Step 4: Stop the server (Ctrl+C)**
