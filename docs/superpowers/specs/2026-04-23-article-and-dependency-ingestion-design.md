# Article & Dependency-Aware Ingestion Design

## Problem

Dev Radar's current data sources (HN trending, GitHub trending, starred repo releases, GHSA advisories) skew toward "what's popular" rather than "what matters to you." Two critical gaps prevent truly personalized, multi-source themes:

1. **No article/blog/documentation ingestion** — trending topics generate buzz across blog posts, official docs, and tutorials, but none of these appear as citations. A theme about "Anthropic agents" should cite the blog post, the repo, AND the docs.
2. **No dependency awareness** — the radar doesn't know what packages the user actually depends on, so it can't surface "your jackson-databind has a new release" as a theme.

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Feed URL source | Curated per interest tag (seed data) | Zero friction; user personalization comes from interest tags |
| Repo scan depth | Root + one level deep | Catches `backend/pom.xml` + `frontend/package.json` layouts without full tree walks |
| Dependency scan trigger | Daily scheduled job | Deps change rarely; keeps radar generation fast |
| Feed format | RSS/Atom only | Reliable, standard, covers all major tech blogs; no brittle scraping |
| Architecture | Two independent ingestion sources following existing patterns | No orchestrator changes; items appear in candidate pool automatically |
| Delivery | Two separate plans, articles first | Article ingestion is simpler and immediately enriches themes |

## Design

### Source 1: RSS/Article Ingestion (`ARTICLE`)

#### New Table: `feed_subscription`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK AUTO_INCREMENT | |
| tag_slug | VARCHAR(100) NOT NULL | FK → interest_tag.slug |
| feed_url | VARCHAR(2048) NOT NULL | RSS/Atom URL |
| title | VARCHAR(255) NOT NULL | Human label (e.g., "Spring Blog") |
| active | BOOLEAN NOT NULL DEFAULT TRUE | Enable/disable individual feeds |

Unique constraint: `(tag_slug, feed_url)`.

#### New Source Row

`Source(code='ARTICLE', displayName='Article', active=true, fetchIntervalMinutes=120)`

#### New Classes

**`RssFeedClient`** — uses Rome library (`com.rometools:rome`). Takes a feed URL, returns `List<FetchedItem>`. Maps RSS/Atom entry fields:
- `<title>` → title
- `<link>` → url
- `<description>` or `<summary>` → description (truncated to 2048 chars)
- `<author>` or `<dc:creator>` → author
- `<pubDate>` or `<published>` → postedAt
- `<guid>` or `<link>` → externalId

**`RssFeedIngestor`** — `@Scheduled` job, every 2 hours (configurable via `devradar.ingest.article.fixed-delay-ms`). Flow:
1. Load all active `feed_subscription` rows
2. For each feed, call `RssFeedClient.fetch(feedUrl)`
3. Inject the feed's `tag_slug` into `FetchedItem.topics` so `TagExtractor` tags the item even if the title doesn't contain the keyword
4. Pass each `FetchedItem` to `IngestionService.ingest()` with source `ARTICLE`
5. Per-feed failures are logged but don't stop the batch

**Deduplication:** Same as existing — `(sourceId, externalId)` unique constraint. ExternalId is the entry's guid/link.

#### Seed Data (~14 curated feeds)

| Tag Slug | Feed URL | Title |
|----------|----------|-------|
| java | https://inside.java/feed/ | Inside Java |
| java | https://www.baeldung.com/feed | Baeldung |
| spring_boot | https://spring.io/blog.atom | Spring Blog |
| react | https://react.dev/rss.xml | React Blog |
| javascript | https://blog.nodejs.org/feed/ | Node.js Blog |
| typescript | https://devblogs.microsoft.com/typescript/feed/ | TypeScript Blog |
| python | https://blog.python.org/feeds/posts/default | Python Blog |
| go | https://go.dev/blog/feed.atom | Go Blog |
| rust | https://blog.rust-lang.org/feed.xml | Rust Blog |
| docker | https://www.docker.com/blog/feed/ | Docker Blog |
| kubernetes | https://kubernetes.io/feed.xml | Kubernetes Blog |
| security | https://blog.cloudflare.com/rss/ | Cloudflare Blog |
| security | https://security.googleblog.com/feeds/posts/default | Google Security Blog |
| postgresql | https://www.postgresql.org/news/feed/ | PostgreSQL News |

All high-signal official sources. Dead feeds at deploy time get `active=false`.

---

### Source 2: Dependency-Aware Ingestion (`DEP_RELEASE`)

