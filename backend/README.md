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

## Plan 3 — AI Radar Generation

Multi-step Anthropic tool-calling agent loop that turns ingested `source_items` into personalized themed radars.

### Architecture

| Component | Role |
|---|---|
| `AiClient` (interface) | Provider-agnostic chat-with-tools abstraction |
| `AnthropicAiClient` | Real impl using `com.anthropic:anthropic-java` 2.26.0 |
| `RecordedAiClient` (test) | Replays canned responses for deterministic agent loop tests |
| `SearchItemsTool` | Query `source_items` by interest tag slugs |
| `ScoreRelevanceTool` | Cheap Haiku call to score a small batch by relevance |
| `GetItemDetailTool` | Fetch full item details by id |
| `RadarOrchestrator` | Multi-turn agent loop; max 8 iterations by default |
| `RadarGenerationService` | `@Async` runner; persists themes; publishes SSE events |
| `AiSummaryCache` | Redis cache keyed by sorted source_item_id list |
| `RadarEventBus` | In-memory per-radar SseEmitter list |

### Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/radars` | Create radar; returns 201 + radar_id immediately; generation runs async |
| GET | `/api/radars/{id}` | Full radar with themes + cited items |
| GET | `/api/radars` | Paginated list of your radars |
| GET | `/api/radars/{id}/stream` | SSE: `radar.started`, `theme.complete`, `radar.complete`, `radar.failed` |

### Configuration

| Property | Default | Description |
|---|---|---|
| `anthropic.api-key` | `${ANTHROPIC_API_KEY}` | Required at runtime for real Claude calls |
| `anthropic.orchestrator-model` | `claude-sonnet-4-6` | Sonnet for the agent loop |
| `anthropic.scoring-model` | `claude-haiku-4-5-20251001` | Haiku for cheap relevance scoring |
| `anthropic.max-tool-iterations` | `8` | Hard cap on agent loop turns |
| `anthropic.max-tokens-per-call` | `4096` | Max output tokens per Sonnet call |

### Local manual radar generation

Requires a real `ANTHROPIC_API_KEY` and ingested data:

```bash
cd backend
export ANTHROPIC_API_KEY=sk-ant-...
DB_HOST_PORT=${DB_HOST_PORT:-3307} docker compose up -d
DB_HOST_PORT=${DB_HOST_PORT:-3307} mvn spring-boot:run &
sleep 25  # wait for app + first ingestion fire

# Register + login + set interests + create radar
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"radar@me","password":"Password1!","displayName":"Me"}' && \
  curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"radar@me","password":"Password1!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -s -X PUT localhost:8080/api/users/me/interests -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"tagSlugs":["spring_boot","react","mysql"]}'

RADAR_ID=$(curl -s -X POST localhost:8080/api/radars -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')

# Stream the SSE events as the agent runs
curl -N -H "Authorization: Bearer $TOKEN" "localhost:8080/api/radars/$RADAR_ID/stream"
```

You should see `radar.started`, then `theme.complete` events as the agent finalizes each theme, then `radar.complete`. Then GET `/api/radars/$RADAR_ID` returns the full radar.

### Multi-provider AI (Plan 3 extension)

The `AiClient` interface lets you swap LLM providers without touching the agent loop, tools, or persistence:

| Spring profile | Implementation | Cost | Use case |
|---|---|---|---|
| `default` or `anthropic` | `AnthropicAiClient` (Claude Sonnet 4.6 + Haiku 4.5) | Paid | Production radar quality |
| `gemini` | `GeminiAiClient` (Gemini 2.0 Flash via REST) | Free tier | Dev / portfolio demo |
| `demo` | `MockAiClient` (canned radar JSON) | Free | UI dev, screen recordings, no API key |

```bash
# Demo mode — no API key required
mvn spring-boot:run -Dspring-boot.run.profiles=demo

# Gemini (free tier, set GEMINI_API_KEY in env first)
export GEMINI_API_KEY=...   # from aistudio.google.com/apikey
mvn spring-boot:run -Dspring-boot.run.profiles=gemini

# Anthropic (paid)
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run
```

The `RadarOrchestrator` agent loop, all 3 tools, persistence, SSE, caching — none of that knows or cares which provider is active.

## Plan 4 — GitHub OAuth + Auto-PR

Lets users link their GitHub account so the radar agent can scan their repos for vulnerable dependencies and propose a PR with the fix.

### Architecture

| Component | Role |
|---|---|
| `TokenEncryptor` | AES-256-GCM encryption for GitHub access tokens at rest |
| `GitHubOAuthClient` | OAuth code → access_token exchange |
| `GitHubApiClient` | REST wrapper for `/user`, `/user/repos`, `/contents`, branch + PR creation |
| `CheckRepoForVulnerabilityTool` | Agent tool: scans user's repos, records `action_proposals` |
| `AutoPrExecutor` | Branches the user's repo, commits the version bump, opens a PR |
| `ActionApplicationService` | List / approve / dismiss proposals |
| `ActionResource` | REST: `GET /api/actions/proposals`, `POST /{id}/approve`, `DELETE /{id}` |

