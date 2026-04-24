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
  const { data: summary, isLoading: summaryLoading, isError: summaryError } = useGetSummaryQuery();
  const { data: timeseries, isLoading: tsLoading, isError: tsError } = useGetTimeseriesQuery(days);

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
        {summaryError && (
          <Typography variant="body2" color="error" sx={{ mb: 4 }}>
            Failed to load metrics. Try refreshing the page.
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
            onChange={(_e, v) => { if (v !== null) setDays(v as number); }}
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
        {tsError && (
          <Typography variant="body2" color="error">Failed to load chart data.</Typography>
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
                    <Tooltip formatter={(v: unknown) => fmtNum(v as number)} labelFormatter={fmtDate} />
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
                    <Tooltip formatter={(v: unknown) => fmtMs(v as number)} labelFormatter={fmtDate} />
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
                    <Tooltip labelFormatter={fmtDate} formatter={(v: unknown) => v != null ? Number(v).toFixed(2) : "—"} />
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
