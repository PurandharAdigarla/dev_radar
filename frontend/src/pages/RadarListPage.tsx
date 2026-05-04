import { useState, useCallback, useEffect } from "react";
import { Link as RouterLink } from "react-router-dom";
import { useDispatch } from "react-redux";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Skeleton from "@mui/material/Skeleton";
import TextField from "@mui/material/TextField";
import IconButton from "@mui/material/IconButton";
import LinearProgress from "@mui/material/LinearProgress";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import AddIcon from "@mui/icons-material/Add";
import CloseIcon from "@mui/icons-material/Close";
import { keyframes } from "@mui/system";
import { motion, AnimatePresence } from "framer-motion";
import { Button } from "../components/Button";
import { Alert } from "../components/Alert";
import { StatusTag } from "../components/StatusTag";
import { PulseDot } from "../components/PulseDot";
import { colors } from "../theme";
import { useCreateRadarMutation, useListRadarsQuery, radarApi } from "../api/radarApi";
import { useGetTopicsQuery, useSetTopicsMutation } from "../api/topicApi";
import { useRadarStream } from "../radar/useRadarStream";
import { generationStarted, generationFinished } from "../radar/radarGenerationSlice";
import type { AppDispatch } from "../store";
import type { RadarSummary } from "../api/types";

const MAX_TOPICS = 15;

const SUGGESTIONS = [
  "MCP Servers", "Claude Code Skills", "AI Agent Frameworks",
  "LLM Prompt Engineering", "RAG Pipelines", "Agentic Coding",
  "Tool Use Patterns", "Multi-Agent Systems", "AI Code Review",
  "Vector Databases", "LLM Fine-tuning", "AI Testing",
];

/* ── helpers ─────────────────────────────────────────────────── */

