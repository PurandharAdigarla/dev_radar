# Dev Radar — MCP Server Surface Design

**Date:** 2026-04-21
**Status:** Approved for implementation (Plan 6)
**Related:** `docs/superpowers/specs/2026-04-19-dev-radar-mvp-design.md` (Section 7.3 — MCP server surface)

## 1. Goal

Expose Dev Radar's core data via Anthropic's Model Context Protocol so MCP clients (Claude Desktop, Cursor, etc.) can query a user's radars, interests, and recent items from inside the editor — and propose CVE-fix PRs — using a scoped API key.

**Portfolio signal:** "I *built* an MCP server with scoped API keys and mutation tools" — not just consumed one.

## 2. Architecture

```
MCP Client (Claude Desktop / Cursor)
    │ HTTP SSE (stdio clients connect via mcp-remote shim)
    ▼
Spring Boot main app (same port 8080)
  └─ /mcp/**  (Spring AI MCP starter registers SSE endpoints)
       │  ↓ ApiKeyAuthenticationFilter validates Bearer <api-key>, sets SecurityContext
       ▼
  com.devradar.mcp
    ├─ RadarMcpTools        @Tool query_radar          (READ)
    ├─ RecentItemsMcpTools  @Tool get_recent_items     (READ)
    ├─ InterestMcpTools     @Tool get_user_interests   (READ)
    └─ ActionMcpTools       @Tool propose_pr_for_cve   (WRITE)
         │
         ▼ delegates to existing application services
  RadarApplicationService, InterestApplicationService,
  SourceItemRepository, ActionApplicationService
```

**Key design principle:** The MCP layer is a thin adapter. No business logic lives in `com.devradar.mcp` — every tool method resolves to a call on an existing application service. The MCP module owns only: tool schemas, LLM-friendly DTO shapes, API key auth, and scope enforcement.

**Simplification from the MVP spec:** Original MVP design says "stdio + HTTP transport on a separate port." This spec instead uses the Spring AI MCP starter's HTTP SSE transport on the **same port** as the main app under the `/mcp` path. Native stdio is dropped in favour of the standard `mcp-remote` npm shim that stdio clients (Claude Desktop) already use. Separate-port deployment adds Tomcat config weight without proportional payoff; scope and risk both drop.

## 3. Tech Stack Additions

- **`spring-ai-starter-mcp-server-webmvc`** — wraps the official `io.modelcontextprotocol:java-sdk`, provides `@Tool` annotation-based tool registration, handles the JSON-RPC / SSE framing.
- **New `user_api_keys` table** — Liquibase migration `012-api-keys-schema.xml`.
- **New `ApiKeyAuthenticationFilter`** in the Spring Security filter chain, positioned before the existing JWT filter.

No other new infrastructure.

## 4. Data Model

New table `user_api_keys`:

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | |
| `user_id` | `BIGINT NOT NULL` | FK → `users(id)` |
| `name` | `VARCHAR(100) NOT NULL` | User-supplied label (e.g. "Cursor laptop") |
| `key_hash` | `VARCHAR(128) NOT NULL UNIQUE` | SHA-256 hex of raw key |
| `key_prefix` | `VARCHAR(16) NOT NULL` | First 8 chars of raw key (for UI display, e.g. `devr_abc1…`) |
| `scope` | `VARCHAR(10) NOT NULL` | `READ` or `WRITE` |
| `created_at` | `TIMESTAMP NOT NULL` | |
| `last_used_at` | `TIMESTAMP NULL` | Updated via async event on every successful auth |
| `revoked_at` | `TIMESTAMP NULL` | Soft-delete; non-null = inactive |

**Indexes:** unique on `key_hash`; composite on `(user_id, revoked_at)` for listing active keys.

**Key format:** `devr_<32-char base62>` (~190 bits entropy). The `devr_` prefix is self-identifying in logs and code-scanners.

**Storage rules:**
- Raw key generated server-side via `SecureRandom`; returned exactly once in the create-key response.
- Only SHA-256 hash persisted. No bcrypt — API keys have enough entropy that a fast hash is fine and lookup must be single-query (bcrypt requires per-row comparison).
- `last_used_at` updated on each request via async `ApiKeyUsedEvent` to avoid write amplification on the hot path.

