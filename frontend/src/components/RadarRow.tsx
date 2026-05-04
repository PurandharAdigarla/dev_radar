import { Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { StatusTag } from "./StatusTag";
import type { RadarSummary } from "../api/types";

function formatDate(iso: string | null): string {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

export interface RadarRowProps {
  radar: RadarSummary;
}

export function RadarRow({ radar }: RadarRowProps) {
  const tokensK = radar.tokenCount != null ? (radar.tokenCount / 1000).toFixed(1) : null;
  const seconds = radar.generationMs != null ? (radar.generationMs / 1000).toFixed(1) : null;

  return (
    <Box
      component={RouterLink}
      to={`/app/radars/${radar.id}`}
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 3,
        padding: "20px 12px",
        mx: "-12px",
        borderBottom: 1,
        borderColor: "divider",
        textDecoration: "none",
        color: "text.primary",
        transition: "background 120ms",
        "&:hover": { bgcolor: "rgba(45,42,38,0.025)" },
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography
          sx={{
            fontSize: 16,
            lineHeight: "24px",
            fontWeight: 500,
            letterSpacing: "-0.005em",
            color: "text.primary",
            mb: "4px",
          }}
        >
          {formatDate(radar.generatedAt) || "Generating..."}
        </Typography>
        <Box
          sx={{
            display: "flex",
            gap: 1.5,
            flexWrap: "wrap",
            fontSize: 13,
            lineHeight: "20px",
            color: "text.secondary",
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {radar.status === "READY" && tokensK != null && seconds != null && (
            <>
              <span>{radar.themeCount ?? 0} themes</span>
              <span>·</span>
              <span>{tokensK}k tokens</span>
              <span>·</span>
              <span>{seconds}s</span>
            </>
          )}
          {radar.status === "GENERATING" && <span>Streaming themes now…</span>}
          {radar.status === "FAILED" && <span>Generation failed — retry from the detail view</span>}
        </Box>
      </Box>
      <StatusTag status={radar.status} />
    </Box>
  );
}
