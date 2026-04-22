# Plan 9 — Observability Dashboard UI + API Key Management UI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build two frontend pages — a public observability dashboard and an authenticated API key management page — completing the MVP feature set.

**Architecture:** Frontend-only. Both backend APIs already exist (`/api/observability/*` and `/api/users/me/api-keys`). New RTK Query API slices call the existing endpoints. Recharts renders timeseries data. Both pages follow existing component patterns (PageHeader, Button, MUI Paper, monochrome theme).

**Tech Stack:** React 19, TypeScript, MUI 6, Redux Toolkit / RTK Query, Recharts 2.15, React Router 7

---

## File Plan

### New Files

```
frontend/src/api/observabilityApi.ts      — RTK Query slice for summary + timeseries (no auth)
frontend/src/api/apiKeyApi.ts             — RTK Query slice for key CRUD (auth)
frontend/src/pages/ObservabilityPage.tsx   — public dashboard (standalone route)
frontend/src/pages/ApiKeysPage.tsx         — authenticated key management page
frontend/src/components/SummaryCard.tsx     — metric card component
frontend/src/components/CreateKeyDialog.tsx — create key dialog with one-time reveal
frontend/src/components/RevokeKeyDialog.tsx — revoke confirmation dialog
```

### Modified Files

```
frontend/package.json                      — add recharts dependency
frontend/src/api/types.ts                  — add observability + API key types
frontend/src/store/index.ts                — register new API slices
frontend/src/App.tsx                       — add /observability + /app/settings/api-keys routes
frontend/src/pages/AppShell.tsx            — activate Settings nav, add dashboard link
```

---

### Task 1: Add recharts dependency and TypeScript types

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/api/types.ts`

- [ ] **Step 1: Install recharts**

```bash
cd frontend && npm install recharts@^2.15
```

- [ ] **Step 2: Add observability types to `types.ts`**

Append to `frontend/src/api/types.ts`:

```typescript
// ─── Observability ──────────────────────────────────────────────────

export interface ObservabilitySummary {
  totalRadars24h: number;
  totalTokens24h: number;
  totalTokensInput24h: number;
  totalTokensOutput24h: number;
  sonnetCalls24h: number;
  haikuCalls24h: number;
  p50Ms24h: number;
  p95Ms24h: number;
  avgGenerationMs24h: number;
  cacheHitRate24h: number;
  itemsIngested24h: number;
  evalScoreRelevance: number | null;
  evalScoreCitations: number | null;
  evalScoreDistinctness: number | null;
}

export interface MetricsDay {
  date: string; // "YYYY-MM-DD"
  totalRadars: number;
  totalTokensInput: number;
  totalTokensOutput: number;
  sonnetCalls: number;
  haikuCalls: number;
  cacheHits: number;
  cacheMisses: number;
  p50Ms: number;
  p95Ms: number;
  avgGenerationMs: number;
  itemsIngested: number;
  itemsDeduped: number;
  evalScoreRelevance: number | null;
  evalScoreCitations: number | null;
  evalScoreDistinctness: number | null;
}

// ─── API Keys ───────────────────────────────────────────────────────

export type ApiKeyScope = "READ" | "WRITE";

export interface ApiKeySummary {
  id: number;
  name: string;
  scope: ApiKeyScope;
  keyPrefix: string;
  createdAt: string;
  lastUsedAt: string | null;
}

export interface ApiKeyCreateRequest {
  name: string;
  scope: ApiKeyScope;
}

export interface ApiKeyCreateResponse {
  id: number;
  name: string;
  scope: ApiKeyScope;
  key: string;
  keyPrefix: string;
  createdAt: string;
}
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/api/types.ts
git commit -m "feat(frontend): add recharts + observability and API key types"
```

---

### Task 2: Observability RTK Query API slice

**Files:**
- Create: `frontend/src/api/observabilityApi.ts`
- Modify: `frontend/src/store/index.ts`

- [ ] **Step 1: Create `observabilityApi.ts`**

Create `frontend/src/api/observabilityApi.ts`:

```typescript
import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react";
import type { ObservabilitySummary, MetricsDay } from "./types";