**New domain classes:**
- `UserApiKey` entity.
- `ApiKeyScope` enum (`READ`, `WRITE`).

**Repository methods:**
- `findByKeyHashAndRevokedAtIsNull(String hash)` — auth lookup.
- `findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId)` — listing active keys.

## 5. MCP Tools

Four tools registered via Spring AI `@Tool` annotations. Each is a thin wrapper over existing application services.

### 5.1 `query_radar` (READ)

**Input:** none (uses authenticated user from `SecurityContext`).
**Behavior:** Returns the latest `READY` radar for the user, or an empty payload if none exist.
**Response:**

```json
{
  "radarId": 42,
  "generatedAt": "2026-04-20T10:15:00Z",
  "periodStart": "2026-04-13T00:00:00Z",
  "periodEnd": "2026-04-20T00:00:00Z",
  "themes": [
    { "title": "Spring Boot updates",
      "summary": "…",
      "citations": [ { "title": "…", "url": "https://…" } ] }
  ]
}
```

Citations capped at 3 per theme; `rawPayload` stripped.
**Delegate:** `RadarApplicationService.getLatestForUser(userId)` (new method — returns an `Optional<RadarDetail>`).

### 5.2 `get_user_interests` (READ)

**Input:** none.
**Response:**

```json
{ "interests": [ { "slug": "java", "displayName": "Java", "category": "language" } ] }
```

**Delegate:** `InterestApplicationService.myInterests()`.

### 5.3 `get_recent_items` (READ)

**Input:** `{ days: int (1–30, default 7), tagSlug: string? }`.
**Behavior:** Returns ingested items from the last N days, filtered by the user's interests (not every item in the system — users see relevant items only). Optional `tagSlug` further narrows. Capped at 20 items.
**Response:**

```json
{ "items": [
    { "title": "…", "url": "https://…", "source": "hn",
      "postedAt": "2026-04-19T…", "tags": ["java", "spring_boot"] }
]}
```

**Delegate:** new `SourceItemRepository.findRecentByUserInterests(userId, since, tagSlug, limit)` — joins `source_items` × `source_item_tags` × `interest_tags` × `user_interests`.

### 5.4 `propose_pr_for_cve` (WRITE)

**Input:** `{ proposalId: long, fixVersion: string }`.
**Behavior:** Programmatic equivalent of the UI Auto-PR approval button.
**Response:**

```json
{ "status": "EXECUTED", "prUrl": "https://github.com/…/pull/42" }
```

