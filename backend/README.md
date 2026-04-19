# Dev Radar — Backend

Foundation layer for the Dev Radar MVP (sub-project 1, plan 1).

## Stack
Java 21, Spring Boot 3.5+, MySQL 8, Liquibase, Spring Security (JWT), MapStruct, JUnit 5 + Testcontainers.

## Local development

```bash
# Start MySQL (override host port via DB_HOST_PORT env var if 3306 is taken)
docker compose up -d
# example with custom port: DB_HOST_PORT=3307 docker compose up -d

# Run app
mvn spring-boot:run

# App runs at http://localhost:8080
```

## Tests

```bash
mvn test
```

Integration tests use Testcontainers MySQL — Docker required.

On macOS with Docker Desktop, if Testcontainers cannot find your Docker socket, create `~/.testcontainers.properties` with:

```
docker.host=unix:///Users/<you>/.docker/run/docker.sock
```

## What this plan ships

- Email + password registration
- Login (returns JWT access token + opaque refresh token)
- Refresh token rotation (each refresh issues a new pair, revokes the old)
- Logout (revokes refresh)
- `GET /api/users/me`, `PATCH /api/users/me`
- Interest tags catalog: `GET /api/interest-tags?q=&category=`
- Set/get user interests: `GET/PUT /api/users/me/interests`
- Audit log of auth events (USER_REGISTERED, USER_LOGIN, USER_LOGOUT)
- GlobalExceptionHandler returning structured `ErrorResponse`

## What this plan does NOT ship

- Anything AI-related (deferred to Plan 3)
- Ingestion, sources, source items (Plan 2)
- GitHub OAuth + Auto-PR (Plan 4)
- MCP server (Plan 5)
- Eval harness (Plan 6)
- Observability dashboard (Plan 7)
- Frontend (Plan 8)

## Plan 2 — Ingestion

Three `@Scheduled` jobs continuously populate `source_items` from external sources.

| Source code | Job | Cadence (default) | Client |
|---|---|---|---|
| `HN` | HackerNewsIngestor | every 1h | Algolia REST API |
| `GH_TRENDING` | GitHubTrendingIngestor | every 6h | github.com/trending HTML scrape (Jsoup) |
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

### Configuration

| Property | Default | Description |
|---|---|---|
| `devradar.ingest.hn.fixed-delay-ms` | 3600000 (1h) | HN fetch cadence |
| `devradar.ingest.hn.initial-delay-ms` | 30000 (30s) | First HN fetch after startup |
| `devradar.ingest.gh-trending.fixed-delay-ms` | 21600000 (6h) | GitHub trending fetch cadence |
| `devradar.ingest.gh-trending.languages` | java,python,javascript,typescript,go,rust | CSV of languages to scrape |
| `devradar.ingest.ghsa.fixed-delay-ms` | 1800000 (30m) | GHSA fetch cadence |
