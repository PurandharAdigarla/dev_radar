import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { keyframes } from "@mui/system";
import { colors } from "../theme";
import type { RadarStatus } from "../api/types";

const pulse = keyframes`
  0%, 100% { opacity: 1; transform: scale(1); }
  50%      { opacity: 0.4; transform: scale(0.75); }
`;

interface StyleConfig {
  dot: string;
  text: string;
  bg: string;
  label: string;
  animated?: boolean;
}

const STYLES: Record<RadarStatus, StyleConfig> = {
  READY: {
    dot: colors.success,
    text: "#065f46",
    bg: "rgba(16,185,129,0.1)",
    label: "Ready",
  },
  GENERATING: {
    dot: colors.warning,
    text: "#92400e",
    bg: "rgba(245,158,11,0.1)",
    label: "Generating",
    animated: true,
  },
  FAILED: {
    dot: colors.error,
    text: "#991b1b",
    bg: "rgba(239,68,68,0.1)",
    label: "Failed",
  },
};

export function StatusTag({ status }: { status: RadarStatus }) {
  const { dot, text, bg, label, animated } = STYLES[status];

  return (
    <Box
      component="span"
      sx={{
        display: "inline-flex",
        alignItems: "center",
        gap: "6px",
        px: 1.25,
        py: 0.5,
        borderRadius: 999,
        bgcolor: bg,
      }}
    >
      <Box
        component="span"
        sx={{
          width: 7,
          height: 7,
          borderRadius: "50%",
          bgcolor: dot,
          flexShrink: 0,
          ...(animated && {
            animation: `${pulse} 1.2s ease-in-out infinite`,
          }),
        }}
      />
      <Typography
        component="span"
        sx={{
          fontSize: "0.75rem",
          fontWeight: 600,
          lineHeight: 1,
          color: text,
          letterSpacing: "0.02em",
        }}
      >
        {label}
      </Typography>
    </Box>
  );
}
