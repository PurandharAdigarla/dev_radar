# GitHub Releases Ingestion — Design Spec

## Problem

The `GH_TRENDING` source scrapes GitHub's trending page, producing citations that link to repo homepages with generic descriptions ("A framework for building..."). Users see a radar theme citing `resend/react-email` but have no idea what changed — they'd have to dig through the entire repo to find anything actionable.

Releases are inherently actionable: they have a version, a changelog, and a publication date. "react-email v4.0 released with RSC support" is immediately useful.

## Solution

Add a new `GH_RELEASES` ingestion source that polls the GitHub Releases API for a curated list of repos mapped to interest tags. Follows the existing ingestor pattern (client → ingestor job → IngestionService).

## Architecture

### New Files

| File | Purpose |
|------|---------|
| `GitHubReleasesClient.java` | REST client calling `GET /repos/{owner}/{repo}/releases?per_page=3` |
| `GitHubReleasesIngestor.java` | Scheduled job iterating curated repos, calling client → IngestionService |
| `015-seed-gh-releases-source.xml` | Liquibase changeset seeding the `GH_RELEASES` source row |

### GitHubReleasesClient

- Uses unauthenticated GitHub API (60 req/hr rate limit — sufficient for ~40 repos polling latest 3 releases each)
- Base URL: `https://api.github.com` (configurable via `devradar.gh-releases.base-url`)
- For each repo, calls `GET /repos/{owner}/{repo}/releases?per_page=3`
- Maps each release JSON to `FetchedItem`:
  - `externalId`: `"{owner}/{repo}:{tag_name}"` (e.g., `"facebook/react:v19.1.0"`)
  - `url`: `html_url` from release JSON (links to the release page, not the repo root)
  - `title`: `"{repo_name} {tag_name}"` with release name appended if different (e.g., `"react v19.1.0 — Compiler improvements"`)
  - `description`: `body` field (the release changelog/notes), truncated to 2000 chars
  - `author`: `author.login` from release JSON
  - `postedAt`: `published_at` from release JSON
  - `rawPayload`: full release JSON
  - `topics`: extracted from config (the interest tag slugs associated with this repo)
- Skips draft and prerelease entries

### GitHubReleasesIngestor

- Source code: `GH_RELEASES`
- Schedule: every 2 hours (`fixedDelay=7200000`, `initialDelay=120000`)
- Reads curated repo list from config property `devradar.ingest.gh-releases.repos`
- Config format: `owner/repo:tag1,tag2;owner2/repo2:tag3` (e.g., `facebook/react:react,frontend;spring-projects/spring-boot:spring_boot,java`)
- Iterates each repo, fetches releases, passes batch to `IngestionService.ingestBatch()`
- Logs per-repo failures and continues

### Curated Repo List (Initial Seed)

Mapped to existing interest tags:

```
facebook/react:react,frontend,javascript
vercel/next.js:next_js,react,frontend
sveltejs/svelte:svelte,frontend
vuejs/core:vue,frontend,javascript
angular/angular:angular,frontend,typescript
spring-projects/spring-boot:spring_boot,java,backend
spring-projects/spring-framework:spring_boot,java,backend
django/django:django,python,backend
fastapi/timerboard:fastapi,python,backend
rails/rails:rails,backend
golang/go:go
rust-lang/rust:rust
JetBrains/kotlin:kotlin
apple/swift:swift
microsoft/TypeScript:typescript
docker/docker-ce:docker,devops
kubernetes/kubernetes:kubernetes,devops
hashicorp/terraform:terraform,devops
redis/redis:redis,database
mysql/mysql-server:mysql,database
postgres/postgres:postgres,database
elastic/elasticsearch:elasticsearch,database
mongodb/mongo:mongodb,database
tailwindlabs/tailwindcss:frontend
vitejs/vite:frontend,javascript
modelcontextprotocol/specification:mcp,ai_tooling
langchain-ai/langchain:ai_tooling,llm,python
ollama/ollama:ai_tooling,llm
```

### Liquibase Migration

`015-seed-gh-releases-source.xml` — insert into `sources`:
- `code`: `GH_RELEASES`
- `display_name`: `GitHub Releases`
- `active`: `true`
- `fetch_interval_minutes`: `120`

### What Doesn't Change

- `IngestionService` — unchanged, processes `FetchedItem` as-is
- `TagExtractor` — unchanged, topics from config provide tag matching
- `SourceItem` entity — unchanged
- `GH_TRENDING` — stays active, can be deactivated later via DB flag
- Frontend — unchanged, already renders `sourceName` from `Source.code`

## Rate Limiting

GitHub's unauthenticated API allows 60 requests/hour. With ~30 repos and 1 API call each, a single run uses ~30 requests. At every-2-hours schedule, this is well within limits. If the list grows beyond ~50 repos, we'd add a GitHub token header for 5000 req/hr.

## Dedup

Existing two-stage dedup handles this naturally:
- `externalId` = `"{owner}/{repo}:{tag_name}"` is unique per release
- Redis SETNX prevents redundant DB checks
- DB unique constraint `(source_id, external_id)` is the final backstop

## Testing

- `GitHubReleasesClientTest` — unit test with WireMock, verify FetchedItem mapping from sample release JSON
- `GitHubReleasesIngestorTest` — unit test verifying config parsing, error handling per repo
