import Box from "@mui/material/Box";
import type { ReactNode } from "react";

export type AlertSeverity = "error" | "success";

export interface AlertProps {
  severity?: AlertSeverity;
  children: ReactNode;
  role?: string;
}

const palette = {
  error: {
    color: "#b3261e",
    bg: "rgba(179,38,30,0.06)",
    border: "rgba(179,38,30,0.2)",
  },
  success: {
    color: "#2d7a3e",
    bg: "rgba(45,122,62,0.06)",
    border: "rgba(45,122,62,0.2)",
  },
};

export function Alert({ severity = "error", children, role = "alert" }: AlertProps) {
  const p = palette[severity];
  return (
    <Box
      role={role}
      sx={{
        fontSize: 14,
        lineHeight: "20px",
        color: p.color,
        background: p.bg,
        border: `1px solid ${p.border}`,
        borderRadius: 1,
        padding: "10px 14px",
        display: "flex",
        gap: 1.25,
        alignItems: "flex-start",
      }}
    >
      <svg
        width="16"
        height="16"
        viewBox="0 0 16 16"
        fill="none"
        aria-hidden="true"
        style={{ flexShrink: 0, marginTop: 2, width: 16, height: 16 }}
      >
        <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth="1.4" />
        <path d="M8 4.5v4M8 11v.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      </svg>
      <Box component="span">{children}</Box>
    </Box>
  );
}