function formatDate(iso: string | null): string {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function formatDateRange(start: string, end: string): string {
  const s = new Date(start).toLocaleDateString("en-US", { month: "short", day: "numeric" });
  const e = new Date(end).toLocaleDateString("en-US", { month: "short", day: "numeric" });
  return `${s} – ${e}`;
}

/* ── topics bar ──────────────────────────────────────────────── */

function formatCountdown(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

function useCountdown(cooldownUntil: number | null) {
  const [remaining, setRemaining] = useState(() =>
    cooldownUntil ? Math.max(0, cooldownUntil - Date.now()) : 0,
  );

  useEffect(() => {
    if (!cooldownUntil) { setRemaining(0); return; }
    const update = () => setRemaining(Math.max(0, cooldownUntil - Date.now()));
    update();
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, [cooldownUntil]);

  return remaining;
}

function TopicsBar({
  onGenerate,
  canGenerate,
  generating,
  cooldownUntil,
}: {
  onGenerate: () => void;
  canGenerate: boolean;
  generating: boolean;
  cooldownUntil: number | null;
}) {
  const cooldownRemaining = useCountdown(cooldownUntil);
  const onCooldown = cooldownRemaining > 0;
  const { data: topics = [] } = useGetTopicsQuery();
  const [setTopics, { isLoading: isSaving }] = useSetTopicsMutation();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [localTopics, setLocalTopics] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const current = localTopics ?? topics.map((t) => t.topic);
  const dirty = localTopics !== null;

  const addTopic = useCallback(
    (name: string) => {
      const trimmed = name.trim();
      if (!trimmed || current.length >= MAX_TOPICS) return;
      if (current.some((t) => t.toLowerCase() === trimmed.toLowerCase())) return;
      setLocalTopics([...current, trimmed]);
      setDraft("");
    },
    [current],
  );

  const removeTopic = (idx: number) => {
    setLocalTopics(current.filter((_, i) => i !== idx));
  };

  async function save() {
    setError(null);
    try {
      await setTopics({ topics: current }).unwrap();
      setLocalTopics(null);
      setEditing(false);
    } catch {
      setError("Failed to save topics.");
    }
  }

  function cancel() {
    setLocalTopics(null);
    setEditing(false);
    setError(null);
  }

  return (
    <Box
      sx={{
        bgcolor: colors.bgPaper,
        border: `1px solid ${colors.divider}`,
        borderRadius: "10px",
        p: 2,
        mb: 2,
      }}
    >
      {error && (
        <Box sx={{ mb: 1.5 }}>
          <Alert severity="error">{error}</Alert>
        </Box>
      )}

      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: editing ? 1.5 : 0 }}>
        {/* Topic chips */}
        <Box sx={{ display: "flex", alignItems: "center", gap: 0.75, flexWrap: "wrap", flex: 1, mr: 2 }}>
          <Typography sx={{ fontSize: "0.6875rem", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.1em", color: colors.textMuted, mr: 0.5 }}>
            Topics
          </Typography>
          {current.length === 0 && !editing && (
            <Typography sx={{ fontSize: "0.8125rem", color: colors.textMuted }}>
              None yet
            </Typography>
          )}
          <AnimatePresence>
            {current.map((t, i) => (
              <motion.div
                key={t}
                initial={{ opacity: 0, scale: 0.85 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.85 }}
                transition={{ duration: 0.15 }}
              >
                <Box
                  sx={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 0.5,
                    height: 26,
                    px: 1,
                    borderRadius: "6px",
                    fontSize: "0.75rem",
                    fontWeight: 500,
                    bgcolor: colors.bgSubtle,
                    color: colors.textSecondary,
                    border: `1px solid ${colors.divider}`,
                  }}
                >
                  {t}
                  {editing && (
                    <CloseIcon
                      onClick={() => removeTopic(i)}
                      sx={{ fontSize: 14, cursor: "pointer", color: colors.textMuted, "&:hover": { color: colors.text } }}
                    />
                  )}
                </Box>
              </motion.div>
            ))}
          </AnimatePresence>
          {!editing && (
            <Box
              component="button"
              onClick={() => setEditing(true)}
              sx={{
                background: "none",
                border: `1px dashed ${colors.divider}`,
                borderRadius: "6px",
                height: 26,
                px: 1,
                fontSize: "0.75rem",
                fontWeight: 500,
                color: colors.textMuted,
                cursor: "pointer",
                display: "inline-flex",
                alignItems: "center",
                gap: 0.25,
                fontFamily: "inherit",
                "&:hover": { borderColor: colors.primary, color: colors.primary },
              }}
            >
              <AddIcon sx={{ fontSize: 14 }} /> Edit
            </Box>
          )}
        </Box>

        {/* Generate button */}
        {!editing && (
          <Box sx={{ position: "relative", flexShrink: 0 }}>
            <Button
              onClick={onGenerate}
              disabled={!canGenerate || generating || onCooldown}
              size="small"
              title={
                onCooldown
                  ? `Cannot generate new radar until ${formatCountdown(cooldownRemaining)}`
                  : !canGenerate
                    ? "Add topics first"
                    : undefined
              }
              sx={onCooldown ? { opacity: 0.5 } : undefined}
            >
              {generating
                ? "Starting..."
                : onCooldown
                  ? `Generate Radar · ${formatCountdown(cooldownRemaining)}`
                  : "Generate Radar"}
            </Button>
          </Box>
        )}
      </Box>

      {/* Editing mode */}
      {editing && (
        <Box>
          {/* Input row */}
          <Box sx={{ display: "flex", gap: 1, mb: 1.5 }}>
            <TextField
              size="small"
              placeholder="Type a topic and press Enter"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  addTopic(draft);
                }
              }}
              disabled={current.length >= MAX_TOPICS}
              sx={{ flex: 1, "& .MuiOutlinedInput-root": { borderRadius: "8px" } }}
              InputProps={{
                sx: { fontSize: "0.8125rem", py: 0 },
              }}
            />
            <IconButton
              onClick={() => addTopic(draft)}
              disabled={!draft.trim() || current.length >= MAX_TOPICS}
              size="small"
              sx={{ border: `1px solid ${colors.divider}`, borderRadius: "8px", width: 36, height: 36 }}
            >
              <AddIcon sx={{ fontSize: 18 }} />
            </IconButton>
          </Box>

          {/* Suggestions */}
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5, mb: 1.5 }}>
            {SUGGESTIONS.filter((s) => !current.some((c) => c.toLowerCase() === s.toLowerCase())).map((s) => (
              <Box
                key={s}
                component="button"
                onClick={() => addTopic(s)}
                sx={{
                  background: "none",
                  border: `1px solid ${colors.divider}`,
                  borderRadius: "6px",
                  height: 26,
                  px: 1,
                  fontSize: "0.6875rem",
                  fontWeight: 500,
                  color: colors.textMuted,
                  cursor: "pointer",
                  fontFamily: "inherit",
                  transition: "all 0.15s ease",
                  "&:hover": { bgcolor: colors.bgSubtle, borderColor: colors.primary, color: colors.primary },
                }}
              >
                + {s}
              </Box>
            ))}
          </Box>

          {/* Save / Cancel */}
          <Box sx={{ display: "flex", gap: 1, justifyContent: "flex-end" }}>
            <Button variant="text" size="small" onClick={cancel}>
              Cancel
            </Button>
            <Button size="small" onClick={save} disabled={!dirty || isSaving}>
              {isSaving ? "Saving..." : "Save"}
            </Button>
          </Box>
        </Box>
      )}
    </Box>
  );
}

/* ── generation progress ────────────────────────────────────── */

const pulse = keyframes`
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
`;

