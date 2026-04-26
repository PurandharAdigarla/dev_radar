import { useState, useCallback, useEffect, useMemo } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import ThumbUpOutlined from "@mui/icons-material/ThumbUpOutlined";
import ThumbUp from "@mui/icons-material/ThumbUp";
import ThumbDownOutlined from "@mui/icons-material/ThumbDownOutlined";
import ThumbDown from "@mui/icons-material/ThumbDown";
import ShareOutlined from "@mui/icons-material/ShareOutlined";
import SecurityOutlined from "@mui/icons-material/SecurityOutlined";
import { keyframes } from "@mui/system";
import { serifStack } from "../theme";
import { SourceCard } from "./SourceCard";
import { usePostEngagementMutation, useGetRadarEngagementsQuery } from "../api/engagementApi";
import type { RadarTheme } from "../api/types";
import type { EventType } from "../api/engagementApi";

const fadeIn = keyframes`
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
`;

const SECURITY_KEYWORDS = /cve|vulnerability|vulnerabilities|security|exploit/i;

function isSecurityTheme(theme: RadarTheme): boolean {
  return SECURITY_KEYWORDS.test(theme.title) || SECURITY_KEYWORDS.test(theme.summary);
}

export interface ThemeCardProps {
  theme: RadarTheme;
  radarId?: number;
}

type ThumbState = "up" | "down" | null;

export function ThemeCard({ theme, radarId }: ThemeCardProps) {
  const [postEngagement] = usePostEngagementMutation();
  const { data: radarEngagements } = useGetRadarEngagementsQuery(radarId!, {
    skip: radarId == null,
  });

  const savedThumb = useMemo<ThumbState>(() => {
    if (!radarEngagements) return null;
    const match = radarEngagements.find((e) => e.themeIndex === theme.displayOrder);
    if (!match) return null;
    return match.eventType === "THUMBS_UP" ? "up" : "down";
  }, [radarEngagements, theme.displayOrder]);

  const [thumb, setThumb] = useState<ThumbState>(null);
  const [shared, setShared] = useState(false);
  const [hydrated, setHydrated] = useState(false);

  // Sync from server once data loads
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
      if (radarId == null) return;
      postEngagement({
        radarId,
        themeIndex: theme.displayOrder,
        eventType,
      });
    },
    [postEngagement, radarId, theme.displayOrder],
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
    const url = window.location.href;
    navigator.clipboard.writeText(url).then(() => {
      setShared(true);
      setTimeout(() => setShared(false), 2000);
    });
    sendEngagement("SHARE");
  }, [sendEngagement]);

  const isSecurity = isSecurityTheme(theme);

  return (
    <Box
      component="article"
      sx={{
        mb: 6,
        animation: `${fadeIn} 400ms ease-out`,
        ...(isSecurity && {
          borderLeft: "4px solid #d32f2f",
          backgroundColor: "rgba(211, 47, 47, 0.04)",
          pl: 2.5,
          borderRadius: 1,
        }),
      }}
    >
      <Typography
        component="h2"
        sx={{
          m: 0,
          mb: 2,
          fontSize: "1.5rem",
          lineHeight: "32px",
          fontWeight: 500,
          letterSpacing: "-0.01em",
          color: "text.primary",
          display: "flex",
          alignItems: "center",
          gap: 1,
        }}
      >
        {isSecurity && (
          <SecurityOutlined sx={{ color: "#d32f2f", fontSize: "1.25rem" }} />
        )}
        {theme.title}
      </Typography>

      <Box
        sx={{
          fontFamily: serifStack,
          fontSize: "1.0625rem",
          lineHeight: "28px",
          color: "text.primary",
          whiteSpace: "pre-line",
          textWrap: "pretty",
        }}
      >
        {theme.summary}
      </Box>

      {theme.items.length > 0 && (
        <Box
          sx={{
            mt: 3,
            border: "1px solid",
            borderColor: "divider",
            borderRadius: 2,
            overflow: "hidden",
            "& > a:last-child": { borderBottom: "none" },
          }}
        >
          {theme.items.map((item) => (
            <SourceCard key={item.id} item={item} />
          ))}
        </Box>
      )}

      {radarId != null && (
        <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, mt: 2 }}>
          <Tooltip title="Like this theme">
            <IconButton
              size="small"
              onClick={handleThumbUp}
              color={thumb === "up" ? "primary" : "default"}
              aria-label="thumbs up"
            >
              {thumb === "up" ? <ThumbUp fontSize="small" /> : <ThumbUpOutlined fontSize="small" />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Dislike this theme">
            <IconButton
              size="small"
              onClick={handleThumbDown}
              color={thumb === "down" ? "error" : "default"}
              aria-label="thumbs down"
            >
              {thumb === "down" ? <ThumbDown fontSize="small" /> : <ThumbDownOutlined fontSize="small" />}
            </IconButton>
          </Tooltip>
          <Tooltip title={shared ? "Link copied!" : "Copy link"}>
            <IconButton
              size="small"
              onClick={handleShare}
              aria-label="share"
            >
              <ShareOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>
      )}
    </Box>
  );
}
