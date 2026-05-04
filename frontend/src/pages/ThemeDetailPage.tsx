import { useMemo, useState, useCallback, useEffect } from "react";
import { useParams, useNavigate, Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Chip from "@mui/material/Chip";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ThumbUpOutlined from "@mui/icons-material/ThumbUpOutlined";
import ThumbUp from "@mui/icons-material/ThumbUp";
import ThumbDownOutlined from "@mui/icons-material/ThumbDownOutlined";
import ThumbDown from "@mui/icons-material/ThumbDown";
import ShareOutlined from "@mui/icons-material/ShareOutlined";
import SecurityOutlined from "@mui/icons-material/SecurityOutlined";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import AutoStoriesOutlined from "@mui/icons-material/AutoStoriesOutlined";
import { motion } from "framer-motion";
import { colors, fonts } from "../theme";
import { useGetRadarQuery } from "../api/radarApi";
import { usePostEngagementMutation, useGetRadarEngagementsQuery } from "../api/engagementApi";
import type { EventType } from "../api/engagementApi";
import type { RadarItem } from "../api/types";

type ThumbState = "up" | "down" | null;

const SECURITY_KEYWORDS = /cve|vulnerability|vulnerabilities|security|exploit/i;

function estimateReadTime(text: string): number {
  const words = text.split(/\s+/).length;
  return Math.max(1, Math.ceil(words / 200));
}

function splitIntoParagraphs(text: string): string[] {
  const parts = text.split(/\n{2,}/);
  if (parts.length > 1) return parts.filter((p) => p.trim().length > 0);

  const sentences = text.match(/[^.!?]+[.!?]+\s*/g) ?? [text];
  if (sentences.length <= 3) return [text];

  const paragraphs: string[] = [];
  const chunkSize = Math.ceil(sentences.length / Math.ceil(sentences.length / 3));
  for (let i = 0; i < sentences.length; i += chunkSize) {
    paragraphs.push(sentences.slice(i, i + chunkSize).join("").trim());
  }
  return paragraphs;
}

function extractDomain(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}

const SOURCE_COLORS: Record<string, { bg: string; text: string }> = {
  GHSA: { bg: "rgba(239,68,68,0.1)", text: "#dc2626" },
  HN: { bg: "rgba(255,102,0,0.1)", text: "#ea580c" },
  GH_TRENDING: { bg: "rgba(87,83,78,0.08)", text: colors.textSecondary },
  GH_RELEASES: { bg: "rgba(59,130,246,0.1)", text: "#2563eb" },
  DEFAULT: { bg: "rgba(87,83,78,0.06)", text: colors.textSecondary },
};

function SourceReference({ item, index }: { item: RadarItem; index: number }) {
  const colorScheme = SOURCE_COLORS[item.sourceName ?? "DEFAULT"] ?? SOURCE_COLORS.DEFAULT;
  const domain = extractDomain(item.url);

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay: 0.05 * index }}
    >
      <Box
        component="a"
        href={item.url}
        target="_blank"
        rel="noreferrer noopener"
        sx={{
          display: "block",
          p: "16px 20px",
          mb: 1.5,
          bgcolor: colors.bgPaper,
          border: `1px solid ${colors.divider}`,
          borderRadius: "10px",
          textDecoration: "none",
          transition: "all 0.2s ease",
          "&:hover": {
            borderColor: `rgba(87,83,78,0.3)`,
            boxShadow: `0 4px 16px rgba(87,83,78,0.06)`,
            transform: "translateY(-1px)",
          },
        }}
      >
        <Box sx={{ display: "flex", alignItems: "flex-start", gap: 2 }}>
          <Typography
            sx={{
              fontSize: "0.75rem",
              fontWeight: 700,
              color: colors.textMuted,
              fontFamily: fonts.mono,
              mt: "3px",
              flexShrink: 0,
              width: 20,
              textAlign: "right",
            }}
          >
            {index + 1}
          </Typography>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 0.5 }}>
              <Chip
                size="small"
                label={item.sourceName ?? "Source"}
                sx={{
                  height: 20,
                  fontSize: "0.625rem",
                  fontWeight: 600,
                  fontFamily: fonts.mono,
                  textTransform: "uppercase",
                  letterSpacing: "0.05em",
                  bgcolor: colorScheme.bg,
                  color: colorScheme.text,
                }}
              />
              <Typography
                sx={{
                  fontSize: "0.75rem",
                  color: colors.textMuted,
                  fontFamily: fonts.mono,
                }}
              >
                {domain}
              </Typography>
            </Box>
            <Typography
              sx={{
                fontSize: "0.9375rem",
                fontWeight: 600,
                color: colors.text,
                lineHeight: 1.4,
                mb: item.description ? 0.75 : 0,
              }}
            >
              {item.title}
              <OpenInNewIcon
                sx={{
                  fontSize: 14,
                  ml: 0.75,
                  verticalAlign: "middle",
                  color: colors.textMuted,
                  opacity: 0.6,
                }}
              />
            </Typography>
            {item.description && (
              <Typography
                sx={{
                  fontSize: "0.8125rem",
                  lineHeight: 1.6,
                  color: colors.textSecondary,
                }}
              >
                {item.description}
              </Typography>
            )}
            {item.author && (
              <Typography
                sx={{
                  fontSize: "0.75rem",
                  color: colors.textMuted,
                  mt: 0.5,
                  fontStyle: "italic",
                }}
              >
                by {item.author}
              </Typography>
            )}
          </Box>
        </Box>
      </Box>
    </motion.div>
  );
}

