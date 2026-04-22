# Plan 9 — Observability Dashboard UI + API Key Management UI

**Status:** Draft
**Date:** 2026-04-22
**Depends on:** Plans 5 (eval harness + observability backend), 6 (MCP + API keys backend), 7-8 (frontend foundation + core product)

---

## 1. Overview

Two new frontend surfaces that complete the MVP:

1. **Public Observability Dashboard** — standalone page at `/observability`, no auth required. Portfolio showcase showing real production AI metrics.
2. **API Key Management** — authenticated page at `/app/settings/api-keys`, filling the "Settings (soon)" placeholder in the app shell sidebar.

Backend APIs already exist from Plans 5+6. This plan is frontend-only.

---

## 2. Observability Dashboard

### 2.1 Route & Layout

- **Route:** `/observability` — standalone, outside AppShell (no sidebar, no auth)
- **Layout:** Full-width page with Dev Radar branding header, metrics below
- **Responsive:** Single column on mobile, 2-column grid on desktop for summary cards
- **Theme:** Same monochrome palette as the rest of the app

### 2.2 Branding Header

Minimal top bar:
- "Dev Radar" wordmark (left) — links to `/`
- "Public Dashboard" label (right)
- No nav links, no user info

### 2.3 Summary Cards (top section)

A row of metric cards showing 24-hour snapshot. Data from `GET /api/observability/summary`.

| Card | Value | Subtitle |
|------|-------|----------|
| Radars Generated | `totalRadars24h` | "last 24 hours" |
| Tokens Used | `totalTokens24h` (formatted: "12.4k") | input/output breakdown below |
| Avg Latency | `avgGenerationMs24h` (formatted: "8.2s") | "p50: Xs · p95: Ys" subtitle |
| Cache Hit Rate | `cacheHitRate24h` (formatted: "73%") | "AI summary cache" |
| Eval: Relevance | `evalScoreRelevance` (formatted: "0.82") | "latest run" |
| Eval: Citations | `evalScoreCitations` | "latest run" |

Layout: 3 cards per row on desktop (lg), 2 on tablet (md), 1 on mobile (xs). Each card is a MUI Paper with the value in large text and subtitle in secondary text.

### 2.4 Timeseries Charts (main section)

Period toggle: **7d** | **30d** | **90d** (default 7d). Calls `GET /api/observability/timeseries?days=N`.

Four charts stacked vertically, each in its own Paper:

1. **Token Usage** — stacked area chart. X: date, Y: tokens. Two series: input tokens (dark), output tokens (lighter).
2. **Generation Latency** — line chart. X: date, Y: milliseconds. Three lines: p50, p95, avg.
3. **Ingestion** — bar chart. X: date, Y: count. Two series: items ingested, items deduped.
4. **Eval Scores** — line chart. X: date, Y: score (0-1). Three lines: relevance, citations, distinctness.

**Chart library:** Recharts. Lightweight, composable, React-native. No heavyweight BI framework.

**Empty states:** If no data for a period, show "No data for this period" centered in the chart area.

### 2.5 Data Fetching

New RTK Query API slice: `observabilityApi`
- `useGetSummaryQuery()` — polls every 60s (refetchOnMountOrArgChange)
- `useGetTimeseriesQuery(days)` — cached by days param

No auth header needed — these endpoints are public.

---

## 3. API Key Management

### 3.1 Route & Layout

- **Route:** `/app/settings/api-keys` — inside AppShell, requires auth
- **Nav:** The existing "Settings" item in the sidebar (currently disabled with "soon" badge) becomes active and links here
- **Layout:** Standard page layout matching RadarListPage/InterestPickerPage

### 3.2 Page Structure

**Header:** "API Keys" title, "Manage keys for MCP and API access." subtitle, "Create key" button (right).

**Key List:** Table or card list showing existing keys:

| Column | Source |
|--------|--------|
| Name | `name` |
| Scope | `scope` (READ/WRITE badge) |
| Key Prefix | `keyPrefix` (monospace, e.g. `sk_live_abc1...`) |
| Created | `createdAt` (relative: "3 days ago") |
| Last Used | `lastUsedAt` (relative, or "Never") |
| Actions | Revoke button (red text) |

**Empty state:** "No API keys yet. Create one to use Dev Radar from Claude Desktop or Cursor." with a "Create your first key" button.

### 3.3 Create Key Dialog

MUI Dialog triggered by "Create key" button:
- **Name** text field (required, max 100 chars)
- **Scope** toggle group: READ | WRITE (default READ)
- "Create" button

On success, show a **one-time key reveal**:
- Full key value in a monospace box with copy-to-clipboard button
- Warning text: "Copy this key now. You won't be able to see it again."
- "Done" button closes dialog and refetches key list

### 3.4 Revoke Key

Click "Revoke" on a key row → confirmation dialog:
- "Revoke key '{name}'? Any integrations using this key will stop working."
- "Cancel" | "Revoke" (destructive red)

On confirm: `DELETE /api/users/me/api-keys/{id}` → refetch list.

### 3.5 Data Fetching

New RTK Query API slice: `apiKeyApi`
- `useListApiKeysQuery()` — auto-refetch on mutations
- `useCreateApiKeyMutation()` — invalidates list cache
- `useRevokeApiKeyMutation()` — invalidates list cache

---

## 4. App Shell Changes

### 4.1 Settings Nav Item

Change the "Settings" sidebar item from disabled with "soon" badge to an active link pointing to `/app/settings/api-keys`.

### 4.2 Public Dashboard Link

Add a subtle link in the sidebar footer area: "Public dashboard →" linking to `/observability`. Opens in same tab (internal route).

---

## 5. New Dependencies

- **recharts** (`^2.15`) — chart library for observability timeseries

No other new dependencies.

---

## 6. File Plan

### New Files

```
frontend/src/api/observabilityApi.ts          — RTK Query slice (summary + timeseries)
frontend/src/api/apiKeyApi.ts                 — RTK Query slice (CRUD)
frontend/src/pages/ObservabilityPage.tsx       — public dashboard page
frontend/src/pages/ApiKeysPage.tsx             — settings page for key management
frontend/src/components/SummaryCard.tsx         — metric card for observability
frontend/src/components/TimeseriesChart.tsx     — reusable chart wrapper
frontend/src/components/CreateKeyDialog.tsx     — create key dialog
frontend/src/components/RevokeKeyDialog.tsx     — revoke confirmation dialog
frontend/src/components/KeyRevealBox.tsx        — one-time key display with copy
```

### Modified Files

```
frontend/src/App.tsx                           — add /observability route + /app/settings/api-keys route
frontend/src/pages/AppShell.tsx                — activate Settings nav, add dashboard link
frontend/src/store/index.ts                    — register new API slices
frontend/package.json                          — add recharts dependency
```

---

## 7. Non-Goals

- No Grafana/Prometheus UI — we build our own lightweight dashboard from the existing JSON API
- No eval triggering from the UI — eval runs are backend/CI only (Plan 5 scope)
- No MCP server configuration UI — MCP connection details can live in docs
- No settings beyond API keys — profile editing is not in MVP scope