### New endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/auth/github/start` | 302 redirect to GitHub consent |
| GET | `/api/auth/github/callback` | OAuth callback; exchanges code, creates/links user, returns JWT |
| GET | `/api/actions/proposals?radar_id=X` | List proposals for a radar |
| POST | `/api/actions/{id}/approve` | Body: `{"fix_version":"2.16.3"}` — opens the real PR via GitHub API |
| DELETE | `/api/actions/{id}` | Dismiss proposal |

### Configuration

| Property / env var | Default | Description |
|---|---|---|
| `GITHUB_OAUTH_CLIENT_ID` | empty | Required for OAuth flow |
| `GITHUB_OAUTH_CLIENT_SECRET` | empty | Required for OAuth flow |
| `GITHUB_OAUTH_REDIRECT_URI` | `http://localhost:8080/api/auth/github/callback` | Match the value registered in your GitHub OAuth App |
| `GITHUB_TOKEN_ENCRYPTION_KEY` | dev key in `application.yml` | Base64-encoded 32 bytes for AES-256. Generate with `openssl rand -base64 32`. |

### Setting up the OAuth app

1. Go to https://github.com/settings/developers → "New OAuth App"
2. Application name: `Dev Radar (local)`
3. Homepage URL: `http://localhost:8080`
4. Authorization callback URL: `http://localhost:8080/api/auth/github/callback`
5. After creating: copy Client ID + generate Client Secret
6. Set in your `.env`:
   ```bash
   GITHUB_OAUTH_CLIENT_ID=Iv1.abcdef...
   GITHUB_OAUTH_CLIENT_SECRET=...
   GITHUB_TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)
   ```

### Agent flow (when AI is configured)

1. Ingestion brings in a GHSA item (e.g., `jackson-databind` CVE)
2. User generates a radar
3. Agent calls `checkRepoForVulnerability(package="jackson-databind", version_pattern="2.16.2", ghsa_id="GHSA-...")`
4. Tool scans the user's repos for `pom.xml`/`package.json` containing the version → finds `creeno-backend/pom.xml` matches
5. Tool persists an `action_proposal` row + returns the affected file to the agent
6. After the agent loop completes, the SSE event `action.proposed` fires for each new proposal
7. User clicks "Open PR" in the UI → POST `/api/actions/{id}/approve` with `{"fix_version":"2.16.3"}`
8. `AutoPrExecutor` creates branch `dev-radar/cve-ghsa-...`, commits the file with the version bump, opens a PR; `pr_url` recorded on the proposal

## MCP Server Surface

Dev Radar exposes four tools via the Model Context Protocol so MCP clients (Claude Desktop, Cursor, etc.) can query your radar data directly from inside your editor.

| Tool | Scope | Description |
|---|---|---|
| `query_radar` | READ | Latest READY radar for the authenticated user |
| `get_user_interests` | READ | User's interest tags |
| `get_recent_items` | READ | Recent ingested items filtered by the user's interests (args: `days`, optional `tagSlug`) |
| `propose_pr_for_cve` | WRITE | Approve a CVE-fix PR proposal (args: `proposalId`, `fixVersion`) |

### Generating an API key

API keys are per-user and scoped (READ or WRITE). Keys are generated with a JWT-authenticated REST call and are returned exactly once on creation — store them immediately.

```bash
curl -X POST http://localhost:8080/api/users/me/api-keys \
     -H "Authorization: Bearer <your-jwt-access-token>" \
     -H "Content-Type: application/json" \
     -d '{"name":"Cursor","scope":"READ"}'
```

The response contains a `key` field starting with `devr_`. Copy it; it will not be shown again. Use `GET /api/users/me/api-keys` to list active keys and `DELETE /api/users/me/api-keys/{id}` to revoke.

### Connecting Claude Desktop

Add to `claude_desktop_config.json` (typically at `~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "devradar": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/mcp/sse",
               "--header", "Authorization: Bearer devr_<your-key>"]
    }
  }
}
```

Restart Claude Desktop. The four tools should appear in the MCP tool tray.

### Connecting Cursor

Add a custom MCP server in Cursor settings pointing to `http://localhost:8080/mcp/sse` with the `Authorization: Bearer devr_<key>` header.

### Observability

MCP activity is exposed via Micrometer counters on `/actuator/prometheus`:

- `mcp_tool_calls_total{tool, status}` — every tool invocation, tagged with `tool` and `status` (`success` / `error` / `denied_scope`)
- `mcp_auth_failures_total{reason}` — authentication rejections, tagged `reason` (`missing` / `malformed` / `unknown_key`)