const baseUrl =
  typeof window !== "undefined" && window.location?.origin
    ? `${window.location.origin}/`
    : "/";

export const observabilityApi = createApi({
  reducerPath: "observabilityApi",
  baseQuery: fetchBaseQuery({ baseUrl }),
  endpoints: (b) => ({
    getSummary: b.query<ObservabilitySummary, void>({
      query: () => ({ url: "/api/observability/summary" }),
    }),
    getTimeseries: b.query<MetricsDay[], number>({
      query: (days) => ({
        url: "/api/observability/timeseries",
        params: { days },
      }),
    }),
  }),
});

export const { useGetSummaryQuery, useGetTimeseriesQuery } = observabilityApi;
```

Note: This uses a plain `fetchBaseQuery` (no auth header injection) because the observability endpoints are public.

- [ ] **Step 2: Register in store**

In `frontend/src/store/index.ts`, add the import and wire up reducer + middleware:

```typescript
import { observabilityApi } from "../api/observabilityApi";
```

Add to `reducer`:
```typescript
[observabilityApi.reducerPath]: observabilityApi.reducer,
```

Add to `middleware`:
```typescript
.concat(observabilityApi.middleware)
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/observabilityApi.ts frontend/src/store/index.ts
git commit -m "feat(frontend): add observability RTK Query API slice"
```

---

### Task 3: API Key RTK Query API slice

**Files:**
- Create: `frontend/src/api/apiKeyApi.ts`
- Modify: `frontend/src/store/index.ts`

- [ ] **Step 1: Create `apiKeyApi.ts`**

Create `frontend/src/api/apiKeyApi.ts`:

```typescript
import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { ApiKeySummary, ApiKeyCreateRequest, ApiKeyCreateResponse } from "./types";