function GenerationProgress({ radarId }: { radarId: number }) {
  const dispatch = useDispatch<AppDispatch>();
  const stream = useRadarStream(radarId, true);
  const { activities, themes, status, error } = stream;
  const latestActivity = activities[activities.length - 1];

  useEffect(() => {
    dispatch(generationStarted({ radarId, startedAt: new Date().toISOString() }));
    return () => { dispatch(generationFinished()); };
  }, [dispatch, radarId]);

  useEffect(() => {
    if (status === "complete" || status === "failed") {
      dispatch(radarApi.util.invalidateTags(["Radar", "RadarList"]));
      dispatch(generationFinished());
    }
  }, [status, dispatch]);

  if (status === "complete") return null;

  return (
    <motion.div
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: "auto" }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ duration: 0.3 }}
    >
      <Box
        sx={{
          bgcolor: colors.bgPaper,
          border: `1px solid ${colors.divider}`,
          borderRadius: "10px",
          p: 2,
          mb: 2,
          overflow: "hidden",
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1.5 }}>
          <PulseDot color={colors.warning} />
          <Typography sx={{ fontSize: "0.8125rem", fontWeight: 600, color: colors.text }}>
            Generating your radar...
          </Typography>
          {themes.length > 0 && (
            <Typography sx={{ fontSize: "0.75rem", color: colors.textMuted }}>
              {themes.length} theme{themes.length !== 1 ? "s" : ""} found
            </Typography>
          )}
        </Box>

        <LinearProgress
          variant="indeterminate"
          sx={{
            height: 3,
            borderRadius: 2,
            mb: 1.5,
            bgcolor: "rgba(87,83,78,0.08)",
            "& .MuiLinearProgress-bar": { borderRadius: 2, bgcolor: colors.primary },
          }}
        />

        {status === "failed" && error && (
          <Box sx={{ mb: 1 }}>
            <Alert severity="error">{error}</Alert>
          </Box>
        )}

        {/* Live activity log */}
        <Box sx={{ display: "flex", flexDirection: "column", gap: 0.75 }}>
          {activities.slice(-5).map((a) => (
            <Box key={a.id} sx={{ display: "flex", alignItems: "flex-start", gap: 1 }}>
              <Box
                sx={{
                  width: 6,
                  height: 6,
                  borderRadius: "50%",
                  bgcolor: colors.textMuted,
                  mt: "6px",
                  flexShrink: 0,
                  animation: a === latestActivity ? `${pulse} 1.5s ease-in-out infinite` : undefined,
                }}
              />
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography sx={{ fontSize: "0.75rem", color: colors.textSecondary }}>
                  {a.phase === "research" ? "Researching" : "Discovering"}: {a.queries.join(", ")}
                </Typography>
                {a.results.length > 0 && (
                  <Typography sx={{ fontSize: "0.6875rem", color: colors.textMuted }}>
                    {a.results.length} source{a.results.length !== 1 ? "s" : ""} found
                  </Typography>
                )}
              </Box>
            </Box>
          ))}

          {activities.length === 0 && status !== "failed" && (
            <Typography sx={{ fontSize: "0.75rem", color: colors.textMuted, animation: `${pulse} 1.5s ease-in-out infinite` }}>
              Starting AI agent...
            </Typography>
          )}

          {themes.length > 0 && (
            <Box sx={{ mt: 0.5, display: "flex", flexWrap: "wrap", gap: 0.5 }}>
              {themes.map((t) => (
                <Box
                  key={t.themeId}
                  sx={{
                    px: 1,
                    py: 0.25,
                    borderRadius: "6px",
                    fontSize: "0.6875rem",
                    fontWeight: 500,
                    bgcolor: colors.bgSubtle,
                    color: colors.textSecondary,
                    border: `1px solid ${colors.divider}`,
                  }}
                >
                  {t.title}
                </Box>
              ))}
            </Box>
          )}
        </Box>
      </Box>
    </motion.div>
  );
}

/* ── radar row ───────────────────────────────────────────────── */