export function ThemeDetailPage() {
  const params = useParams<{ id: string; themeId: string }>();
  const navigate = useNavigate();
  const radarId = Number(params.id);
  const themeId = Number(params.themeId);

  const { data: radar, error } = useGetRadarQuery(radarId, { skip: !radarId });

  const theme = useMemo(() => {
    if (!radar) return null;
    return radar.themes.find((t) => t.id === themeId) ?? null;
  }, [radar, themeId]);

  const [postEngagement] = usePostEngagementMutation();
  const { data: radarEngagements } = useGetRadarEngagementsQuery(radarId, {
    skip: radarId == null,
  });

  const savedThumb = useMemo<ThumbState>(() => {
    if (!radarEngagements || !theme) return null;
    const match = radarEngagements.find((e) => e.themeIndex === theme.displayOrder);
    if (!match) return null;
    return match.eventType === "THUMBS_UP" ? "up" : "down";
  }, [radarEngagements, theme]);

  const [thumb, setThumb] = useState<ThumbState>(null);
  const [shared, setShared] = useState(false);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    if (savedThumb !== null && !hydrated) {
      setThumb(savedThumb);
      setHydrated(true);
    } else if (radarEngagements && !hydrated) {
      setHydrated(true);
    }
  }, [savedThumb, radarEngagements, hydrated]);

  const sendEngagement = useCallback(
    (eventType: EventType) => {
      if (radarId == null || !theme) return;
      postEngagement({
        radarId,
        themeIndex: theme.displayOrder,
        eventType,
      });
    },
    [postEngagement, radarId, theme],
  );

  const handleThumbUp = useCallback(() => {
    const next: ThumbState = thumb === "up" ? null : "up";
    setThumb(next);
    if (next === "up") sendEngagement("THUMBS_UP");
  }, [thumb, sendEngagement]);

  const handleThumbDown = useCallback(() => {
    const next: ThumbState = thumb === "down" ? null : "down";
    setThumb(next);
    if (next === "down") sendEngagement("THUMBS_DOWN");
  }, [thumb, sendEngagement]);

  const handleShare = useCallback(() => {
    navigator.clipboard.writeText(window.location.href).then(() => {
      setShared(true);
      setTimeout(() => setShared(false), 2000);
    });
    sendEngagement("SHARE");
  }, [sendEngagement]);

  useEffect(() => {
    if (error && "status" in error && (error.status === 404 || error.status === 403)) {
      navigate("/app/radars", { replace: true });
    }
  }, [error, navigate]);

  const paragraphs = useMemo(() => {
    if (!theme) return [];
    return splitIntoParagraphs(theme.summary);
  }, [theme]);

  const readTime = useMemo(() => {
    if (!theme) return 0;
    return estimateReadTime(theme.summary);
  }, [theme]);

  if (!radar) {
    return (
      <Box sx={{ py: 8, maxWidth: 720, mx: "auto" }}>
        <Typography variant="body2" color="text.secondary">
          Loading...
        </Typography>
      </Box>
    );
  }

  if (!theme) {
    return (
      <Box sx={{ py: 8, maxWidth: 720, mx: "auto" }}>
        <Typography variant="body2" color="text.secondary">
          Theme not found.
        </Typography>
      </Box>
    );
  }

  const isSecurity =
    SECURITY_KEYWORDS.test(theme.title) || SECURITY_KEYWORDS.test(theme.summary);

  const generatedDate = radar.generatedAt
    ? new Date(radar.generatedAt).toLocaleDateString("en-US", {
        month: "long",
        day: "numeric",
        year: "numeric",
      })
    : null;

  return (
    <Box sx={{ width: "100%", maxWidth: 760, mx: "auto" }}>
      {/* Back link */}
      <motion.div
        initial={{ opacity: 0, x: -8 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.3 }}
      >
        <Box
          component={RouterLink}
          to={`/app/radars/${radarId}`}
          sx={{
            display: "inline-flex",
            alignItems: "center",
            gap: 0.75,
            mb: 4,
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
          Back to Radar
        </Box>
      </motion.div>

      {/* Article header */}
      <motion.article
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.1 }}
      >
        {/* Security badge */}
        {isSecurity && (
          <Chip
            icon={<SecurityOutlined sx={{ fontSize: 14 }} />}
            label="Security Advisory"
            size="small"
            sx={{
              mb: 2,
              height: 24,
              fontSize: "0.75rem",
              fontWeight: 600,
              bgcolor: "rgba(239,68,68,0.1)",
              color: "#dc2626",
              "& .MuiChip-icon": { color: "#dc2626" },
            }}
          />
        )}

        {/* Title */}
        <Typography
          component="h1"
          sx={{
            m: 0,
            mb: 2.5,
            fontFamily: fonts.headline,
            fontSize: { xs: "1.75rem", md: "2.5rem" },
            fontWeight: 800,
            letterSpacing: "-0.035em",
            lineHeight: 1.15,
            color: colors.text,
          }}
        >
          {theme.title}
        </Typography>

        {/* Meta bar */}
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 2,
            mb: 4,
            pb: 3,
            borderBottom: `1px solid ${colors.divider}`,
            flexWrap: "wrap",
          }}
        >
          <Box sx={{ display: "flex", alignItems: "center", gap: 2, flex: 1 }}>
            {generatedDate && (
              <Typography
                sx={{
                  fontSize: "0.8125rem",
                  color: colors.textSecondary,
                }}
              >
                {generatedDate}
              </Typography>
            )}
            <Typography
              sx={{
                fontSize: "0.8125rem",
                color: colors.textMuted,
                display: "flex",
                alignItems: "center",
                gap: 0.5,
              }}
            >
              <AutoStoriesOutlined sx={{ fontSize: 15 }} />
              {readTime} min read
            </Typography>
            <Typography
              sx={{
                fontSize: "0.8125rem",
                color: colors.textMuted,
              }}
            >
              {theme.items.length} {theme.items.length === 1 ? "source" : "sources"}
            </Typography>
          </Box>

          {/* Engagement */}
          <Box sx={{ display: "flex", alignItems: "center", gap: 0.25 }}>
            <Tooltip title="Like">
              <IconButton
                size="small"
                onClick={handleThumbUp}
                sx={{
                  color: thumb === "up" ? colors.primary : colors.textMuted,
                  "&:hover": { color: colors.primary, bgcolor: "rgba(87,83,78,0.08)" },
                }}
              >
                {thumb === "up" ? <ThumbUp sx={{ fontSize: 18 }} /> : <ThumbUpOutlined sx={{ fontSize: 18 }} />}
              </IconButton>
            </Tooltip>
            <Tooltip title="Dislike">
              <IconButton
                size="small"
                onClick={handleThumbDown}
                sx={{
                  color: thumb === "down" ? colors.error : colors.textMuted,
                  "&:hover": { color: colors.error, bgcolor: "rgba(239,68,68,0.08)" },
                }}
              >
                {thumb === "down" ? <ThumbDown sx={{ fontSize: 18 }} /> : <ThumbDownOutlined sx={{ fontSize: 18 }} />}
              </IconButton>
            </Tooltip>
            <Tooltip title={shared ? "Copied!" : "Share"}>
              <IconButton
                size="small"
                onClick={handleShare}
                sx={{
                  color: colors.textMuted,
                  "&:hover": { color: colors.primary, bgcolor: "rgba(87,83,78,0.08)" },
                }}
              >
                <ShareOutlined sx={{ fontSize: 18 }} />
              </IconButton>
            </Tooltip>
          </Box>
        </Box>

        {/* Article body */}
        <Box sx={{ mb: 5 }}>
          {paragraphs.map((paragraph, idx) => (
            <Typography
              key={idx}
              component="p"
              sx={{
                fontFamily: fonts.body,
                fontSize: "1.0625rem",
                lineHeight: 1.85,
                color: colors.text,
                mb: 2.5,
                textWrap: "pretty",
                ...(idx === 0 && {
                  fontSize: "1.125rem",
                  color: colors.text,
                  fontWeight: 400,
                }),
              }}
            >
              {paragraph}
            </Typography>
          ))}
        </Box>

        {/* Sources section */}
        {theme.items.length > 0 && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.4, delay: 0.3 }}
          >
            <Box
              sx={{
                pt: 4,
                borderTop: `1px solid ${colors.divider}`,
              }}
            >
              <Typography
                component="h2"
                sx={{
                  mb: 3,
                  fontFamily: fonts.headline,
                  fontSize: "1.375rem",
                  fontWeight: 700,
                  letterSpacing: "-0.02em",
                  color: colors.text,
                }}
              >
                Sources & References
              </Typography>

              {theme.items.map((item, idx) => (
                <SourceReference key={item.id ?? `${item.url}-${idx}`} item={item} index={idx} />
              ))}
            </Box>
          </motion.div>
        )}

        {theme.items.length === 0 && (
          <Box
            sx={{
              p: "48px 24px",
              textAlign: "center",
              border: `1px dashed ${colors.divider}`,
              borderRadius: "12px",
              mt: 4,
            }}
          >
            <Typography sx={{ fontSize: "0.875rem", color: colors.textSecondary }}>
              No sources cited for this theme.
            </Typography>
          </Box>
        )}
      </motion.article>
    </Box>
  );
}
