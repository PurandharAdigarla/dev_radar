import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import PersonOutlined from "@mui/icons-material/PersonOutlined";
import { motion } from "framer-motion";
import { colors, fonts } from "../theme";
import { useAuth } from "../auth/useAuth";

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.08, delayChildren: 0.1 },
  },
};

const item = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.4, 0, 0.2, 1] as const } },
};

const cardSx = {
  bgcolor: colors.bgPaper,
  border: `1px solid ${colors.divider}`,
  borderRadius: "12px",
  p: 3,
  transition: "all 0.25s cubic-bezier(0.4, 0, 0.2, 1)",
} as const;

export function SettingsPage() {
  const { user } = useAuth();

  return (
    <Box sx={{ maxWidth: 640, width: "100%" }}>
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        <Typography
          component="h1"
          sx={{
            m: 0,
            mb: 4,
            fontFamily: fonts.headline,
            fontSize: "2rem",
            fontWeight: 700,
            letterSpacing: "-0.03em",
            color: colors.text,
          }}
        >
          Settings
        </Typography>
      </motion.div>

      <motion.div variants={container} initial="hidden" animate="show">
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
          {/* Account card */}
          <motion.div variants={item}>
            <Box sx={cardSx}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2 }}>
                <Box
                  sx={{
                    width: 40,
                    height: 40,
                    borderRadius: "10px",
                    background: `linear-gradient(135deg, ${colors.primary}, ${colors.secondary})`,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    color: "#fff",
                  }}
                >
                  <PersonOutlined sx={{ fontSize: 20 }} />
                </Box>
                <Typography
                  sx={{
                    fontFamily: fonts.headline,
                    fontSize: "1.125rem",
                    fontWeight: 600,
                    color: colors.text,
                  }}
                >
                  Account
                </Typography>
              </Box>

              <Box
                sx={{
                  p: 2,
                  bgcolor: colors.bgSubtle,
                  borderRadius: "10px",
                }}
              >
                <Typography
                  sx={{
                    fontSize: "0.9375rem",
                    fontWeight: 500,
                    color: colors.text,
                  }}
                >
                  {user?.displayName}
                </Typography>
                <Typography
                  sx={{
                    fontSize: "0.8125rem",
                    color: colors.textSecondary,
                    mt: 0.25,
                  }}
                >
                  {user?.email}
                </Typography>
              </Box>
            </Box>
          </motion.div>

        </Box>
      </motion.div>
    </Box>
  );
}
