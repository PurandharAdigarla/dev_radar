# GitHub Stars Release Ingestion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fetch releases from a user's GitHub-starred repos at radar creation time, making radar citations personalized without manual curation.

**Architecture:** New `GH_STARS` source seeded via Liquibase. `GitHubApiClient` gains a `listStarred()` method. New `GitHubStarsReleaseFetcher` service handles the starred-repo → release → ingest pipeline. `RadarApplicationService.createForCurrentUser()` calls the fetcher before pre-filtering candidates.

**Tech Stack:** Java 21, Spring Boot 3.5, JUnit 5, WireMock, Mockito, Liquibase, MySQL 8

---

### Task 1: Liquibase Migration — Seed GH_STARS Source

**Files:**
- Create: `backend/src/main/resources/db/changelog/016-seed-gh-stars-source.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create the Liquibase changeset**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="016-seed-gh-stars-source" author="devradar">
        <sql>
INSERT INTO sources (code, display_name, active, fetch_interval_minutes, created_at) VALUES
  ('GH_STARS', 'GitHub Stars', true, 0, NOW());
        </sql>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: Add include to db.changelog-master.xml**

Add after the `015-seed-gh-releases-source.xml` include:

```xml
<include file="db/changelog/016-seed-gh-stars-source.xml"/>
```

- [ ] **Step 3: Run the build to verify migration applies**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS, Liquibase applies changeset 016

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/016-seed-gh-stars-source.xml backend/src/main/resources/db/changelog/db.changelog-master.xml
git commit -m "feat(backend): seed GH_STARS source via Liquibase migration"
```

---

### Task 2: GitHubApiClient.listStarred() — TDD

**Files:**
- Modify: `backend/src/main/java/com/devradar/github/GitHubApiClient.java`
- Modify: `backend/src/test/java/com/devradar/github/GitHubApiClientTest.java`

- [ ] **Step 1: Write the failing test**

Add to `GitHubApiClientTest.java`:

```java
@Test
void listStarred_returnsRepoFullNames() {
    wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/user/starred"))
        .withQueryParam("per_page", WireMock.equalTo("100"))
        .withQueryParam("sort", WireMock.equalTo("updated"))
        .withQueryParam("direction", WireMock.equalTo("desc"))
        .willReturn(WireMock.okJson("""
            [
              {"full_name":"alice/react-app"},
              {"full_name":"bob/spring-starter"}
            ]
            """)));

    List<String> starred = client.listStarred("my-token");

    assertThat(starred).containsExactly("alice/react-app", "bob/spring-starter");
    wm.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/user/starred"))
        .withHeader("Authorization", WireMock.equalTo("Bearer my-token")));
}

@Test
void listStarred_returnsEmptyListWhenNoStars() {
    wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/user/starred"))
        .willReturn(WireMock.okJson("[]")));

    List<String> starred = client.listStarred("my-token");

    assertThat(starred).isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=GitHubApiClientTest#listStarred_returnsRepoFullNames -Dtest=GitHubApiClientTest#listStarred_returnsEmptyListWhenNoStars`
Expected: FAIL — `listStarred` method does not exist

- [ ] **Step 3: Implement listStarred()**

Add to `GitHubApiClient.java` after the `listRepos` method:

```java
public List<String> listStarred(String token) {
    JsonNode arr = http.get()
        .uri(uri -> uri.path("/user/starred")
            .queryParam("per_page", "100")
            .queryParam("sort", "updated")
            .queryParam("direction", "desc")
            .build())
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/vnd.github+json")
        .retrieve().body(JsonNode.class);
    List<String> out = new ArrayList<>();
    if (arr != null && arr.isArray()) {
        for (JsonNode r : arr) {
            String name = r.path("full_name").asText(null);
            if (name != null) out.add(name);
        }
    }
    return out;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl . -Dtest=GitHubApiClientTest`
Expected: All tests pass (existing + 2 new)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/github/GitHubApiClient.java backend/src/test/java/com/devradar/github/GitHubApiClientTest.java
git commit -m "feat(backend): add listStarred() to GitHubApiClient"
```

---

### Task 3: GitHubStarsReleaseFetcher Service — TDD

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/job/GitHubStarsReleaseFetcher.java`
- Create: `backend/src/test/java/com/devradar/ingest/job/GitHubStarsReleaseFetcherTest.java`

- [ ] **Step 1: Write the failing tests**

Create `GitHubStarsReleaseFetcherTest.java`:

```java
package com.devradar.ingest.job;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.Source;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.GitHubReleasesClient;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitHubStarsReleaseFetcherTest {

    GitHubApiClient github;
    GitHubReleasesClient releasesClient;
    IngestionService ingestion;
    SourceRepository sources;
    UserGithubIdentityRepository identityRepo;
    TokenEncryptor encryptor;
    GitHubStarsReleaseFetcher fetcher;

    @BeforeEach
    void setup() {
        github = mock(GitHubApiClient.class);
        releasesClient = mock(GitHubReleasesClient.class);
        ingestion = mock(IngestionService.class);
        sources = mock(SourceRepository.class);
        identityRepo = mock(UserGithubIdentityRepository.class);
        encryptor = mock(TokenEncryptor.class);
        fetcher = new GitHubStarsReleaseFetcher(github, releasesClient, ingestion, sources, identityRepo, encryptor);
    }

    @Test
    void tryFetchForUser_fetchesReleasesForStarredRepos() {
        UserGithubIdentity identity = new UserGithubIdentity();
        identity.setUserId(7L);
        identity.setAccessTokenEncrypted("encrypted-token");
        when(identityRepo.findById(7L)).thenReturn(Optional.of(identity));
        when(encryptor.decrypt("encrypted-token")).thenReturn("ghp_real_token");

        Source src = new Source();
        src.setCode("GH_STARS");
        src.setActive(true);
        when(sources.findByCode("GH_STARS")).thenReturn(Optional.of(src));

        when(github.listStarred("ghp_real_token")).thenReturn(List.of("alice/lib-a", "bob/lib-b"));

        var item = new FetchedItem("alice/lib-a:v1.0.0", "https://example.com", "lib-a v1.0.0",
            "notes", "alice", Instant.now(), null, List.of());
        when(releasesClient.fetchReleases("alice/lib-a", List.of())).thenReturn(List.of(item));
        when(releasesClient.fetchReleases("bob/lib-b", List.of())).thenReturn(List.of());

        fetcher.tryFetchForUser(7L);

        verify(releasesClient).fetchReleases("alice/lib-a", List.of());
        verify(releasesClient).fetchReleases("bob/lib-b", List.of());
        verify(ingestion, times(2)).ingestBatch(eq(src), any());
    }

    @Test
    void tryFetchForUser_skipsWhenNoGithubIdentity() {
        when(identityRepo.findById(99L)).thenReturn(Optional.empty());

        fetcher.tryFetchForUser(99L);

        verifyNoInteractions(github);
        verifyNoInteractions(releasesClient);
        verifyNoInteractions(ingestion);
    }

    @Test
    void tryFetchForUser_skipsWhenSourceInactive() {
        UserGithubIdentity identity = new UserGithubIdentity();
        identity.setUserId(7L);
        identity.setAccessTokenEncrypted("encrypted-token");
        when(identityRepo.findById(7L)).thenReturn(Optional.of(identity));
        when(encryptor.decrypt("encrypted-token")).thenReturn("ghp_real_token");

        Source src = new Source();
        src.setActive(false);
        when(sources.findByCode("GH_STARS")).thenReturn(Optional.of(src));

        fetcher.tryFetchForUser(7L);

        verifyNoInteractions(github);
        verifyNoInteractions(releasesClient);
    }

    @Test
    void tryFetchForUser_continuesOnPerRepoFailure() {
        UserGithubIdentity identity = new UserGithubIdentity();
        identity.setUserId(7L);
        identity.setAccessTokenEncrypted("enc");
        when(identityRepo.findById(7L)).thenReturn(Optional.of(identity));
        when(encryptor.decrypt("enc")).thenReturn("token");

        Source src = new Source();
        src.setCode("GH_STARS");
        src.setActive(true);
        when(sources.findByCode("GH_STARS")).thenReturn(Optional.of(src));

        when(github.listStarred("token")).thenReturn(List.of("bad/repo", "good/repo"));
        when(releasesClient.fetchReleases("bad/repo", List.of())).thenThrow(new RuntimeException("API error"));
        when(releasesClient.fetchReleases("good/repo", List.of())).thenReturn(List.of());

        fetcher.tryFetchForUser(7L);

        verify(releasesClient).fetchReleases("good/repo", List.of());
        verify(ingestion).ingestBatch(eq(src), any());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -pl . -Dtest=GitHubStarsReleaseFetcherTest`
Expected: FAIL — `GitHubStarsReleaseFetcher` class does not exist

- [ ] **Step 3: Implement GitHubStarsReleaseFetcher**

Create `backend/src/main/java/com/devradar/ingest/job/GitHubStarsReleaseFetcher.java`:

