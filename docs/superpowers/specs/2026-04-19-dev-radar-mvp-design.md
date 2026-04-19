# Dev Radar MVP — Design Spec

**Status:** Draft for review
**Date:** 2026-04-19
**Sub-project:** 1 of 3 (MVP) — see Roadmap section for sub-projects 2 and 3.

---

## 1. Concept

Dev Radar is a personalized tech-intelligence feed. A user signs up (email or GitHub), declares technical interests, and the system ingests dev content from multiple sources continuously. On demand or on a weekly schedule, an AI agent uses tool calling to synthesize a "Radar" — a short scannable report of the items that genuinely matter to that user, grouped into themes with cited sources.

**Audience:** Anyone in tech (developers, PMs, designers, students). GitHub login is optional enrichment, not a gate.

**Demo pitch:** *"I built a multi-user agentic system. Claude uses tool calling and model cascading to ingest dev content, synthesize personalized briefs, and act on findings — including opening migration PRs in the user's GitHub repos when CVEs are detected. Backed by an eval harness, a public observability dashboard, and an MCP server surface so the same intelligence can be queried from inside Claude Desktop or Cursor."*

---

## 2. Portfolio Positioning

This is the second project in a diversifying portfolio. The first, **Creeno ATS** (internal B2B recruitment platform), proves enterprise patterns: 7-module modular monolith, granular RBAC, audit trails, configurable status flow engine, Elasticsearch search, MinIO storage, WebSocket notifications, JWT auth.

Dev Radar fills the gaps Creeno does not cover:

| Skill | Creeno | Dev Radar |
|---|---|---|
| AI tool calling / function calling | Future vision only | Core feature |
| Multi-step LLM agent orchestration | — | Core feature |
| Background ingestion pipelines | — | Core feature |
| Cross-user AI summary caching | — | Core feature (cost discipline) |
| Public-facing product | Internal B2B | Public |
| OAuth2 third-party integration | — | GitHub OAuth |
| Streaming AI responses (SSE) | — | Core delivery mechanism |
| Model cascading by cost (Sonnet + Haiku) | — | Core architecture |

---

## 3. Scope (MVP — Sub-project 1)

### In scope
- Email + password auth (Creeno-style: bcrypt + JWT + refresh tokens)
- Optional GitHub OAuth2 (sign in or link to existing email account); when linked, requests `repo` scope so the auto-PR feature is enabled
- Interest declaration UI (search + categorized picker; auto-prefill from GitHub if linked)
- Background ingestion of two sources: Hacker News (Algolia API) and GitHub Trending
- CVE ingestion from GitHub Security Advisories (GHSA) — third source, but treated as a separate first-class signal
- Tag normalization and matching (Creeno pattern: spaces → underscores, case-insensitive)
- Radar generation via Claude with multi-step tool calling
- Two-model strategy: Sonnet for synthesis, Haiku for cheap relevance scoring
- Cross-user AI summary caching in Redis
- SSE streaming of radar generation to the frontend
- Read your own radars (current + history)
- **Agent action — Auto-PR for CVE upgrades**: when the agent identifies a CVE affecting a user's repo, it can propose a migration PR. User reviews the proposal in the UI; one click opens the actual PR via the GitHub API.
- **Eval harness** (`evals/` directory): golden datasets, LLM-as-judge with calibration against hand-labeled radars, regression tracking, scored on every radar generated
- **Observability dashboard** (public): per-radar token cost, p50/p95 generation latency, cache hit ratio, model routing decisions, eval scores over time
- **MCP server surface**: exposes `query_radar`, `get_user_interests`, `get_recent_items` as MCP tools so Claude Desktop / Cursor can query Dev Radar from inside the editor
- **Defensible model routing**: documented thresholds for when Haiku handles a call vs Sonnet, with eval-driven justification

### Deferred to Sub-project 2 (Social Layer)
- Follow other users
- Public/private radar toggle
- Discover page
- Profile pages

