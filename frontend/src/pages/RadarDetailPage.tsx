import { useEffect, useMemo, useState, useCallback } from "react";
import { useNavigate, useParams, Link as RouterLink } from "react-router-dom";
import { useDispatch } from "react-redux";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Snackbar from "@mui/material/Snackbar";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ShareOutlined from "@mui/icons-material/ShareOutlined";
import { motion } from "framer-motion";
import { ActivityFeed } from "../components/ActivityFeed";
import { Button } from "../components/Button";
import { PulseDot } from "../components/PulseDot";
import { RepoSection } from "../components/RepoSection";
import { StatusTag } from "../components/StatusTag";
import { ThemeCard } from "../components/ThemeCard";
import { ThemeSkeleton } from "../components/ThemeSkeleton";
import { Alert } from "../components/Alert";
import { colors, fonts } from "../theme";
import { useGetRadarQuery, useShareRadarMutation, radarApi } from "../api/radarApi";
import { useGetTopicsQuery } from "../api/topicApi";
import { useRadarStream } from "../radar/useRadarStream";
import { generationFinished, generationStarted } from "../radar/radarGenerationSlice";
import type { AppDispatch } from "../store";
import type { RadarTheme } from "../api/types";

function formatDate(iso: string | null): string {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}

/* ── animation variants ───────────────────────────────────────── */

const themeContainer = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.12, delayChildren: 0.2 },
  },
};

