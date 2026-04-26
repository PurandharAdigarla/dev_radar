import { useMemo } from "react";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Typography from "@mui/material/Typography";
import { monoStack } from "../theme";
import type { RadarItem } from "../api/types";

const SOURCE_LABELS: Record<string, string> = {
  HN: "HN",
  GH_TRENDING: "GitHub",
  GH_RELEASES: "Release",
  GH_STARS: "Starred",
  GHSA: "GHSA",
  ARTICLE: "Article",
  DEP_RELEASE: "Dependency",
};

function sourceBadgeColor(sourceName: string): string {
  switch (sourceName) {
    case "GHSA": return "rgba(179,38,30,0.08)";
    default: return "rgba(45,42,38,0.05)";
  }
}

const EPSS_REGEX = /\[EPSS:\s*(\d+(?:\.\d+)%)\s*\(([^)]+)\)\]/;

interface EpssInfo {
  percent: string;
  label: string;
  numericPercent: number;
}

function parseEpss(description: string | null): EpssInfo | null {
  if (!description) return null;
  const m = description.match(EPSS_REGEX);
  if (!m) return null;
  return {
    percent: m[1],
    label: m[2],
    numericPercent: parseFloat(m[1]),
  };
}

function stripEpssTag(description: string): string {
  return description.replace(/\.\s*\[EPSS:.*?\]/, "").replace(/\[EPSS:.*?\]/, "").trim();
}

interface SourceCardProps {
  item: RadarItem;
}

export function SourceCard({ item }: SourceCardProps) {
  const label = SOURCE_LABELS[item.sourceName] ?? item.sourceName;
  const epss = useMemo(() => parseEpss(item.description), [item.description]);
  const cleanDescription = useMemo(
    () => (item.description ? stripEpssTag(item.description) : null),
    [item.description],
  );

  return (
    <Box
      component="a"
      href={item.url}
      target="_blank"
      rel="noreferrer noopener"
      sx={{
        display: "flex",
        gap: "12px",
        alignItems: "flex-start",
        py: "10px",
        px: "12px",
        textDecoration: "none",
        borderBottom: "1px solid",
        borderColor: "divider",
        "&:hover": { bgcolor: "rgba(45,42,38,0.02)" },
        transition: "background 120ms",
      }}
    >
      <Box
        sx={{
          flexShrink: 0,
          mt: "2px",
          px: "6px",
          py: "2px",
          borderRadius: "4px",
          bgcolor: sourceBadgeColor(item.sourceName),
          fontFamily: monoStack,
          fontSize: "0.6875rem",
          fontWeight: 500,
          lineHeight: "16px",
          color: "text.secondary",
          whiteSpace: "nowrap",
        }}
      >
        {label}
      </Box>
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Typography
          sx={{
            fontSize: "0.875rem",
            lineHeight: "20px",
            fontWeight: 500,
            color: "text.primary",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {item.title}
        </Typography>
        {cleanDescription && (
          <Typography
            sx={{
              fontSize: "0.8125rem",
              lineHeight: "18px",
              color: "text.secondary",
              mt: "2px",
              overflow: "hidden",
              textOverflow: "ellipsis",
              display: "-webkit-box",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical",
            }}
          >
            {cleanDescription}
          </Typography>
        )}
        {epss && (
          <Chip
            size="small"
            label={`EPSS: ${epss.percent} (${epss.label})`}
            sx={{
              mt: "4px",
              height: 20,
              fontSize: "0.6875rem",
              fontFamily: monoStack,
              fontWeight: 600,
              ...(epss.numericPercent >= 10
                ? { bgcolor: "rgba(211,47,47,0.12)", color: "#b71c1c" }
                : { bgcolor: "rgba(237,108,2,0.12)", color: "#e65100" }),
            }}
          />
        )}
      </Box>
    </Box>
  );
}