### Deferred to Sub-project 3 (Polish & Expansion)
- More sources (Reddit, RSS blogs, library release notes)
- Email / Slack digest
- Search across past radars
- Notifications, comments
- Anonymous "try it" mode
- Vector embeddings / semantic retrieval (specifically: search across past radars, semantic dedup of near-identical items across sources, MCP natural-language query expansion — see decision log for the "why not in MVP" reasoning)
- Token-by-token prose streaming inside themes
- Additional agent actions beyond Auto-PR (e.g., auto-create Linear issue, auto-Slack-DM)

---

## 4. Architecture

### 4.1 Stack

| Layer | Choice | Notes |
|---|---|---|
| Backend | Java 21 + Spring Boot 3.5+ | Same as Creeno; modular monolith with clean architecture |
| Frontend | React + Redux Toolkit + MUI | Same as Creeno |
| Database | MySQL 8 (source of truth) | Same as Creeno |
| Cache | Redis | New — AI summary cache, ingestion locks, GitHub repo cache |
| Auth | Spring Security + JWT + Refresh | Creeno pattern + GitHub OAuth2 |
| AI | Anthropic SDK (Claude) | Sonnet 4.6 (synthesis) + Haiku 4.5 (scoring) |
| Streaming | Server-Sent Events (SseEmitter) | One-way streaming fits radar generation |
| Ingestion | Spring `@Scheduled` | Single-instance for MVP; can graduate to Quartz |
| Migrations | Liquibase | Same as Creeno |
| Build | Maven | Same as Creeno |
| Deploy | Docker Compose (dev) → GCloud free tier (prod) | Single Compute Engine VM running everything |
| **Observability** | **Micrometer + Prometheus + Grafana** (or Spring Boot Actuator + custom JSON dashboard) | Powers the public observability dashboard |
| **MCP server** | **Anthropic MCP SDK (Java or Node)** | Exposes Dev Radar as an MCP server consumable from Claude Desktop / Cursor |
| **Evals** | Custom harness in `evals/` (Python or Java); LLM-as-judge via Claude | Golden datasets + regression tracking |
| **GitHub agent actions** | GitHub REST API (PR creation) using user's OAuth token with `repo` scope | Auto-PR feature |

### 4.2 Module structure

```
com.devradar/
├── auth/          # JWT issuance, login, register, GitHub OAuth callback (with repo scope)
├── user/          # accounts, interests, GitHub identity link
├── source/        # source registry + source_items table
├── ingest/        # @Scheduled fetchers (HN, GitHub trending, GHSA CVEs) → source_items
├── ai/            # Anthropic client, tool definitions, agent loop, Redis summary cache
├── radar/         # generation orchestration, persistence, SSE delivery
├── action/        # agent-driven side effects (Auto-PR for CVE) + user approval flow
├── mcp/           # MCP server surface — exposes query_radar / get_user_interests / get_recent_items
├── eval/          # eval harness, golden dataset loader, LLM-as-judge runner, scorecards
├── observability/ # cost/latency/cache/eval metrics aggregation + public dashboard endpoint
└── web/           # REST + SSE controllers, DTOs, exception handlers
```

Each module follows clean architecture: `domain/` (entities) → `repository/` (JPA) → `service/` (domain services) → `service/application/` (DTO mapping facades) → `web/rest/` (controllers).

---

## 5. Three Flows

### Flow 1 — User journey (~90s from landing to first radar)

