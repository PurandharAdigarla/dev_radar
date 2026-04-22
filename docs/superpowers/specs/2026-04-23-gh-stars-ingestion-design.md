# GitHub Stars Release Ingestion — Design Spec

## Problem

The curated `GH_RELEASES` repo list covers popular ecosystem repos, but users care about niche repos they've starred — libraries they use in their own projects. Currently there's no way to personalize the release feed without manually adding repos to the config.

## Solution

At radar generation time, fetch the user's GitHub starred repos (via their stored OAuth token), fetch recent releases for each, and ingest them into the global `SourceItem` pool under a new `GH_STARS` source. The items then flow through the normal candidate filtering and AI orchestration.

## Architecture

### Trigger: On-Demand at Radar Creation

Unlike the scheduled `GH_RELEASES` ingestor, starred-repo ingestion runs **during `RadarApplicationService.createForCurrentUser()`**, before candidate pre-filtering. This ensures starred-repo releases are available as candidates for the radar being generated.

Flow:
1. `createForCurrentUser()` called
2. Check if user has linked GitHub (`UserGithubIdentityRepository.findById(uid)`)
3. If yes → decrypt token → call `GitHubStarsReleaseFetcher.fetchAndIngest(token)`
4. Proceed with normal `preFilterCandidates()` + `runGeneration()`

### New Files

| File | Purpose |
|------|---------|
| `GitHubStarsReleaseFetcher.java` | Service: list starred repos → fetch releases via `GitHubReleasesClient` → ingest via `IngestionService` |
| `016-seed-gh-stars-source.xml` | Liquibase: seed `GH_STARS` source row |

### Modified Files

| File | Change |
|------|--------|
| `GitHubApiClient.java` | Add `listStarred(String token)` method |
| `RadarApplicationService.java` | Call `GitHubStarsReleaseFetcher` before pre-filtering candidates |
| `db.changelog-master.xml` | Add include for `016-seed-gh-stars-source.xml` |
| `application-test.yml` | No change needed — fetcher is not scheduled |

### GitHubApiClient — New Method

```java
public List<String> listStarred(String token) {
    // GET /user/starred?per_page=100&sort=updated&direction=desc
    // Returns list of "owner/repo" strings (full_name)
    // Caps at 100 repos (first page only)
}
```

Returns `List<String>` (repo full names) rather than a rich DTO — we only need the name to pass to `GitHubReleasesClient.fetchReleases()`.

### GitHubStarsReleaseFetcher

```java
@Service
public class GitHubStarsReleaseFetcher {

    private final GitHubApiClient github;
    private final GitHubReleasesClient releasesClient;
    private final IngestionService ingestion;
    private final SourceRepository sourceRepo;

    public void fetchAndIngest(String token) {
        Source src = sourceRepo.findByCode("GH_STARS").orElse(null);
        if (src == null || !src.isActive()) return;

        List<String> starredRepos = github.listStarred(token);

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

Key behaviors:
- **Topics are empty** (`List.of()`) — TagExtractor will match tags from title/description content, which is fine since release titles contain the project name
- **Errors are debug-level** — many starred repos won't have releases (forks, docs repos, etc.), this is expected noise
- **Reuses `GitHubReleasesClient`** — same release JSON → FetchedItem mapping, same draft/prerelease filtering
- **Dedup handled by existing IngestionService** — if a starred repo overlaps with the curated list, the `(source_id, external_id)` constraint prevents duplicates within `GH_STARS`, and the orchestrator can see items from both sources

### RadarApplicationService — Modification

In `createForCurrentUser()`, add starred-repo fetch before pre-filtering:

```java
public RadarSummaryDTO createForCurrentUser() {
    Long uid = SecurityUtils.getCurrentUserId();
    if (uid == null) throw new UserNotAuthenticatedException();

    // Fetch starred-repo releases if user has linked GitHub
    starsFetcher.tryFetchForUser(uid);

    List<InterestTag> userTags = interests.findInterestsForUser(uid);
    List<String> slugs = userTags.stream().map(InterestTag::getSlug).toList();
    List<Long> candidateIds = preFilterCandidates(slugs);

    Radar created = radarService.createPending(uid);
    generation.runGeneration(created.getId(), uid, slugs, candidateIds);
    return summary(created);
}
```

The `tryFetchForUser(uid)` method on the fetcher handles the GitHub identity lookup and token decryption internally — keeps the application service clean.

### Liquibase Migration

`016-seed-gh-stars-source.xml` — insert into `sources`:
- `code`: `GH_STARS`
- `display_name`: `GitHub Stars`
- `active`: `true`
- `fetch_interval_minutes`: `0` (on-demand, not scheduled)

### Rate Limiting

User's authenticated token gets 5000 requests/hour. With 100 starred repos × 1 release call each = 100 API calls per radar generation. Well within limits even if the user generates multiple radars.

### What Doesn't Change

- `IngestionService` — unchanged, processes FetchedItem as-is
- `GitHubReleasesClient` — unchanged, reused for release fetching
- `TagExtractor` — unchanged, extracts tags from release title/description
- `SourceItem` — no schema change, no userId column
- Frontend — already renders `sourceName`, will show `GH_STARS` badge

### Performance

Starred-repo fetch adds latency to radar creation. With 100 repos:
- `GET /user/starred`: ~200ms (one call)
- `GET /repos/{repo}/releases`: ~100ms each × 100 repos = ~10s sequential

To keep it reasonable, we process repos sequentially (simple, within rate limits). 10s added to radar creation is acceptable since generation itself takes 20-30s. If this becomes a bottleneck, we could parallelize with a small thread pool later.

## Testing

- `GitHubApiClientTest` — add test for `listStarred()` with WireMock
- `GitHubStarsReleaseFetcherTest` — unit test with mocked GitHubApiClient, GitHubReleasesClient, IngestionService
- `RadarApplicationServiceTest` — verify `tryFetchForUser()` is called before pre-filtering
