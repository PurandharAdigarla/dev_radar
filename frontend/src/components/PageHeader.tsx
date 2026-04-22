import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import type { ReactNode } from "react";

export interface PageHeaderProps {
  title: string;
  sub?: string;
  right?: ReactNode;
}

export function PageHeader({ title, sub, right }: PageHeaderProps) {
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "flex-end",
        justifyContent: "space-between",
        gap: 3,
        mb: 5,
        flexWrap: "wrap",
      }}
    >
      <Box sx={{ minWidth: 0 }}>
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
          {title}
        </Typography>
        {sub && (
          <Typography
            variant="body1"
            color="text.secondary"
            sx={{ mt: 1, fontSize: "0.9375rem", lineHeight: "24px" }}
          >
            {sub}
          </Typography>
        )}
      </Box>
      {right}
    </Box>
  );
}