| Time | User | Frontend | Backend |
|---|---|---|---|
| 0s | Lands on devradar.app | — | — |
| 5s | Clicks "Sign up with email" or "Sign in with GitHub" | Routes to register form OR redirects to GitHub OAuth | — |
| 15s | (GitHub path) Approves on GitHub | Receives `?code=xyz`, POSTs to `/api/auth/github/callback` | Exchange code → access token → upsert user + identity → issue JWT |
| 25s | — | Navigates to `/onboarding/interests` | (If GitHub linked) Async: fetch starred + top-commit repos → extract languages/topics → return pre-filled interest suggestions |
| 45s | Adds/removes interests from suggested set or curated catalog | Autocompletes against `interest_tags`; PUT `/api/users/me/interests` | Validates + normalizes tags, persists |
| 60s | Clicks "Generate my first radar" | POST `/api/radars`, opens SSE to `/api/radars/{id}/stream` | Creates `radars` row (GENERATING), dispatches async job, returns radar_id |
| 60-90s | Watches themes appear one by one as the agent finalizes each | Renders SSE events into Redux store | AI agent loop runs (see Flow 3) |
| 95s | Sees completed radar with cited sources | Closes SSE, renders final view | Sets status=READY, stores token_count |

### Flow 2 — Background ingestion (24/7, independent)

Two `@Scheduled` jobs:
- `HackerNewsIngestor` — every 1h. GET hn.algolia.com/api/v1/search with story tag and points threshold > 50.
- `GitHubTrendingIngestor` — every 6h. Scrape github.com/trending per tracked language.

For each fetched item:
1. Dedup check — Redis SETNX on `ingest:{source}:{external_id}` plus MySQL `(source_id, external_id)` unique constraint.
2. Extract metadata (title, url, author, posted_at, points/stars).
3. Tag extraction — scan title + description for known `interest_tags` slugs; plus GitHub repo `topics` field.
4. Persist `source_items` + `source_item_tags` in a single transaction.

Source fetch failures: log and skip. Ingestion is best-effort. Radar generation runs against whatever is currently in `source_items`.

### Flow 3 — Radar generation (the AI agent showcase)

**Step 0 — setup.** Load user's interests. Pre-filter `source_items`: WHERE `posted_at > NOW() - 7d` AND item has at least one tag matching user_interests. Typically ~150 candidates. Pass title + url + posted_at + tags to the agent (not full text).

**Step 1 — orchestrator.** Sonnet receives system prompt + candidate list + tool definitions: `searchItems`, `scoreRelevance` (calls Haiku), `getItemDetail`, `checkUserRepos` (no-op if user has no GitHub link).

**Step 2 — agent loop.** Multi-turn tool use:
- Turn 1: Sonnet searches for theme candidates
- Turn 2: Haiku scores items cheaply (cost < $0.001 per call)
- Turn 3: Sonnet reads top items in full
- Turn 4: Sonnet asks `checkUserRepos` if a CVE or breaking change is found
- Turns 5-N: Repeat for additional themes
- Final turn: Sonnet emits a structured `radar_output` tool call with themes + cited item_ids + summaries