export function RadarDetailPage() {
  const params = useParams<{ id: string }>();
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const radarId = Number(params.id);

  const { data: radar, error } = useGetRadarQuery(radarId, { skip: !radarId });
  const { data: topics } = useGetTopicsQuery();
  const [shareRadar] = useShareRadarMutation();
  const [shareSnackbar, setShareSnackbar] = useState<string | null>(null);

  const handleShare = useCallback(async () => {
    try {
      const result = await shareRadar(radarId).unwrap();
      await navigator.clipboard.writeText(result.shareUrl);
      setShareSnackbar("Share link copied to clipboard!");
    } catch {
      setShareSnackbar("Failed to generate share link.");
    }
  }, [shareRadar, radarId]);

  const isGenerating = radar?.status === "GENERATING";
  const stream = useRadarStream(radarId, isGenerating);
  const streaming = isGenerating && stream.status !== "complete" && stream.status !== "failed";

  useEffect(() => {
    if (isGenerating) {
      dispatch(generationStarted({ radarId, startedAt: new Date().toISOString() }));
    } else {
      dispatch(generationFinished());
    }
  }, [dispatch, radarId, isGenerating]);

  useEffect(() => {
    return () => {
      dispatch(generationFinished());
    };
  }, [dispatch]);

  useEffect(() => {
    if (stream.status === "complete" || stream.status === "failed") {
      dispatch(radarApi.util.invalidateTags([{ type: "Radar", id: radarId }]));
    }
  }, [stream.status, dispatch, radarId]);

  useEffect(() => {
    if (error && "status" in error && (error.status === 404 || error.status === 403)) {
      navigate("/app/radars", { replace: true });
    }
  }, [error, navigate]);

  const themes: RadarTheme[] = useMemo(() => {
    const persisted = radar?.themes ?? [];
    if (!isGenerating) return persisted;
    const persistedIds = new Set(persisted.map((t) => t.id));
    const streamedOnly: RadarTheme[] = stream.themes
      .filter((t) => !persistedIds.has(t.themeId))
      .map((t) => ({
        id: t.themeId,
        title: t.title,
        summary: t.summary,
        displayOrder: t.displayOrder,
        items: [],
      }));
    return [...persisted, ...streamedOnly].sort((a, b) => a.displayOrder - b.displayOrder);
  }, [radar, stream.themes, isGenerating]);

  if (!radar) {
    return (
      <Box sx={{ py: 8 }}>
        <Typography variant="body2" color="text.secondary">
          Loading radar...
        </Typography>
      </Box>
    );
  }

  const expectedThemes = topics?.length ?? 5;
  const pendingCount = streaming ? Math.max(0, expectedThemes - themes.length) : 0;

  return (
    <Box sx={{ width: "100%" }}>
      {/* Back link */}
      <motion.div
        initial={{ opacity: 0, x: -8 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.3 }}
      >
        <Box
          component={RouterLink}
          to="/app/radars"
          sx={{
            display: "inline-flex",
            alignItems: "center",
            gap: 0.75,
            mb: 3,
            fontSize: "0.875rem",
            fontWeight: 500,
            color: colors.textSecondary,
            textDecoration: "none",
            transition: "all 0.15s ease",
            "&:hover": {
              color: colors.primary,
              transform: "translateX(-2px)",
            },
          }}
        >
          <ArrowBackIcon sx={{ fontSize: 18 }} />
          Back to Radars
        </Box>
      </motion.div>

      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.1 }}
      >
        <Box sx={{ mb: 5 }}>
          <Box
            sx={{
              display: "flex",
              alignItems: "flex-start",
              justifyContent: "space-between",
              gap: 2,
              flexWrap: "wrap",
              mb: 2,
            }}
          >
            <Box sx={{ flex: 1 }}>
              <Typography
                component="h1"
                sx={{
                  m: 0,
                  fontFamily: fonts.headline,
                  fontSize: "2rem",
                  fontWeight: 700,
                  letterSpacing: "-0.03em",
                  lineHeight: 1.2,
                  color: colors.text,
                  mb: 1,
                }}
              >
                Your Dev AI Radar
              </Typography>

              <Typography
                sx={{
                  fontSize: "0.9375rem",
                  color: colors.textSecondary,
                  mb: 1.5,
                }}
              >
                {formatDate(radar.generatedAt) || "Generating..."}
              </Typography>
            </Box>

            <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, flexShrink: 0 }}>
              <StatusTag status={radar.status} />
              {!streaming && radar.status === "READY" && (
                <Button variant="outlined" size="small" onClick={handleShare}>
                  <ShareOutlined sx={{ fontSize: 16, mr: 0.75 }} />
                  Share
                </Button>
              )}
            </Box>
          </Box>

          {/* Generation stats bar */}
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1.5,
              flexWrap: "wrap",
              fontSize: "0.8125rem",
              color: colors.textMuted,
              fontVariantNumeric: "tabular-nums",
            }}
          >
            {streaming ? (
              <>
                <PulseDot color={colors.warning} />
                <span>Generating themes...</span>
                <span>&middot;</span>
                <span>
                  {themes.length} of ~{expectedThemes}
                </span>
              </>
            ) : (
              <>
                <span>{themes.length} themes</span>
                <span>&middot;</span>
                <span>{((radar.generationMs ?? 0) / 1000).toFixed(1)}s</span>
                <span>&middot;</span>
                <span>{((radar.tokenCount ?? 0) / 1000).toFixed(1)}k tokens</span>
              </>
            )}
          </Box>

          {stream.status === "failed" && (
            <Typography variant="body2" color="error" sx={{ mt: 2 }}>
              Generation failed: {stream.error ?? "unknown error"}
            </Typography>
          )}

          {!streaming && radar.status === "FAILED" && (
            <Box sx={{ mt: 3 }}>
              <Alert severity="error">
                {radar.errorMessage ??
                  "This radar failed to generate. Start a fresh one from the Radars list."}
              </Alert>
            </Box>
          )}
        </Box>
      </motion.div>

      {/* Activity feed during streaming */}
      {streaming && <ActivityFeed activities={stream.activities} streaming={streaming} />}

      {/* Themes */}
      <motion.div
        variants={themeContainer}
        initial="hidden"
        animate="show"
      >
        {themes.map((t) => (
          <ThemeCard key={t.id} theme={t} radarId={radarId} />
        ))}
      </motion.div>

      {streaming &&
        Array.from({ length: pendingCount }).map((_, i) => <ThemeSkeleton key={`sk-${i}`} />)}

      {!streaming && themes.length === 0 && radar.status !== "FAILED" && (
        <Box
          sx={{
            p: "80px 24px",
            textAlign: "center",
            border: "2px dashed",
            borderColor: "rgba(87,83,78,0.2)",
            borderRadius: "16px",
            background: colors.gradientCard,
          }}
        >
          <Typography sx={{ fontSize: "0.9375rem", color: colors.textSecondary }}>
            No themes generated.
          </Typography>
        </Box>
      )}

      {/* Repos */}
      {!streaming && radar.repos && radar.repos.length > 0 && (
        <RepoSection repos={radar.repos} />
      )}

      <Snackbar
        open={shareSnackbar !== null}
        autoHideDuration={3000}
        onClose={() => setShareSnackbar(null)}
        message={shareSnackbar}
      />
    </Box>
  );
}
