import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { keyframes } from "@mui/system";
import { serifStack } from "../theme";
import { CitationPill } from "./CitationPill";
import type { RadarTheme } from "../api/types";

const fadeIn = keyframes`
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
`;

export interface ThemeCardProps {
  theme: RadarTheme;
}

export function ThemeCard({ theme }: ThemeCardProps) {
  return (
    <Box component="article" sx={{ mb: 6, animation: `${fadeIn} 400ms ease-out` }}>
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
        }}
      >
        {theme.title}
      </Typography>

      <Box
        sx={{
          fontFamily: serifStack,
          fontSize: "1.0625rem",  // 17px
          lineHeight: "28px",
          color: "text.primary",
          whiteSpace: "pre-line",
          textWrap: "pretty",
        }}
      >
        {theme.summary}
      </Box>

      {theme.items.length > 0 && (
        <Box sx={{ mt: "20px", display: "flex", gap: "6px", flexWrap: "wrap", alignItems: "center" }}>
          <Typography
            variant="overline"
            color="text.secondary"
            sx={{ mr: "6px" }}
          >
            Sources
          </Typography>
          {theme.items.map((item, idx) => (
            <CitationPill
              key={item.id}
              index={idx + 1}
              source={{ title: item.title, url: item.url, author: item.author }}
            />
          ))}
        </Box>
      )}
    </Box>
  );
}
