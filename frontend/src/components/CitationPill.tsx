import { useState } from "react";
import Box from "@mui/material/Box";
import { monoStack } from "../theme";

export interface CitationPillSource {
  title: string;
  url: string;
  author?: string | null;
}

export interface CitationPillProps {
  index: number; // 1-based
  source: CitationPillSource;
}

export function CitationPill({ index, source }: CitationPillProps) {
  const [hover, setHover] = useState(false);
  return (
    <Box component="span" sx={{ position: "relative", display: "inline-block" }}>
      <Box
        component="a"
        href={source.url}
        target="_blank"
        rel="noreferrer noopener"
        onMouseEnter={() => setHover(true)}
        onMouseLeave={() => setHover(false)}
        sx={{
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          minWidth: 24,
          height: 20,
          padding: "0 6px",
          borderRadius: "4px",
          fontFamily: monoStack,
          fontSize: 11,
          lineHeight: 1,
          color: "text.primary",
          bgcolor: hover ? "rgba(45,42,38,0.08)" : "rgba(45,42,38,0.04)",
          border: "1px solid",
          borderColor: "divider",
          textDecoration: "none",
          verticalAlign: "baseline",
          cursor: "pointer",
          transition: "background 120ms",
        }}
      >
        [{index}]
      </Box>
      {hover && (
        <Box
          component="span"
          role="tooltip"
          sx={{
            position: "absolute",
            bottom: "100%",
            left: 0,
            mb: "6px",
            padding: "8px 12px",
            bgcolor: "text.primary",
            color: "#fff",
            fontSize: 12,
            lineHeight: "16px",
            borderRadius: "6px",
            whiteSpace: "nowrap",
            maxWidth: 280,
            overflow: "hidden",
            textOverflow: "ellipsis",
            zIndex: 10,
            pointerEvents: "none",
            boxShadow: "0 2px 8px rgba(0,0,0,0.15)",
          }}
        >
          {source.title}
          {source.author && (
            <Box component="span" sx={{ opacity: 0.6, ml: "6px" }}>· {source.author}</Box>
          )}
        </Box>
      )}
    </Box>
  );
}