```java
package com.devradar.ingest.job;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.Source;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.GitHubReleasesClient;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GitHubStarsReleaseFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubStarsReleaseFetcher.class);
    static final String CODE = "GH_STARS";

    private final GitHubApiClient github;
    private final GitHubReleasesClient releasesClient;
    private final IngestionService ingestion;
    private final SourceRepository sourceRepo;
    private final UserGithubIdentityRepository identityRepo;
    private final TokenEncryptor encryptor;

    public GitHubStarsReleaseFetcher(
        GitHubApiClient github,
        GitHubReleasesClient releasesClient,
        IngestionService ingestion,
        SourceRepository sourceRepo,
        UserGithubIdentityRepository identityRepo,
        TokenEncryptor encryptor
    ) {
        this.github = github;
        this.releasesClient = releasesClient;
        this.ingestion = ingestion;
        this.sourceRepo = sourceRepo;
        this.identityRepo = identityRepo;
        this.encryptor = encryptor;
    }

    public void tryFetchForUser(Long userId) {
        UserGithubIdentity identity = identityRepo.findById(userId).orElse(null);
        if (identity == null) return;

        String token = encryptor.decrypt(identity.getAccessTokenEncrypted());

        Source src = sourceRepo.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) return;

        List<String> starredRepos = github.listStarred(token);
        LOG.info("GH_STARS userId={} starred={}", userId, starredRepos.size());

        for (String repo : starredRepos) {
            try {
                List<FetchedItem> releases = releasesClient.fetchReleases(repo, List.of());
                ingestion.ingestBatch(src, releases);
            } catch (Exception e) {
                LOG.debug("Starred repo release fetch skipped repo={}: {}", repo, e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl . -Dtest=GitHubStarsReleaseFetcherTest`
Expected: All 4 tests pass

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/ingest/job/GitHubStarsReleaseFetcher.java backend/src/test/java/com/devradar/ingest/job/GitHubStarsReleaseFetcherTest.java
git commit -m "feat(backend): add GitHubStarsReleaseFetcher service"
```

---

### Task 4: Wire Fetcher into RadarApplicationService

**Files:**
- Modify: `backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java`
- Modify: `backend/src/test/java/com/devradar/radar/application/RadarApplicationServiceTest.java` (if it exists; create if not)

- [ ] **Step 1: Add the fetcher dependency to RadarApplicationService**

In `RadarApplicationService.java`, add the import and field:

```java
import com.devradar.ingest.job.GitHubStarsReleaseFetcher;
```

Add field:

```java
private final GitHubStarsReleaseFetcher starsFetcher;
```

Update the constructor to accept the new dependency — add `GitHubStarsReleaseFetcher starsFetcher` as the last constructor parameter and assign `this.starsFetcher = starsFetcher;`.

- [ ] **Step 2: Add the tryFetchForUser call to createForCurrentUser()**

In `createForCurrentUser()`, add the starred-repo fetch call after the null check and before `interests.findInterestsForUser()`:

```java
public RadarSummaryDTO createForCurrentUser() {
    Long uid = SecurityUtils.getCurrentUserId();
    if (uid == null) throw new UserNotAuthenticatedException();

    starsFetcher.tryFetchForUser(uid);

    List<InterestTag> userTags = interests.findInterestsForUser(uid);
    List<String> slugs = userTags.stream().map(InterestTag::getSlug).toList();
    List<Long> candidateIds = preFilterCandidates(slugs);

    Radar created = radarService.createPending(uid);
    generation.runGeneration(created.getId(), uid, slugs, candidateIds);
    return summary(created);
}
```

- [ ] **Step 3: Run the full test suite to verify nothing broke**

Run: `cd backend && mvn test`
Expected: All tests pass. The `RadarApplicationService` constructor change may require updating existing test mocks if `RadarApplicationServiceTest` exists.

- [ ] **Step 4: If test compilation fails due to constructor change, fix the test**

If there is an existing `RadarApplicationServiceTest`, add a mocked `GitHubStarsReleaseFetcher` to its setup and constructor call.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java
git add -A backend/src/test/java/com/devradar/radar/application/
git commit -m "feat(backend): call GitHubStarsReleaseFetcher during radar creation"
```

---

### Task 5: Full Integration Verification

**Files:** None (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `cd backend && mvn test`
Expected: All tests pass (existing + new tests from Tasks 2-4)

- [ ] **Step 2: Compile the full project**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Verify the Liquibase migration applies**

Start the application briefly to verify the migration runs:

Run: `cd backend && DB_HOST_PORT=3307 mvn spring-boot:run` (stop after startup completes)
Expected: Liquibase applies changeset `016-seed-gh-stars-source`, application starts successfully

- [ ] **Step 4: Verify GH_STARS source exists in the database**

Run: `mysql -h 127.0.0.1 -P 3307 -u devradar -pdevradar devradar -e "SELECT * FROM sources WHERE code='GH_STARS'"`
Expected: One row with `code=GH_STARS`, `display_name=GitHub Stars`, `active=1`, `fetch_interval_minutes=0`
