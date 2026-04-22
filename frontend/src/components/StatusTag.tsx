import Box from "@mui/material/Box";
import { PulseDot } from "./PulseDot";
import type { RadarStatus } from "../api/types";

const STYLES: Record<RadarStatus, { color: string; label: string }> = {
  READY:      { color: "text.secondary", label: "Ready" },
  GENERATING: { color: "text.primary",   label: "Generating" },
  FAILED:     { color: "error.main",     label: "Failed" },
};

export function StatusTag({ status }: { status: RadarStatus }) {
  const { color, label } = STYLES[status];
  return (
    <Box
      component="span"
      sx={{
        display: "inline-flex",
        alignItems: "center",
        gap: "6px",
        fontSize: 11,
        lineHeight: "16px",
        fontWeight: 500,
        letterSpacing: "0.06em",
        textTransform: "uppercase",
        color,
      }}
    >
      {status === "GENERATING" && <PulseDot />}
      {label}
    </Box>
  );
}
