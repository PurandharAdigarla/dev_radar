import { useEffect, useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useDispatch } from "react-redux";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { PulseDot } from "../components/PulseDot";
import { ThemeCard } from "../components/ThemeCard";
import { ThemeSkeleton } from "../components/ThemeSkeleton";
import { ProposalCard } from "../components/ProposalCard";
import { Alert } from "../components/Alert";
import { useGetRadarQuery, radarApi } from "../api/radarApi";
import { useListProposalsByRadarQuery } from "../api/actionApi";
import { useGetMyInterestsQuery } from "../api/interestApi";
import { useRadarStream } from "../radar/useRadarStream";
import { generationFinished, generationStarted } from "../radar/radarGenerationSlice";
import type { AppDispatch } from "../store";
import type { RadarTheme } from "../api/types";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function formatPeriod(startIso: string, endIso: string): string {
  return `Week of ${formatDate(startIso)} – ${formatDate(endIso)}`;
}

/** Best-effort expected theme count for the progress counter while streaming.
 *  Backend doesn't tell us the target, so estimate from interest count
 *  (min 2, max 5). Only controls how many skeleton placeholders render. */
function estimateExpectedThemes(interestCount: number): number {
  return Math.max(2, Math.min(5, Math.ceil(interestCount / 2)));
}

export function RadarDetailPage() {
  const params = useParams<{ id: string }>();
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const radarId = Number(params.id);

  const { data: radar, error } = useGetRadarQuery(radarId, { skip: !radarId });
  const { data: proposals = [] } = useListProposalsByRadarQuery(radarId, { skip: !radarId });
  const { data: myInterests } = useGetMyInterestsQuery();

  const isGenerating = radar?.status === "GENERATING";
  const stream = useRadarStream(radarId, isGenerating);
  const streaming = isGenerating && stream.status !== "complete" && stream.status !== "failed";

  // Track generating radar in the sidebar indicator.
  useEffect(() => {
    if (isGenerating) {
      dispatch(generationStarted({ radarId, startedAt: new Date().toISOString() }));
    } else {
      dispatch(generationFinished());
    }
  }, [dispatch, radarId, isGenerating]);

  // Clear sidebar indicator on unmount.
  useEffect(() => {
    return () => { dispatch(generationFinished()); };
  }, [dispatch]);

  // When the stream finishes, refetch the radar for full item metadata.
  useEffect(() => {
    if (stream.status === "complete" || stream.status === "failed") {
      dispatch(radarApi.util.invalidateTags([{ type: "Radar", id: radarId }]));
    }
  }, [stream.status, dispatch, radarId]);

  // Redirect on 404 / 403.
  useEffect(() => {
    if (error && "status" in error && (error.status === 404 || error.status === 403)) {
      navigate("/app/radars", { replace: true });
    }
  }, [error, navigate]);

  // Merge persisted themes + streamed themes (streamed lack full items until
  // the final refetch fills in metadata).
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
      <Box sx={{ maxWidth: 720 }}>
        <Typography variant="body2" color="text.secondary">Loading radar…</Typography>
      </Box>
    );
  }

  const expectedThemes = estimateExpectedThemes(myInterests?.length ?? 3);
  const pendingCount = streaming ? Math.max(0, expectedThemes - themes.length) : 0;

  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: { xs: "1fr", lg: "minmax(0, 720px) 300px" },
        gap: { xs: 5, lg: 6 },
        alignItems: "flex-start",
      }}
    >
      {/* Read column */}
      <Box sx={{ minWidth: 0 }}>
        <Box sx={{ mb: 5 }}>
          <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 1 }}>
            {formatPeriod(radar.periodStart, radar.periodEnd)}
          </Typography>
          <Typography
            component="h1"
            sx={{
              m: 0,
              fontSize: "2rem",
              lineHeight: "40px",
              fontWeight: 500,
              letterSpacing: "-0.01em",
              color: "text.primary",
            }}
          >
            This week in your stack
          </Typography>
          <Box
            sx={{
              mt: 1.5,
              fontSize: 13,
              lineHeight: "20px",
              color: "text.secondary",
              display: "flex",
              alignItems: "center",
              gap: 1.25,
              flexWrap: "wrap",
              fontVariantNumeric: "tabular-nums",
            }}
          >
            {streaming ? (
              <>
                <PulseDot />
                <span>Generating themes…</span>
                <span>·</span>
                <span>{themes.length} of {expectedThemes}</span>
              </>
            ) : (
              <>
                <span>{themes.length} themes</span>
                <span>·</span>
                <span>{((radar.generationMs ?? 0) / 1000).toFixed(1)}s</span>
                <span>·</span>
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
                This radar failed to generate. Start a fresh one from the Radars list.
              </Alert>
            </Box>
          )}
        </Box>

        {themes.map((t) => (
          <ThemeCard key={t.id} theme={t} />
        ))}
        {streaming &&
          Array.from({ length: pendingCount }).map((_, i) => (
            <ThemeSkeleton key={`sk-${i}`} />
          ))}

        {!streaming && themes.length === 0 && radar.status !== "FAILED" && (
          <Box
            sx={{
              padding: "80px 24px",
              textAlign: "center",
              border: "1px dashed",
              borderColor: "divider",
              borderRadius: 3,
              color: "text.secondary",
              fontSize: 14,
            }}
          >
            No themes generated.
          </Box>
        )}
      </Box>

      {/* Proposals column */}
      {proposals.length > 0 && (
        <Box component="aside">
          <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 2 }}>
            Action proposals
          </Typography>
          {proposals.map((p) => (
            <ProposalCard key={p.id} proposal={p} />
          ))}
        </Box>
      )}
    </Box>
  );
}
