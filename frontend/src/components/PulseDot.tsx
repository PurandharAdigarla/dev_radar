import Box from "@mui/material/Box";
import { keyframes } from "@mui/system";

const pulse = keyframes`
  0%, 100% { opacity: 1; transform: scale(1); }
  50%      { opacity: 0.3; transform: scale(0.85); }
`;

export interface PulseDotProps {
  size?: number;
  color?: string;
}

export function PulseDot({ size = 7, color = "text.primary" }: PulseDotProps) {
  return (
    <Box
      component="span"
      sx={{
        display: "inline-block",
        width: size,
        height: size,
        borderRadius: 999,
        bgcolor: color,
        animation: `${pulse} 1.2s ease-in-out infinite`,
      }}
    />
  );
}
