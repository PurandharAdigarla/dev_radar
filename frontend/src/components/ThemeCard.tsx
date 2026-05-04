import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import SecurityOutlined from "@mui/icons-material/SecurityOutlined";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { motion } from "framer-motion";
import { colors, fonts } from "../theme";
import type { RadarTheme } from "../api/types";

const SECURITY_KEYWORDS = /cve|vulnerability|vulnerabilities|security|exploit/i;

function isSecurityTheme(theme: RadarTheme): boolean {
  return SECURITY_KEYWORDS.test(theme.title) || SECURITY_KEYWORDS.test(theme.summary);
}

export interface ThemeCardProps {
  theme: RadarTheme;
  radarId?: number;
}

export function ThemeCard({ theme, radarId }: ThemeCardProps) {
  const navigate = useNavigate();
  const isSecurity = isSecurityTheme(theme);

  const handleClick = () => {
    if (radarId != null) {
      navigate(`/app/radars/${radarId}/themes/${theme.id}`);
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.4, 0, 0.2, 1] as const }}
      whileHover={{ x: 4 }}
    >
      <Box
        component="article"
        onClick={handleClick}
        sx={{
          mb: 2,
          p: "20px 24px",
          bgcolor: colors.bgPaper,
          border: `1px solid ${colors.divider}`,
          borderRadius: "12px",
          borderLeft: isSecurity ? "4px solid #ef4444" : `4px solid ${colors.primary}`,
          cursor: radarId != null ? "pointer" : "default",
          display: "flex",
          alignItems: "center",
          gap: 3,
          transition: "all 0.2s cubic-bezier(0.4, 0, 0.2, 1)",
          "&:hover": {
            borderColor: `rgba(87,83,78,0.3)`,
            boxShadow: `0 4px 20px rgba(87,83,78,0.06)`,
            bgcolor: "rgba(250,250,249,0.8)",
          },
          ...(isSecurity && {
            backgroundColor: "rgba(239,68,68,0.03)",
          }),
        }}
      >
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography
            component="h2"
            sx={{
              m: 0,
              mb: 0.75,
              fontFamily: fonts.headline,
              fontSize: "1.125rem",
              lineHeight: 1.35,
              fontWeight: 600,
              letterSpacing: "-0.01em",
              color: colors.text,
              display: "flex",
              alignItems: "center",
              gap: 1,
            }}
          >
            {isSecurity && (
              <SecurityOutlined sx={{ color: "#ef4444", fontSize: "1.125rem", flexShrink: 0 }} />
            )}
            {theme.title}
          </Typography>

          <Typography
            sx={{
              fontFamily: fonts.body,
              fontSize: "0.875rem",
              lineHeight: 1.5,
              color: colors.textSecondary,
              overflow: "hidden",
              display: "-webkit-box",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical",
            }}
          >
            {theme.summary}
          </Typography>
        </Box>

        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 1.5,
            flexShrink: 0,
            color: colors.textMuted,
          }}
        >
          <Typography
            sx={{
              fontSize: "0.75rem",
              fontWeight: 500,
              whiteSpace: "nowrap",
            }}
          >
            {theme.items.length} {theme.items.length === 1 ? "source" : "sources"}
          </Typography>
          <ChevronRightIcon sx={{ fontSize: 18 }} />
        </Box>
      </Box>
    </motion.div>
  );
}