function RadarRow({ radar }: { radar: RadarSummary }) {
  const seconds = radar.generationMs != null ? (radar.generationMs / 1000).toFixed(0) : null;
  const isReady = radar.status === "READY";

  const content = (
    <>
      <StatusTag status={radar.status} />

      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography sx={{ fontSize: "0.875rem", fontWeight: 600, color: colors.text }}>
          {formatDate(radar.generatedAt) || "Generating..."}
        </Typography>
        {radar.periodStart && radar.periodEnd && (
          <Typography sx={{ fontSize: "0.75rem", color: colors.textMuted }}>
            {formatDateRange(radar.periodStart, radar.periodEnd)}
          </Typography>
        )}
      </Box>

      {isReady && (
        <Typography sx={{ fontSize: "0.75rem", color: colors.textMuted, fontVariantNumeric: "tabular-nums", flexShrink: 0 }}>
          {radar.themeCount ?? 0} themes{seconds ? ` · ${seconds}s` : ""}
        </Typography>
      )}

      {isReady && (
        <ArrowForwardIcon
          className="row-arrow"
          sx={{
            fontSize: 14,
            color: colors.textMuted,
            opacity: 0,
            transform: "translateX(-4px)",
            transition: "all 0.15s ease",
            flexShrink: 0,
          }}
        />
      )}
    </>
  );

  const rowSx = {
    display: "flex",
    alignItems: "center",
    gap: 2,
    py: 1.5,
    px: 2,
    textDecoration: "none",
    color: "inherit",
    borderBottom: `1px solid ${colors.divider}`,
    transition: "all 0.15s ease",
    "&:last-child": { borderBottom: "none" },
    ...(isReady && {
      "&:hover": {
        bgcolor: "rgba(87,83,78,0.03)",
        "& .row-arrow": { opacity: 1, transform: "translateX(0)" },
      },
    }),
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
      layout
    >
      {isReady ? (
        <Box component={RouterLink} to={`/app/radars/${radar.id}`} sx={rowSx}>
          {content}
        </Box>
      ) : (
        <Box sx={rowSx}>
          {content}
        </Box>
      )}
    </motion.div>
  );
}

/* ── main page ───────────────────────────────────────────────── */

export function RadarListPage() {
  const { data: page, isLoading } = useListRadarsQuery({ page: 0, size: 20 });
  const { data: topics } = useGetTopicsQuery();
  const [createRadar, createState] = useCreateRadarMutation();

  const hasTopics = (topics?.length ?? 0) > 0;
  const allRadars = page?.content ?? [];
  const radars = allRadars.filter((r) => r.status !== "FAILED" && r.status !== "GENERATING");
  const generatingRadar = allRadars.find((r) => r.status === "GENERATING");
  const hasGenerating = !!generatingRadar;

  const latestReadyAt = allRadars
    .filter((r) => r.status === "READY" && r.generatedAt)
    .map((r) => new Date(r.generatedAt!).getTime())
    .sort((a, b) => b - a)[0] ?? null;
  const cooldownUntil = latestReadyAt ? latestReadyAt + 24 * 60 * 60 * 1000 : null;
  const onCooldown = cooldownUntil !== null && cooldownUntil > Date.now();

  const canGenerate = hasTopics && !hasGenerating && !onCooldown;

  const [generateError, setGenerateError] = useState<string | null>(null);

  async function onGenerate() {
    if (!canGenerate) return;
    setGenerateError(null);
    try {
      await createRadar().unwrap();
    } catch (err: unknown) {
      const msg = (err as { data?: { message?: string } })?.data?.message;
      setGenerateError(msg || "Couldn't start a new radar. Try again.");
    }
  }

  return (
    <Box>
      {/* Topics bar */}
      <TopicsBar onGenerate={onGenerate} canGenerate={canGenerate} generating={createState.isLoading} cooldownUntil={onCooldown ? cooldownUntil : null} />

      {generateError && (
        <Box sx={{ mb: 2 }}>
          <Alert severity="error">{generateError}</Alert>
        </Box>
      )}

      {/* Generation progress */}
      <AnimatePresence>
        {generatingRadar && <GenerationProgress radarId={generatingRadar.id} />}
      </AnimatePresence>

      {/* Radars list */}
      <Box
        sx={{
          bgcolor: colors.bgPaper,
          border: `1px solid ${colors.divider}`,
          borderRadius: "10px",
          overflow: "hidden",
        }}
      >
        <Box sx={{ px: 2, py: 1.25, borderBottom: `1px solid ${colors.divider}` }}>
          <Typography sx={{ fontSize: "0.6875rem", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.1em", color: colors.textMuted }}>
            Radars
          </Typography>
        </Box>

        {isLoading && (
          <Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 1.5 }}>
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} variant="rounded" height={48} sx={{ borderRadius: "8px" }} />
            ))}
          </Box>
        )}

        {!isLoading && radars.length === 0 && (
          <Box sx={{ py: 6, textAlign: "center" }}>
            <Typography sx={{ fontSize: "0.875rem", color: colors.textMuted, mb: 0.5 }}>
              No radars generated yet.
            </Typography>
            <Typography sx={{ fontSize: "0.8125rem", color: colors.textMuted }}>
              {hasTopics ? "Click \"Generate Radar\" above to get started." : "Add topics first, then generate your radar."}
            </Typography>
          </Box>
        )}

        {radars.length > 0 && (
          <AnimatePresence>
            {radars.map((r) => (
              <RadarRow key={r.id} radar={r} />
            ))}
          </AnimatePresence>
        )}
      </Box>
    </Box>
  );
}
