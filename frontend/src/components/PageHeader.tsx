import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { colors, fonts } from "../theme";
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
            fontFamily: fonts.headline,
            fontSize: "2rem",
            lineHeight: "40px",
            fontWeight: 700,
            letterSpacing: "-0.02em",
            color: "text.primary",
          }}
        >
          {title}
        </Typography>
        {/* Gradient underline accent */}
        <Box
          sx={{
            mt: 1,
            height: 3,
            width: 48,
            borderRadius: 2,
            background: colors.gradientPrimary,
          }}
        />
        {sub && (
          <Typography
            variant="body1"
            color="text.secondary"
            sx={{ mt: 1.5, fontSize: "0.9375rem", lineHeight: "24px" }}
          >
            {sub}
          </Typography>
        )}
      </Box>
      {right}
    </Box>
  );
}