**Delegate:** `ActionApplicationService.approve(proposalId, fixVersion, userId)`. Ownership check lives in the application service (returns 403-equivalent MCP error if proposal doesn't belong to caller).

**Scope enforcement:** `@RequireScope(WRITE)` on this method; aspect reads the principal's scope and throws `McpScopeException` → mapped to MCP error code `-32001` ("Unauthorized scope") if insufficient.

## 6. Authentication & Security

### 6.1 API key authentication filter

`ApiKeyAuthenticationFilter` sits in the Spring Security chain **before** `JwtAuthenticationFilter`:

1. Only activates for paths matching `/mcp/**`.
2. Extracts `Authorization: Bearer <token>`.
3. If token starts with `devr_` → treat as API key:
   a. SHA-256 hash the raw key.
   b. Look up via `userApiKeyRepository.findByKeyHashAndRevokedAtIsNull(hash)`.
   c. On hit, build `ApiKeyAuthenticationToken(principal=ApiKeyPrincipal(userId, keyId, scope), authorities=[SCOPE_<scope>])` and set in `SecurityContext`.
   d. Publish `ApiKeyUsedEvent(keyId)` (async) so `last_used_at` updates off the request path.
4. If token missing or invalid → `401` with MCP-shaped JSON-RPC error envelope.

JWT auth continues to work unchanged for `/api/**` — the two filters are mutually exclusive by path.

### 6.2 Principal & helpers

- `ApiKeyPrincipal(userId, keyId, scope)` — exposes `userId` so `SecurityUtils.getCurrentUserId()` keeps working unchanged.
- New `SecurityUtils.getCurrentApiKeyScope()` — returns the scope; throws if caller isn't API-key-authed.

### 6.3 Scope enforcement

- `@RequireScope(ApiKeyScope value)` meta-annotation on `@Tool` methods that need WRITE.
- `RequireScopeAspect` (`@Around`) checks the current principal's scope and throws `McpScopeException` if insufficient.
- `McpErrorAdvice` maps `McpScopeException` → MCP JSON-RPC error `-32001`.

### 6.4 Key management REST endpoints (JWT-authenticated)

| Endpoint | Request | Response |
|---|---|---|
| `POST /api/users/me/api-keys` | `{ name, scope }` | `{ id, name, scope, key: "devr_abc…", keyPrefix, createdAt }` — raw key returned **once** |
| `GET /api/users/me/api-keys` | — | `[{ id, name, scope, keyPrefix, createdAt, lastUsedAt, revoked }]` (no raw key) |
| `DELETE /api/users/me/api-keys/{id}` | — | `204`; sets `revoked_at = now()` |

### 6.5 Security config changes

- `/mcp/**` added to `permitAll()` in `SecurityConfig` so the JWT filter doesn't 401 it. The API key filter enforces that a valid key is actually present — no API key, no MCP.
- Filter ordering: `ApiKeyAuthenticationFilter` → `JwtAuthenticationFilter` → standard Spring Security.

### 6.6 Observability hooks

- Counter `mcp.tool.calls` tagged `tool` + `status` (`success` / `error` / `denied_scope`).
- Counter `mcp.auth.failures` for bad / expired / revoked keys.
- Both roll into the existing nightly `MetricsAggregationJob` via the daily Redis counters already in place for other services.

## 7. File Structure

```
backend/
├── pom.xml                                                (modify: + spring-ai-starter-mcp-server-webmvc)
├── src/main/
│   ├── java/com/devradar/
│   │   ├── config/
│   │   │   ├── SecurityConfig.java                       (modify: permit /mcp/**, add ApiKeyAuthenticationFilter)
│   │   │   └── McpConfig.java                            (new: register ToolCallbackProvider beans)
│   │   ├── domain/
│   │   │   ├── UserApiKey.java                           (new entity)
│   │   │   └── ApiKeyScope.java                          (new enum: READ, WRITE)
│   │   ├── repository/
│   │   │   ├── UserApiKeyRepository.java                 (new)
│   │   │   └── SourceItemRepository.java                 (modify: + findRecentByUserInterests)
│   │   ├── security/
│   │   │   ├── ApiKeyAuthenticationFilter.java           (new)
│   │   │   ├── ApiKeyAuthenticationToken.java            (new)
│   │   │   ├── ApiKeyPrincipal.java                      (new)
│   │   │   ├── ApiKeyHasher.java                         (new: SHA-256 helper)
│   │   │   ├── ApiKeyGenerator.java                      (new: SecureRandom + devr_ prefix)
│   │   │   └── SecurityUtils.java                        (modify: + getCurrentApiKeyScope)
│   │   ├── apikey/
│   │   │   ├── ApiKeyService.java                        (new: generate / list / revoke)
│   │   │   ├── ApiKeyUsedEvent.java                      (new)
│   │   │   ├── ApiKeyUsedListener.java                   (new: async updates last_used_at)
│   │   │   └── application/
│   │   │       └── ApiKeyApplicationService.java         (new: facade for REST layer)
│   │   ├── mcp/
│   │   │   ├── RadarMcpTools.java                        (new: @Tool query_radar)
│   │   │   ├── InterestMcpTools.java                     (new: @Tool get_user_interests)
│   │   │   ├── RecentItemsMcpTools.java                  (new: @Tool get_recent_items)
│   │   │   ├── ActionMcpTools.java                       (new: @Tool propose_pr_for_cve)
│   │   │   ├── RequireScope.java                         (new: annotation)
│   │   │   ├── RequireScopeAspect.java                   (new)
│   │   │   ├── McpScopeException.java                    (new)
│   │   │   ├── McpErrorAdvice.java                       (new)
│   │   │   └── dto/
│   │   │       ├── RadarMcpDTO.java
│   │   │       ├── ThemeMcpDTO.java
│   │   │       ├── CitationMcpDTO.java
│   │   │       ├── InterestMcpDTO.java
│   │   │       └── RecentItemMcpDTO.java
│   │   ├── radar/application/RadarApplicationService.java (modify: + getLatestForUser)
│   │   └── web/rest/
│   │       ├── ApiKeyResource.java                       (new: /api/users/me/api-keys)
│   │       └── dto/
│   │           ├── ApiKeyCreateRequest.java
│   │           ├── ApiKeyCreateResponse.java             (includes raw key, one-time)
│   │           └── ApiKeySummaryDTO.java
│   └── resources/
│       ├── application.yml                               (modify: spring.ai.mcp.server config)
│       └── db/changelog/
│           ├── db.changelog-master.xml                   (modify: include 012)
│           └── 012-api-keys-schema.xml                   (new)
└── src/test/java/com/devradar/
    ├── apikey/ApiKeyServiceTest.java                     (new: unit)
    ├── security/ApiKeyAuthenticationFilterTest.java      (new: unit — Mockito)
    ├── mcp/
    │   ├── RadarMcpToolsIT.java                          (new: IT via MCP HTTP endpoint)
    │   ├── ActionMcpToolsScopeIT.java                    (new: IT — READ key rejected for WRITE tool)
    │   └── RecentItemsMcpToolsIT.java                    (new: IT)
    └── web/rest/ApiKeyResourceIT.java                    (new: IT for REST key mgmt)
```

## 8. Error Handling

| Failure | Behavior |
|---|---|
| Missing `Authorization` header on `/mcp/**` | `401` JSON-RPC error `-32000`: "Missing API key" |
| Invalid / revoked API key | `401` JSON-RPC error `-32000`: "Invalid API key" |
| READ key calling WRITE tool | `403` JSON-RPC error `-32001`: "Unauthorized scope" |
| Tool input validation error (e.g. `days > 30`) | `400` JSON-RPC error `-32602`: "Invalid params" (standard MCP code) |
| Downstream service exception | `500` JSON-RPC error `-32603`: "Internal error"; full stack logged |
| Proposal not owned by caller (`propose_pr_for_cve`) | `403` JSON-RPC error `-32001`: "Forbidden" |
| MCP request over HTTP but not a valid JSON-RPC envelope | Spring AI starter handles (returns framed error) |

All MCP errors use standard JSON-RPC error envelope shape — per the MCP spec.

## 9. Testing Strategy

- **Unit (Mockito):** `ApiKeyService`, `ApiKeyHasher`, `ApiKeyGenerator`, `ApiKeyAuthenticationFilter`.
- **Integration (Testcontainers, MockMvc):** Each MCP tool via HTTP JSON-RPC envelope; `ActionMcpToolsScopeIT` specifically exercises READ-key-rejected-from-WRITE-tool path; `ApiKeyResourceIT` covers create/list/revoke via REST with JWT.
- **Follow existing patterns:** `AbstractIntegrationTest` provides MySQL + Redis Testcontainers; auth-requiring ITs register + login a user to get a JWT (see `UserResourceIT.registerAndLogin(email)` pattern).

## 10. Out of Scope (deferred / explicitly rejected)

- **Native stdio transport** — dropped in favour of `mcp-remote` shim; documented in README.
- **Rate limiting** — API key auth is the gate; user controls blast radius.
- **Per-key audit log** — `last_used_at` is enough for MVP. Full audit deferred.
- **Key rotation helpers** — user revokes old key, creates new one. No automated rotation flow.
- **`bcrypt` for key_hash** — SHA-256 is sufficient given 190-bit entropy and is O(1) lookup; bcrypt would force table scan or a second lookup column.
- **Tool-level Redis caching** — MCP responses are always fresh per call. Cache added later only if latency becomes an issue.