**Step 3 — cache + persist + stream.**
- For each theme summary: compute `content_hash(cited_items_sorted)` → check Redis `ai_summary_cache`. HIT = reuse text (cross-user cost savings). MISS = store with TTL 30d.
- Insert `radar_themes` and `radar_theme_items` rows as themes finalize.
- Stream each theme over SSE as soon as it is ready (don't batch).
- At end: set `radars.status=READY`, store `token_count` and `generation_ms`.

**Cost target:** ~$0.02-0.05 per radar (Sonnet ~2k tokens × 4 turns + Haiku scoring). Cached summaries make repeat users effectively free.

---

## 6. Data Model (MySQL)

### Auth & user
```sql
users (id PK, email UNIQUE, password_hash NULLABLE, display_name NOT NULL,
       active BOOL DEFAULT true, created_at, updated_at)
user_github_identity (user_id PK FK, github_user_id, github_login,
       access_token_encrypted, granted_scopes, linked_at)
refresh_tokens (id PK, user_id FK, token_hash, expires_at, revoked_at)
interest_tags (id PK, slug UNIQUE, display_name, category)
       -- category: language | framework | topic | tool
user_interests (user_id, interest_tag_id) -- composite PK
```

`password_hash` is nullable so users who only ever use GitHub OAuth don't carry a placeholder. Login enforces presence based on the chosen flow.

### Ingestion
```sql
sources (id PK, code UNIQUE, display_name, active, fetch_interval_minutes)
source_items (id PK, source_id FK, external_id, url, title, author,
       posted_at, raw_payload JSON, fetched_at)
       UNIQUE (source_id, external_id), INDEX (posted_at)
source_item_tags (source_item_id, interest_tag_id) -- composite PK
```

### Radar
```sql
radars (id PK, user_id FK, period_start, period_end,
       status ENUM('GENERATING','READY','FAILED'),
       generated_at, generation_ms, token_count)
       INDEX (user_id, generated_at DESC)
radar_themes (id PK, radar_id FK, title, summary, display_order)
radar_theme_items (id PK, theme_id FK, source_item_id FK,
       ai_commentary, display_order)
```

### Agent actions
See Section 15 for the `action_proposals` table schema and full Auto-PR flow.

### Observability rollup
```sql
metrics_daily_rollup (date PK, total_radars, total_tokens_input, total_tokens_output,
       sonnet_calls, haiku_calls, cache_hits, cache_misses, p50_ms, p95_ms,
       eval_score_relevance, eval_score_citations, eval_score_distinctness)
```
Populated by a nightly `@Scheduled` aggregation job. Powers the public observability dashboard (Section 14) without scanning per-request rows.

### Audit
```sql
audit_log (id PK, user_id FK NULLABLE, action, entity, entity_id,
       details JSON, created_at)
       INDEX (user_id, created_at)
```

### Outside MySQL (Redis)
- `ai_summary_cache` — `content_hash → { summary, model, generated_at }`, TTL 30d
- `ingest:{source}:{external_id}` — SETNX dedup lock, TTL 5m
- `gh_user_repos:{user_id}` — cached GitHub repo list, TTL 1h
- SSE session state — in-memory on the single app instance

---

## 7. API Surface

### REST
```
# Auth
POST   /api/auth/register             { email, password, display_name }
POST   /api/auth/login                { email, password }       → { token, refresh }
POST   /api/auth/refresh              { refresh }               → { token }
POST   /api/auth/logout               (revoke refresh)
GET    /api/auth/github/start         → 302 to GitHub
GET    /api/auth/github/callback      ?code → links/creates user → JWT

# User
GET    /api/users/me
PATCH  /api/users/me                  { display_name }
GET    /api/users/me/interests
PUT    /api/users/me/interests        { tag_slugs: [...] }
DELETE /api/users/me/github-link

# Interests catalog
GET    /api/interest-tags?q=&category=         (search/autocomplete, paginated)

# Radars
POST   /api/radars                    → 201 { radar_id, status:GENERATING }
GET    /api/radars/{id}               → full radar with themes + items
GET    /api/radars                    → paginated list (max 100/page, default 20)

# Agent actions (Auto-PR)
GET    /api/actions/proposals?radar_id=  → list of agent-proposed actions for a radar (e.g., "open PR for CVE in repo X")
POST   /api/actions/{proposal_id}/approve → executes the action (calls GitHub API, opens PR), returns PR URL
DELETE /api/actions/{proposal_id}      → dismiss the proposal

# Observability (public, read-only)
GET    /api/observability/summary     → token cost (24h/7d), p50/p95 latency, cache hit %, eval scores
GET    /api/observability/timeseries  → per-day metrics for the dashboard charts

# Evals (admin-only)
POST   /api/evals/run                 → triggers eval suite over latest N radars
GET    /api/evals/runs                → eval run history with scores and regressions
```

### SSE
```
GET    /api/radars/{id}/stream        text/event-stream
```

Event types:
- `radar.started` — `{ radar_id }`
- `theme.complete` — `{ theme_id, title, summary, item_ids: [...], display_order }` (one event per theme as it finalizes)
- `action.proposed` — `{ proposal_id, kind:"auto_pr_cve", repo, package, summary }` (emitted when agent flags a CVE-action opportunity)
- `radar.complete` — `{ radar_id, generated_ms, token_count }`
- `radar.failed` — `{ radar_id, error_code, message }`
- `heartbeat` — every 15s to keep proxies alive

Streaming granularity is theme-level, not token-level. Each theme appears as a chunk when the agent finalizes it. Token-by-token prose streaming is deferred to sub-project 3 (would require an extra per-theme Claude call after the structured orchestration step).

### MCP server surface
Exposed via stdio + HTTP transport on a separate port (configurable). Tools:

| MCP Tool | Description |
|---|---|
| `query_radar` | Returns the latest radar for the authenticated user |
| `get_user_interests` | Returns the user's interest tags |
| `get_recent_items` | Returns ingested items from last N days, optionally filtered by tag |
| `propose_pr_for_cve` | Programmatic equivalent of the UI Auto-PR action |

MCP authentication: API key per user, generated in user settings, scoped to that user's data.

---

## 8. Error Handling

| Failure | Behavior |
|---|---|
| Source fetch fails | Log, skip, retry next tick. Ingestion is best-effort. |
| AI provider fails mid-generation | Set `radars.status=FAILED`, emit `radar.failed`, persist partial themes. User can retry from UI. |
| AI rate limit | Exponential backoff (1s, 2s, 4s, 8s, max 4 retries). Then fail. |
| GitHub API rate limit | Cached repo list in Redis (TTL 1h). `checkUserRepos` degrades to no-op. |
| Redis down | Cache becomes pass-through (always miss). System correct, just slower and more expensive. |
| MySQL down | Health check fails, readiness probe pulls instance from rotation. |
| Concurrent ingestion | Redis SETNX lock per `(source, batch_window)` prevents double-fetch. |
| **Auto-PR fails** | **Mark proposal `FAILED` with reason; user can retry or dismiss. Never auto-retry — a PR mutation is too expensive to silently re-attempt.** |
| **Auto-PR partial success** (PR opened but tests fail in CI) | **Status surfaced in proposal; do not auto-close. User decides.** |
| **MCP request fails** | **Standard MCP error envelope; clients handle.** |
| **Eval run fails** | **Logged, dashboard shows last successful run; no impact on user-facing flows.** |
| Validation errors | 400 with field-level error map (Creeno `GlobalExceptionHandler` pattern). |
| Errors exposed to client | Sanitized; no stack traces; structured `ErrorResponse { status, message, timestamp }`. |

Domain exceptions follow Creeno pattern: `UserNotFoundException`, `RadarNotFoundException`, `RadarGenerationFailedException`, `InterestNotFoundException`, `GitHubAuthException`, etc., each mapped to HTTP status in `GlobalExceptionHandler`.

---

## 9. Security

- Passwords: bcrypt cost 12.
- JWT: HS256, 24h access token, refresh token rotated on use.
- GitHub access tokens: AES-encrypted at rest in `user_github_identity.access_token_encrypted`.
- Endpoints: `permitAll` on `/api/auth/**`, `authenticated` on everything else.
- Pagination DoS protection: max-page-size=100, default=20 (Creeno pattern).
- CORS configured for the frontend origin only.
- Rate limiting per user on `POST /api/radars` (e.g., 10 per hour) to cap AI cost exposure from abuse.
- Audit log for: account creation, GitHub link/unlink, login, password reset (if added later), agent action approvals (Auto-PR proposals approved/dismissed).
- **GitHub OAuth scope:** request `repo` (write) only when user opts into Auto-PR feature; otherwise `public_repo` (read) is sufficient. UI clearly labels which actions require which scope.
- **MCP API keys:** generated server-side, hashed at rest, never re-displayed. User can revoke and rotate from settings.
- **Auto-PR safety:** all proposals require explicit user approval. Agent never opens PRs autonomously. Branch name is `dev-radar/cve-{ghsa_id}` and PR body cites the CVE source and the diff rationale.

---

## 10. Testing Strategy

- **Unit (JUnit 5 + Mockito):** tag normalization, password hashing, JWT validation, status transitions, AI tool argument parsing.
- **Integration (Spring Boot `@SpringBootTest` + Testcontainers MySQL + Redis):** repository queries, full HTTP flows, OAuth callback flow with WireMock for GitHub.
- **AI tests:** tool definitions tested with recorded fixtures. `@MockBean` AnthropicClient replays canned tool-call sequences. Separate manual `@Tag("live-ai")` suite hits real Claude on demand for smoke testing.
- **Frontend:** Vitest + React Testing Library for components; Playwright for golden paths (signup → interests → first radar → review Auto-PR proposal).
- **Auto-PR tests:** PR generation tested against a sandbox GitHub org (or fully mocked via WireMock). Real `repo` scope flows tested manually only.
- **MCP server tests:** integration tests using the MCP SDK's testing harness; verify each tool's input/output contract.
- **Eval harness:** runs in CI nightly against a fixed set of "golden radar inputs"; failure threshold blocks merge on regressions.
- **Coverage target:** 75% backend lines, 60% frontend (pragmatic).

---

## 11. Roadmap

| Sub-project | Scope | Demo gain |
|---|---|---|
| **1 (this spec)** | MVP: email+GitHub auth, ingestion (HN+GH trending+GHSA CVEs), AI radar with multi-step tool calling, model cascading (Sonnet+Haiku), Redis cache, SSE streaming, Auto-PR for CVE agent action, MCP server surface, eval harness, public observability dashboard | "I built a multi-user agentic system with eval-driven model routing, an MCP surface, and agents that act on the user's GitHub — not just summarize." |
| 2 | Social: follow graph, public/private radars, discover, profiles | "Now it is a social product." |
| 3 | Polish: more sources, email/Slack digest, search, notifications, comments, anonymous try-it mode, semantic retrieval | Production feel. |

---

## 12. Design Decisions Log

| Decision | Choice | Rationale |
|---|---|---|
| Architecture | Modular monolith + clean architecture | Same as Creeno; deploys on free tier; can extract later |
| Auth primary mechanism | Email + password | Inclusive to non-developers (PMs, designers, students). GitHub becomes enrichment, not gate. |
| GitHub integration | Optional OAuth2 link | Enriches interest auto-prefill and unlocks `checkUserRepos` agent tool. Gracefully no-ops if absent. |
| AI orchestration | Multi-step tool calling | Demonstrates real agent patterns vs simple prompt-stuffing. |
| Model strategy | Sonnet (orchestration) + Haiku (scoring) | Production cost discipline; documented in README as a teaching point. |
| Summary cache | Redis, keyed by `content_hash` | Cross-user reuse; same article summary serves all users; teaches AI cost discipline. |
| Streaming | SSE (SseEmitter) | One-way server→client fits radar generation; lighter than WebSocket. |
| Ingestion sources for MVP | HN + GitHub trending only | Two is enough to prove the pattern. More deferred to sub-project 3. |
| Ingestion driver | Spring `@Scheduled` | Simpler than Quartz for MVP single-instance scale. |
| Anonymous demo mode | Deferred to sub-project 3 | Nice-to-have; not core to the architecture story. |
| Vector embeddings | Deferred to sub-project 3 | Powerful but adds scope without proportional payoff for MVP. |
| Database | MySQL | Same as Creeno; reuses existing operational muscle memory. |
| Status flow engine | Not built (radar status is a simple enum) | Creeno already showcases configurable flows; not needed here. |
| Search | Not built (radar count is small per user) | Deferred to sub-project 3 if needed. |
| Notifications | Not built (UI shows status; no out-of-band delivery) | Deferred to sub-project 3 (email digest). |
| Mobile | Desktop-first responsive | Acceptable for MVP. |
| **Eval harness in MVP** | **Yes — first-class artifact** | 2026 senior signal (Hamel Husain framework). Without evals, "AI app" reads as a wrapper. |
| **Observability dashboard public** | **Yes — readable URL on the portfolio** | Per-radar cost/latency/cache hit/eval scores. Demonstrates production AI discipline. |
| **MCP server surface in MVP** | **Yes** | Building (not consuming) MCP is the highest-signal AI move in 2026. Forces clean tool boundaries. |
| **Agent action with side effects (Auto-PR for CVE)** | **Yes — promoted to MVP** | "Agent that acts on the user's GitHub" is what separates this from a chatbot. Required for the recruiter pitch. |
| **Model routing data-driven** | **Yes — published in observability dashboard** | Cascading without numbers is hand-waving. With numbers, it is the kind of thing Anthropic's DevRel posts about. |
| **CVE source (GHSA)** | **Added to MVP** | Without CVE ingestion, Auto-PR has nothing to act on. Three sources total: HN + GitHub trending + GHSA. |
| **Vector embeddings / RAG** | **Deliberately NOT in MVP** | Our retrieval problem is small and structured: ~150 pre-tagged candidates per radar fit trivially in Sonnet's 200k context. Tag-based SQL filtering is deterministic and faster than embedding lookup. Adding vectors would solve no problem we have. The 2026 senior signal is *knowing when NOT to reach for RAG* — Hamel Husain and Anthropic both flag reflexive RAG as the #1 junior pattern. Postmortem (Section 16) will include a "When we didn't use vectors and why" subsection. Promoted to sub-project 3 with three concrete use cases that would justify it: search across past radars, semantic dedup of near-identical items, MCP natural-language query expansion. |

---

## 13. Eval Harness Strategy

The eval harness is a first-class artifact, not an afterthought. It lives in `evals/` at the repo root and is exposed via the admin endpoints in Section 7.

### 13.1 What we evaluate

| Eval | What it scores | How |
|---|---|---|
| **Theme relevance** | Did the agent surface items that genuinely match the user's interests? | LLM-as-judge (Sonnet) compares generated themes against a hand-labeled "gold radar" for the same user + week |
| **Citation accuracy** | Does every claim in a theme summary trace to a cited source? | Programmatic: parse summary, check every URL and key claim is present in the cited `source_items` |
| **Theme distinctness** | Are themes meaningfully different, not duplicates? | LLM-as-judge with rubric: "are these two themes about the same underlying topic?" |
| **Cost discipline** | Did the agent stay within token and call budgets? | Programmatic: assert `token_count < threshold`, `tool_calls < max_calls` |
| **Auto-PR safety** | When the agent proposes a PR, does the diff actually fix what the CVE describes? Does it not break anything obvious? | Programmatic: parse the proposed diff against a fixture repo; assert version bumps match the CVE's fixed-in version; run a smoke build |
| **Refusal calibration** | When the agent should refuse (e.g., user has no relevant items in last 7d), does it refuse cleanly? | Hand-curated negative test cases |

### 13.2 Golden datasets

- `evals/golden_radars/` — 20+ curated `(user_interests, source_items, expected_themes)` triples, hand-labeled
- `evals/cve_fixtures/` — sample repos with known-vulnerable dependencies and known correct upgrade diffs
- `evals/refusal_cases/` — inputs the agent should handle by saying "no relevant items this week" rather than fabricating themes

### 13.3 Calibration

LLM-as-judge is calibrated against human labels on a held-out set. Document calibration in the postmortem (Section 16): if Sonnet-as-judge agrees with the human label X% of the time at threshold T, that is the operating point.

### 13.4 CI integration

Nightly GitHub Actions job runs the eval suite. A regression beyond a configured threshold posts a check on the latest main commit. Visible in the public observability dashboard.

---

## 14. Observability Dashboard

A public, read-only page at `/observability` and via API at `/api/observability/*`. No auth required to view (anonymized). This is *part of the portfolio surface*.

### 14.1 Metrics surfaced

- **Cost** — total Claude tokens (input + output), broken down by model (Sonnet vs Haiku), per-radar average, 24h / 7d / 30d trends
- **Latency** — p50 / p95 / p99 of `generation_ms`, broken down by phase (orchestration, tool calls, persistence)
- **Cache hit ratio** — Redis `ai_summary_cache` hit rate, with a "savings" tally ("cached summaries saved $X this week")
- **Model routing decisions** — count of Haiku calls vs Sonnet calls; routing-policy documentation linked
- **Eval scores** — most recent eval run's scores per category (theme relevance, citation accuracy, etc.); historical sparkline
- **Ingestion stats** — items ingested per source per day, dedup rate, tag-match rate

### 14.2 Implementation

- Spring Boot Actuator + Micrometer for metric collection
- Prometheus scrape endpoint at `/actuator/prometheus`
- Grafana dashboard JSON committed to repo so reviewers can reproduce locally
- Public dashboard endpoint reads aggregated metrics from a small `metrics_daily_rollup` MySQL table populated by a `@Scheduled` job
- All public metrics anonymized — no per-user data exposed

### 14.3 Why public

A senior reviewer can click one URL and see your system's actual production AI behavior. This is the move that separates "AI app" from "AI engineer."

---

## 15. Auto-PR Agent Action — Detailed Flow

### 15.1 Trigger

During radar generation, when the agent encounters an item flagged as a security advisory (GHSA source), it calls a dedicated tool:

```
checkRepoForVulnerability(package, version_range)
  → returns { affected_repos: [{repo, file_path, current_version, fix_version}, ...] }
```

If the result is non-empty, the agent emits a structured `action_proposal` of kind `auto_pr_cve`, persisted to `action_proposals` table with `status=PROPOSED`.

### 15.2 User review UI

On the radar detail page, proposals appear under the relevant theme as an expandable card:
- *"Auto-PR proposal: bump jackson-databind 2.16.2 → 2.16.3 in `creeno-backend/pom.xml` (fixes [GHSA-xxxx](link))"*
- Diff preview (rendered server-side using GitHub's diff endpoint)
- Two actions: **Open PR** | **Dismiss**

### 15.3 PR execution

On approve: server uses the user's `repo`-scoped OAuth token to:
1. Create branch `dev-radar/cve-{ghsa_id}` from `main`
2. PUT updated file contents (just the version bump in `pom.xml` / `package.json`)
3. POST PR with title `chore(security): bump {package} to {fix_version} (fixes {ghsa_id})` and body explaining the CVE + linking to the source advisory
4. Persist `pr_url` on the proposal row, mark `status=EXECUTED`

### 15.4 Failure modes

- **Branch already exists** — append `-2`, `-3`, etc.
- **Repo write denied** — surface to user, suggest re-auth with `repo` scope
- **File pattern not recognized** (unusual `pom.xml`/`package.json`) — propose with manual-edit warning, do not auto-modify
- **GitHub API failure** — never auto-retry; user retries explicitly

### 15.5 New table

```sql
action_proposals (
  id PK,
  radar_id FK,
  user_id FK,
  kind ENUM('auto_pr_cve'),
  payload JSON,             -- { repo, package, current_version, fix_version, ghsa_id, file_path }
  status ENUM('PROPOSED','EXECUTED','DISMISSED','FAILED'),
  pr_url NULLABLE,
  failure_reason NULLABLE,
  created_at, updated_at,
  INDEX (user_id, status)
)
```

---

## 16. Engineering Postmortem (Public Writeup)

A `POSTMORTEM.md` shipped at the repo root, written after the first 30 days of usage. Linked from the README. Topics:

- **5 ways the agent failed and how the eval harness caught them** — with traces, prompt diffs, and links to the eval runs that flagged each regression
- **Model routing policy decisions** — why Haiku for X, why Sonnet for Y, what eval signal triggered the threshold
- **Cost discipline learnings** — what we cached, what we didn't, what we wished we had
- **Auto-PR safety incidents (or close calls)** — what guardrails worked, what we changed
- **What we would do differently** — honest section

This single artifact is the highest-signal portfolio asset. It demonstrates that the engineering decisions weren't made in a vacuum and that we operated the system, not just shipped it.
