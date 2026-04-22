import Box from "@mui/material/Box";
import { keyframes } from "@mui/system";

const shimmer = keyframes`
  0%, 100% { opacity: 0.7; }
  50%      { opacity: 1; }
`;

const LINE_WIDTHS = ["100%", "96%", "100%", "78%"];

export function ThemeSkeleton() {
  return (
    <Box component="article" role="status" aria-label="Loading theme" sx={{ mb: 6, opacity: 0.9 }}>
      <Box
        sx={{
          height: 24,
          width: "58%",
          mb: "20px",
          bgcolor: "rgba(45,42,38,0.07)",
          borderRadius: "4px",
          animation: `${shimmer} 1.4s ease-in-out infinite`,
        }}
      />
      {LINE_WIDTHS.map((w, i) => (
        <Box
          key={i}
          sx={{
            height: 14,
            width: w,
            mb: "10px",
            bgcolor: "rgba(45,42,38,0.05)",
            borderRadius: "4px",
            animation: `${shimmer} 1.4s ease-in-out infinite`,
            animationDelay: `${i * 120}ms`,
          }}
        />
      ))}
    </Box>
  );
}