#### New Table: `user_dependency`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK AUTO_INCREMENT | |
| user_id | BIGINT NOT NULL | FK → users.id |
| repo_full_name | VARCHAR(255) NOT NULL | e.g., "PurandharAdigarla/devradar-vuln-test" |
| file_path | VARCHAR(512) NOT NULL | e.g., "backend/pom.xml" |
| ecosystem | VARCHAR(20) NOT NULL | `MAVEN`, `NPM`, `GRADLE` |
| package_name | VARCHAR(255) NOT NULL | e.g., "com.fasterxml.jackson.core:jackson-databind" |
| current_version | VARCHAR(100) NOT NULL | e.g., "2.16.1" |
| scanned_at | TIMESTAMP NOT NULL | Last scan time |

Unique constraint: `(user_id, repo_full_name, file_path, package_name)` — upserts on rescan.

#### New Source Row

`Source(code='DEP_RELEASE', displayName='Dependency', active=true, fetchIntervalMinutes=1440)`

#### New Classes

**`DependencyFileParser`** — interface with three implementations:
- `PomParser` — XML parsing of `<dependency>` elements; extracts `groupId:artifactId` + `<version>`. Skips deps with property placeholders (`${...}`) that can't be resolved.
- `PackageJsonParser` — JSON parsing of `dependencies` + `devDependencies` objects.
- `GradleParser` — regex extraction of `implementation 'group:artifact:version'` and `implementation "group:artifact:version"` patterns. Best-effort; won't catch all Gradle DSL variations.

**`DependencyScanJob`** — `@Scheduled` daily (configurable via `devradar.ingest.dep-scan.fixed-delay-ms`, default 86400000). For each user with a linked GitHub identity:
1. `GitHubApiClient.listRepos(token)` — get user's repos (capped at 20 repos per user, configurable)
2. For each repo, use GitHub Contents API to list root directory, then check for `pom.xml`, `package.json`, `build.gradle`, `build.gradle.kts`
3. Also check immediate subdirectories (one level deep) for the same files
4. Parse found files with the appropriate `DependencyFileParser`
5. Upsert results into `user_dependency`

**`DependencyReleaseClient`** — checks package registries for new versions:
- Maven Central: `GET https://search.maven.org/solrsearch/select?q=g:"{groupId}"+AND+a:"{artifactId}"&rows=5&wt=json` — returns latest versions
- npm: `GET https://registry.npmjs.org/{package}/latest` — returns latest version info
- Produces `FetchedItem` with title like "jackson-databind 2.17.0 released", url pointing to Maven Central page or npm page, description from release metadata if available.

**`DependencyReleaseIngestor`** — `@Scheduled` daily (configurable via `devradar.ingest.dep-release.fixed-delay-ms`, default 86400000; initial delay of 2h to ensure scan job runs first). Queries distinct `(ecosystem, package_name, current_version)` from `user_dependency`. For each, calls `DependencyReleaseClient` to check for versions newer than `current_version`. Produces `FetchedItem`s → `IngestionService.ingest()` with source `DEP_RELEASE`. Topics include the package name slug so `TagExtractor` can match interest tags.

**Deduplication:** ExternalId = `{ecosystem}:{package}:{version}` — each release version ingested once even if multiple users depend on it.

**Rate Limiting:** Scan job processes one user at a time. Max 20 repos per user, ~5 file checks per repo (root + subdirs) = ~100 GitHub API calls per user, well within GitHub's 5000/hour limit. Registry lookups are unauthenticated and rate-limited to 1 req/sec.

---

### Orchestrator Impact

**No orchestrator code changes.** The existing pipeline handles everything:
1. `RadarApplicationService` pre-filters candidates by date + user interest tags
2. `searchItems` tool returns items from all sources
3. `getItemDetail` returns `source_name` so the AI knows the citation type
4. The AI groups related items into themes naturally

**One prompt tweak:** Add source descriptions for the two new types:
- `ARTICLE`: authoritative blog posts and documentation — good for "why this matters" context
- `DEP_RELEASE`: direct dependency updates from the user's repos — highest priority for actionable themes

### Frontend Impact

Add two entries to `SOURCE_LABELS` in `SourceCard.tsx`:
- `ARTICLE: "Article"`
- `DEP_RELEASE: "Dependency"`

No other UI changes. Articles and dependency releases appear as citations in themes alongside existing source types.

---

## Delivery

Two independent plans, built in order:

1. **Plan A: RSS/Article Ingestion** — `feed_subscription` table, Rome dependency, `RssFeedClient`, `RssFeedIngestor`, seed data, frontend label. Self-contained, simpler.
2. **Plan B: Dependency-Aware Ingestion** — `user_dependency` table, parsers, `DependencyScanJob`, registry clients, `DependencyReleaseIngestor`, frontend label. Larger scope, depends on users having linked GitHub.

## Out of Scope

- User-managed custom RSS feeds
- Gradle version catalog support (`libs.versions.toml`)
- Full recursive repo scanning (beyond root + one level)
- Web scraping for non-RSS sources
- Email/push notifications
