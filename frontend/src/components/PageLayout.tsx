import Box from "@mui/material/Box";
import type { ReactNode } from "react";

export interface PageLayoutProps {
  children: ReactNode;
  maxWidth?: number;
}

export function PageLayout({ children, maxWidth = 720 }: PageLayoutProps) {
  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "background.default",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        py: { xs: 6, md: 10 },
        px: 3,
      }}
    >
      <Box sx={{ width: "100%", maxWidth: `${maxWidth}px` }}>{children}</Box>
    </Box>
  );
}