export const apiKeyApi = createApi({
  reducerPath: "apiKeyApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["ApiKey"],
  endpoints: (b) => ({
    list: b.query<ApiKeySummary[], void>({
      query: () => ({ url: "/api/users/me/api-keys" }),
      providesTags: ["ApiKey"],
    }),
    create: b.mutation<ApiKeyCreateResponse, ApiKeyCreateRequest>({
      query: (body) => ({
        url: "/api/users/me/api-keys",
        method: "POST",
        body,
      }),
      invalidatesTags: ["ApiKey"],
    }),
    revoke: b.mutation<void, number>({
      query: (id) => ({
        url: `/api/users/me/api-keys/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: ["ApiKey"],
    }),
  }),
});

export const {
  useListQuery: useListApiKeysQuery,
  useCreateMutation: useCreateApiKeyMutation,
  useRevokeMutation: useRevokeApiKeyMutation,
} = apiKeyApi;
```

- [ ] **Step 2: Register in store**

In `frontend/src/store/index.ts`, add the import and wire up:

```typescript
import { apiKeyApi } from "../api/apiKeyApi";
```

Add to `reducer`:
```typescript
[apiKeyApi.reducerPath]: apiKeyApi.reducer,
```

Add to `middleware`:
```typescript
.concat(apiKeyApi.middleware)
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/apiKeyApi.ts frontend/src/store/index.ts
git commit -m "feat(frontend): add API key RTK Query slice"
```

---

### Task 4: SummaryCard component

**Files:**
- Create: `frontend/src/components/SummaryCard.tsx`

- [ ] **Step 1: Create `SummaryCard.tsx`**

Create `frontend/src/components/SummaryCard.tsx`:

```typescript
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";

interface SummaryCardProps {
  label: string;
  value: string;
  sub?: string;
}

export function SummaryCard({ label, value, sub }: SummaryCardProps) {
  return (
    <Paper
      sx={{
        p: "20px 24px",
        border: 1,
        borderColor: "divider",
        borderRadius: 2,
      }}
    >
      <Typography
        sx={{
          fontSize: "0.8125rem",
          lineHeight: "20px",
          color: "text.secondary",
          mb: 0.5,
        }}
      >
        {label}
      </Typography>
      <Typography
        sx={{
          fontSize: "1.75rem",
          lineHeight: "36px",
          fontWeight: 500,
          letterSpacing: "-0.01em",
          color: "text.primary",
        }}
      >
        {value}
      </Typography>
      {sub && (
        <Typography
          sx={{
            fontSize: "0.8125rem",
            lineHeight: "20px",
            color: "text.secondary",
            mt: 0.5,
          }}
        >
          {sub}
        </Typography>
      )}
    </Paper>
  );
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/SummaryCard.tsx
git commit -m "feat(frontend): add SummaryCard component for observability metrics"
```

---

### Task 5: ObservabilityPage — full public dashboard

**Files:**
- Create: `frontend/src/pages/ObservabilityPage.tsx`

- [ ] **Step 1: Create `ObservabilityPage.tsx`**

Create `frontend/src/pages/ObservabilityPage.tsx`:

```typescript
import { useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Paper from "@mui/material/Paper";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import {
  AreaChart,
  Area,
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts";
import { SummaryCard } from "../components/SummaryCard";
import { useGetSummaryQuery, useGetTimeseriesQuery } from "../api/observabilityApi";

const INK = "#2d2a26";
const INK_LIGHT = "#6b655e";
const DIVIDER = "#e8e4df";

function fmtNum(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}k`;
  return String(n);
}

function fmtMs(ms: number): string {
  if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`;
  return `${ms}ms`;
}

function fmtPct(rate: number): string {
  return `${Math.round(rate * 100)}%`;
}

function fmtScore(v: number | null): string {
  if (v == null) return "—";
  return Number(v).toFixed(2);
}

function fmtDate(d: string): string {
  const parts = d.split("-");
  return `${parts[1]}/${parts[2]}`;
}

function ChartEmpty() {
  return (
    <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", height: 240 }}>
      <Typography sx={{ fontSize: "0.875rem", color: "text.secondary" }}>
        No data for this period
      </Typography>
    </Box>
  );
}

export function ObservabilityPage() {
  const [days, setDays] = useState(7);
  const { data: summary, isLoading: summaryLoading } = useGetSummaryQuery();
  const { data: timeseries, isLoading: tsLoading } = useGetTimeseriesQuery(days);

  const hasTs = timeseries && timeseries.length > 0;

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      {/* Header */}
      <Box
        sx={{
          px: { xs: 3, md: 6 },
          py: 3,
          borderBottom: 1,
          borderColor: "divider",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <Typography
          component="a"
          href="/"
          variant="overline"
          color="text.secondary"
          sx={{ textDecoration: "none" }}
        >
          Dev Radar
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Public Dashboard
        </Typography>
      </Box>

      <Box sx={{ maxWidth: 1120, mx: "auto", px: { xs: 3, md: 6 }, py: 5 }}>
        <Typography
          component="h1"
          sx={{
            fontSize: "2rem",
            lineHeight: "40px",
            fontWeight: 500,
            letterSpacing: "-0.01em",
            color: "text.primary",
            mb: 1,
          }}
        >
          Observability
        </Typography>
        <Typography sx={{ fontSize: "0.9375rem", color: "text.secondary", mb: 5 }}>
          Real-time production metrics. Anonymized, public.
        </Typography>

        {/* Summary cards */}
        {summaryLoading && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
            Loading metrics…
          </Typography>
        )}
        {summary && (
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: {
                xs: "1fr",
                sm: "repeat(2, 1fr)",
                lg: "repeat(3, 1fr)",
              },
              gap: 2,
              mb: 6,
            }}
          >
            <SummaryCard
              label="Radars Generated"
              value={String(summary.totalRadars24h)}
              sub="last 24 hours"
            />
            <SummaryCard
              label="Tokens Used"
              value={fmtNum(summary.totalTokens24h)}
              sub={`in: ${fmtNum(summary.totalTokensInput24h)} · out: ${fmtNum(summary.totalTokensOutput24h)}`}
            />
            <SummaryCard
              label="Avg Latency"
              value={fmtMs(summary.avgGenerationMs24h)}
              sub={`p50: ${fmtMs(summary.p50Ms24h)} · p95: ${fmtMs(summary.p95Ms24h)}`}
            />
            <SummaryCard
              label="Cache Hit Rate"
              value={fmtPct(summary.cacheHitRate24h)}
              sub="AI summary cache"
            />
            <SummaryCard
              label="Eval: Relevance"
              value={fmtScore(summary.evalScoreRelevance)}
              sub="latest run"
            />
            <SummaryCard
              label="Eval: Citations"
              value={fmtScore(summary.evalScoreCitations)}
              sub="latest run"
            />
          </Box>
        )}

        {/* Period toggle */}
        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 3 }}>
          <Typography variant="h3" color="text.primary">
            Trends
          </Typography>
          <ToggleButtonGroup
            value={days}
            exclusive
            onChange={(_e, v) => { if (v !== null) setDays(v); }}
            size="small"
          >
            <ToggleButton value={7} sx={{ px: 2, textTransform: "none", fontSize: "0.8125rem" }}>7d</ToggleButton>
            <ToggleButton value={30} sx={{ px: 2, textTransform: "none", fontSize: "0.8125rem" }}>30d</ToggleButton>
            <ToggleButton value={90} sx={{ px: 2, textTransform: "none", fontSize: "0.8125rem" }}>90d</ToggleButton>
          </ToggleButtonGroup>
        </Box>

        {tsLoading && (
          <Typography variant="body2" color="text.secondary">Loading charts…</Typography>
        )}

        {!tsLoading && (
          <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
            {/* Token Usage */}
            <Paper sx={{ p: 3, border: 1, borderColor: "divider" }}>
              <Typography variant="body2" sx={{ fontWeight: 500, mb: 2 }}>Token Usage</Typography>
              {!hasTs ? <ChartEmpty /> : (
                <ResponsiveContainer width="100%" height={240}>
                  <AreaChart data={timeseries}>
                    <CartesianGrid strokeDasharray="3 3" stroke={DIVIDER} />
                    <XAxis dataKey="date" tickFormatter={fmtDate} tick={{ fontSize: 12, fill: INK_LIGHT }} />
                    <YAxis tickFormatter={fmtNum} tick={{ fontSize: 12, fill: INK_LIGHT }} />
                    <Tooltip formatter={(v: number) => fmtNum(v)} labelFormatter={fmtDate} />
                    <Legend />
                    <Area type="monotone" dataKey="totalTokensInput" name="Input" stackId="1" fill={INK} fillOpacity={0.7} stroke={INK} />
                    <Area type="monotone" dataKey="totalTokensOutput" name="Output" stackId="1" fill={INK_LIGHT} fillOpacity={0.4} stroke={INK_LIGHT} />
                  </AreaChart>
                </ResponsiveContainer>
              )}
            </Paper>

            {/* Generation Latency */}
            <Paper sx={{ p: 3, border: 1, borderColor: "divider" }}>
              <Typography variant="body2" sx={{ fontWeight: 500, mb: 2 }}>Generation Latency</Typography>
              {!hasTs ? <ChartEmpty /> : (
                <ResponsiveContainer width="100%" height={240}>
                  <LineChart data={timeseries}>
                    <CartesianGrid strokeDasharray="3 3" stroke={DIVIDER} />
                    <XAxis dataKey="date" tickFormatter={fmtDate} tick={{ fontSize: 12, fill: INK_LIGHT }} />
                    <YAxis tickFormatter={fmtMs} tick={{ fontSize: 12, fill: INK_LIGHT }} />
                    <Tooltip formatter={(v: number) => fmtMs(v)} labelFormatter={fmtDate} />
                    <Legend />
                    <Line type="monotone" dataKey="p50Ms" name="p50" stroke={INK} strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="p95Ms" name="p95" stroke={INK_LIGHT} strokeWidth={2} dot={false} strokeDasharray="5 5" />
                    <Line type="monotone" dataKey="avgGenerationMs" name="Avg" stroke="#9e9a95" strokeWidth={1} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </Paper>

            {/* Ingestion */}
            <Paper sx={{ p: 3, border: 1, borderColor: "divider" }}>
              <Typography variant="body2" sx={{ fontWeight: 500, mb: 2 }}>Ingestion</Typography>
              {!hasTs ? <ChartEmpty /> : (
                <ResponsiveContainer width="100%" height={240}>
                  <BarChart data={timeseries}>
                    <CartesianGrid strokeDasharray="3 3" stroke={DIVIDER} />
                    <XAxis dataKey="date" tickFormatter={fmtDate} tick={{ fontSize: 12, fill: INK_LIGHT }} />
                    <YAxis tick={{ fontSize: 12, fill: INK_LIGHT }} />
                    <Tooltip labelFormatter={fmtDate} />
                    <Legend />
                    <Bar dataKey="itemsIngested" name="Ingested" fill={INK} radius={[2, 2, 0, 0]} />
                    <Bar dataKey="itemsDeduped" name="Deduped" fill={INK_LIGHT} radius={[2, 2, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </Paper>

            {/* Eval Scores */}
            <Paper sx={{ p: 3, border: 1, borderColor: "divider" }}>
              <Typography variant="body2" sx={{ fontWeight: 500, mb: 2 }}>Eval Scores</Typography>
              {!hasTs ? <ChartEmpty /> : (
                <ResponsiveContainer width="100%" height={240}>
                  <LineChart data={timeseries}>
                    <CartesianGrid strokeDasharray="3 3" stroke={DIVIDER} />
                    <XAxis dataKey="date" tickFormatter={fmtDate} tick={{ fontSize: 12, fill: INK_LIGHT }} />
                    <YAxis domain={[0, 1]} tick={{ fontSize: 12, fill: INK_LIGHT }} />
                    <Tooltip labelFormatter={fmtDate} formatter={(v: number) => v != null ? Number(v).toFixed(2) : "—"} />
                    <Legend />
                    <Line type="monotone" dataKey="evalScoreRelevance" name="Relevance" stroke={INK} strokeWidth={2} dot={false} connectNulls />
                    <Line type="monotone" dataKey="evalScoreCitations" name="Citations" stroke={INK_LIGHT} strokeWidth={2} dot={false} connectNulls />
                    <Line type="monotone" dataKey="evalScoreDistinctness" name="Distinctness" stroke="#9e9a95" strokeWidth={2} dot={false} connectNulls />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </Paper>
          </Box>
        )}
      </Box>
    </Box>
  );
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/ObservabilityPage.tsx
git commit -m "feat(frontend): add ObservabilityPage with summary cards and Recharts timeseries"
```

---

### Task 6: CreateKeyDialog and RevokeKeyDialog components

**Files:**
- Create: `frontend/src/components/CreateKeyDialog.tsx`
- Create: `frontend/src/components/RevokeKeyDialog.tsx`

- [ ] **Step 1: Create `CreateKeyDialog.tsx`**

Create `frontend/src/components/CreateKeyDialog.tsx`:

```typescript
import { useState } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import TextField from "@mui/material/TextField";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import { Button } from "./Button";
import { Alert } from "./Alert";
import { monoStack } from "../theme";
import { useCreateApiKeyMutation } from "../api/apiKeyApi";
import type { ApiKeyScope, ApiKeyCreateResponse } from "../api/types";

interface CreateKeyDialogProps {
  open: boolean;
  onClose: () => void;
}

export function CreateKeyDialog({ open, onClose }: CreateKeyDialogProps) {
  const [name, setName] = useState("");
  const [scope, setScope] = useState<ApiKeyScope>("READ");
  const [created, setCreated] = useState<ApiKeyCreateResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const [createKey, { isLoading, error }] = useCreateApiKeyMutation();

  function reset() {
    setName("");
    setScope("READ");
    setCreated(null);
    setCopied(false);
  }

  function handleClose() {
    reset();
    onClose();
  }

  async function handleCreate() {
    if (!name.trim()) return;
    const resp = await createKey({ name: name.trim(), scope }).unwrap().catch(() => null);
    if (resp) setCreated(resp);
  }

  async function handleCopy() {
    if (!created) return;
    await navigator.clipboard.writeText(created.key);
    setCopied(true);
  }

  if (created) {
    return (
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 500 }}>Key Created</DialogTitle>
        <DialogContent>
          <Typography sx={{ fontSize: "0.875rem", color: "text.secondary", mb: 2 }}>
            Copy this key now. You won't be able to see it again.
          </Typography>
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1,
              p: "12px 16px",
              bgcolor: "rgba(45,42,38,0.03)",
              border: 1,
              borderColor: "divider",
              borderRadius: 1,
              fontFamily: monoStack,
              fontSize: "0.8125rem",
              wordBreak: "break-all",
            }}
          >
            <Box sx={{ flex: 1 }}>{created.key}</Box>
            <IconButton onClick={handleCopy} size="small" title="Copy to clipboard">
              <Typography sx={{ fontSize: "0.75rem" }}>{copied ? "✓" : "Copy"}</Typography>
            </IconButton>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button variant="outlined" onClick={handleClose}>Done</Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 500 }}>Create API Key</DialogTitle>
      <DialogContent>
        {error && (
          <Box sx={{ mb: 2 }}>
            <Alert severity="error">Failed to create key. Try again.</Alert>
          </Box>
        )}
        <TextField
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          fullWidth
          placeholder="e.g. Claude Desktop"
          inputProps={{ maxLength: 100 }}
          sx={{ mb: 3, mt: 1 }}
        />
        <Typography sx={{ fontSize: "0.8125rem", fontWeight: 500, color: "text.primary", mb: 1 }}>
          Scope
        </Typography>
        <ToggleButtonGroup
          value={scope}
          exclusive
          onChange={(_e, v) => { if (v) setScope(v as ApiKeyScope); }}
          size="small"
        >
          <ToggleButton value="READ" sx={{ px: 3, textTransform: "none" }}>Read</ToggleButton>
          <ToggleButton value="WRITE" sx={{ px: 3, textTransform: "none" }}>Write</ToggleButton>
        </ToggleButtonGroup>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button variant="text" onClick={handleClose}>Cancel</Button>
        <Button onClick={handleCreate} disabled={!name.trim() || isLoading}>
          {isLoading ? "Creating…" : "Create"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
```

- [ ] **Step 2: Create `RevokeKeyDialog.tsx`**

Create `frontend/src/components/RevokeKeyDialog.tsx`:

```typescript
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import { Button } from "./Button";
import { Alert } from "./Alert";
import { useRevokeApiKeyMutation } from "../api/apiKeyApi";

interface RevokeKeyDialogProps {
  open: boolean;
  keyId: number | null;
  keyName: string;
  onClose: () => void;
}

export function RevokeKeyDialog({ open, keyId, keyName, onClose }: RevokeKeyDialogProps) {
  const [revoke, { isLoading, error }] = useRevokeApiKeyMutation();

  async function handleRevoke() {
    if (keyId == null) return;
    await revoke(keyId).unwrap().catch(() => null);
    onClose();
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 500 }}>Revoke Key</DialogTitle>
      <DialogContent>
        {error && (
          <Box sx={{ mb: 2 }}>
            <Alert severity="error">Failed to revoke key.</Alert>
          </Box>
        )}
        <Typography sx={{ fontSize: "0.875rem", color: "text.secondary" }}>
          Revoke key "{keyName}"? Any integrations using this key will stop working.
        </Typography>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button variant="text" onClick={onClose}>Cancel</Button>
        <Button color="error" onClick={handleRevoke} disabled={isLoading}>
          {isLoading ? "Revoking…" : "Revoke"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/CreateKeyDialog.tsx frontend/src/components/RevokeKeyDialog.tsx
git commit -m "feat(frontend): add CreateKeyDialog and RevokeKeyDialog components"
```

---

### Task 7: ApiKeysPage

**Files:**
- Create: `frontend/src/pages/ApiKeysPage.tsx`

- [ ] **Step 1: Create `ApiKeysPage.tsx`**

Create `frontend/src/pages/ApiKeysPage.tsx`:

```typescript
import { useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { PageHeader } from "../components/PageHeader";
import { Button } from "../components/Button";
import { CreateKeyDialog } from "../components/CreateKeyDialog";
import { RevokeKeyDialog } from "../components/RevokeKeyDialog";
import { monoStack, serifStack } from "../theme";
import { useListApiKeysQuery } from "../api/apiKeyApi";

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  return `${days}d ago`;
}

export function ApiKeysPage() {
  const { data: keys, isLoading } = useListApiKeysQuery();
  const [createOpen, setCreateOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<{ id: number; name: string } | null>(null);

  const hasKeys = keys && keys.length > 0;

  return (
    <Box sx={{ maxWidth: 960, width: "100%" }}>
      <PageHeader
        title="API Keys"
        sub="Manage keys for MCP and API access."
        right={<Button onClick={() => setCreateOpen(true)}>Create key</Button>}
      />

      {isLoading && (
        <Typography variant="body2" color="text.secondary">Loading keys…</Typography>
      )}

      {!isLoading && !hasKeys && (
        <Box
          sx={{
            padding: "80px 24px",
            textAlign: "center",
            border: "1px dashed",
            borderColor: "divider",
            borderRadius: 3,
            bgcolor: "background.paper",
          }}
        >
          <Box
            sx={{
              fontFamily: serifStack,
              fontSize: 20,
              lineHeight: "28px",
              fontStyle: "italic",
              color: "text.primary",
              mb: 1,
            }}
          >
            No API keys yet.
          </Box>
          <Typography sx={{ fontSize: 14, color: "text.secondary", mb: 3, lineHeight: "22px" }}>
            Create one to use Dev Radar from Claude Desktop or Cursor.
          </Typography>
          <Button onClick={() => setCreateOpen(true)}>Create your first key</Button>
        </Box>
      )}

      {hasKeys && (
        <Box sx={{ borderTop: 1, borderColor: "divider" }}>
          {keys.map((k) => (
            <Box
              key={k.id}
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 2,
                py: "14px",
                px: 1,
                borderBottom: 1,
                borderColor: "divider",
                flexWrap: "wrap",
              }}
            >
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography sx={{ fontSize: "0.9375rem", fontWeight: 500, color: "text.primary" }}>
                  {k.name}
                </Typography>
                <Box sx={{ display: "flex", gap: 2, mt: 0.5, flexWrap: "wrap" }}>
                  <Typography sx={{ fontFamily: monoStack, fontSize: "0.8125rem", color: "text.secondary" }}>
                    {k.keyPrefix}
                  </Typography>
                  <Typography
                    sx={{
                      fontSize: "0.6875rem",
                      textTransform: "uppercase",
                      letterSpacing: "0.06em",
                      fontWeight: 500,
                      color: k.scope === "WRITE" ? "text.primary" : "text.secondary",
                      bgcolor: "rgba(45,42,38,0.04)",
                      px: 1,
                      py: "2px",
                      borderRadius: 1,
                    }}
                  >
                    {k.scope}
                  </Typography>
                </Box>
              </Box>
              <Box sx={{ textAlign: "right", flexShrink: 0 }}>
                <Typography sx={{ fontSize: "0.8125rem", color: "text.secondary" }}>
                  Created {timeAgo(k.createdAt)}
                </Typography>
                <Typography sx={{ fontSize: "0.8125rem", color: "text.secondary" }}>
                  {k.lastUsedAt ? `Used ${timeAgo(k.lastUsedAt)}` : "Never used"}
                </Typography>
              </Box>
              <Box
                component="button"
                onClick={() => setRevokeTarget({ id: k.id, name: k.name })}
                sx={{
                  background: "transparent",
                  border: "none",
                  padding: "4px 8px",
                  fontFamily: "inherit",
                  fontSize: "0.8125rem",
                  color: "error.main",
                  cursor: "pointer",
                  borderRadius: 1,
                  "&:hover": { bgcolor: "rgba(179,38,30,0.04)" },
                }}
              >
                Revoke
              </Box>
            </Box>
          ))}
        </Box>
      )}

      <CreateKeyDialog open={createOpen} onClose={() => setCreateOpen(false)} />
      <RevokeKeyDialog
        open={revokeTarget !== null}
        keyId={revokeTarget?.id ?? null}
        keyName={revokeTarget?.name ?? ""}
        onClose={() => setRevokeTarget(null)}
      />
    </Box>
  );
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/ApiKeysPage.tsx
git commit -m "feat(frontend): add ApiKeysPage with key list, create, and revoke"
```

---

### Task 8: Wire routes and update AppShell

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/pages/AppShell.tsx`

- [ ] **Step 1: Add routes to `App.tsx`**

In `frontend/src/App.tsx`:

Add imports at top:
```typescript
import { ObservabilityPage } from "./pages/ObservabilityPage";
import { ApiKeysPage } from "./pages/ApiKeysPage";
```

Add `/observability` route as a standalone public route (outside the ProtectedRoute) — add it right after the `/auth/github/complete` route:
```typescript
<Route path="/observability" element={<ObservabilityPage />} />
```

Add the settings route inside the `<Route path="/app" element={<AppShell />}>` block, after the interests route:
```typescript
<Route path="settings/api-keys" element={<ApiKeysPage />} />
```

- [ ] **Step 2: Update AppShell — activate Settings nav link**

In `frontend/src/pages/AppShell.tsx`, change the NAV_ITEMS array. Replace the disabled Settings item:

```typescript
{ key: "settings", label: "Settings", disabled: true },
```

with:

```typescript
{ key: "settings", label: "Settings", to: "/app/settings/api-keys" },
```

- [ ] **Step 3: Add public dashboard link to sidebar footer**

In `frontend/src/pages/AppShell.tsx`, add `Link` to the imports from react-router-dom:

```typescript
import { NavLink, Outlet, useLocation, Link } from "react-router-dom";
```

In the desktop sidebar, right before the user info `<Box>` (the one with `pt: 2.5, borderTop: 1`), add:

```tsx
<Box sx={{ mb: 2 }}>
  <Box
    component={Link}
    to="/observability"
    sx={{
      display: "block",
      fontSize: "0.8125rem",
      color: "text.secondary",
      textDecoration: "none",
      "&:hover": { color: "text.primary" },
    }}
  >
    Public dashboard →
  </Box>
</Box>
```

- [ ] **Step 4: Verify TypeScript compiles and dev server runs**

```bash
cd frontend && npx tsc --noEmit
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx frontend/src/pages/AppShell.tsx
git commit -m "feat(frontend): wire /observability and /app/settings/api-keys routes, activate Settings nav"
```

---

### Task 9: Manual smoke test

- [ ] **Step 1: Start backend and frontend**

```bash
cd backend && DB_HOST_PORT=3307 GOOGLE_AI_API_KEY="..." mvn spring-boot:run -Dspring-boot.run.profiles=gemini &
cd frontend && npm run dev
```

- [ ] **Step 2: Test observability page**

Navigate to `http://localhost:5173/observability`:
- Summary cards should render (may show 0s if no recent activity)
- Period toggle (7d/30d/90d) should switch chart data
- All four chart sections should appear (may show "No data for this period")

- [ ] **Step 3: Test API keys page**

Log in, click "Settings" in sidebar:
- Should navigate to `/app/settings/api-keys`
- Empty state should show "No API keys yet"
- Click "Create your first key" → dialog opens
- Enter name, pick scope, click Create → key revealed
- Copy key, close dialog → key appears in list
- Click Revoke → confirmation dialog → key removed

- [ ] **Step 4: Test sidebar changes**

- "Settings" nav item should be active (not "soon")
- "Public dashboard →" link should appear in sidebar footer
- Clicking it navigates to `/observability`

- [ ] **Step 5: Verify no TypeScript or lint errors**

```bash
cd frontend && npx tsc --noEmit && npm run lint
```
