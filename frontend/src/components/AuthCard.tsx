import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import RadarRounded from "@mui/icons-material/RadarRounded";
import { motion } from "framer-motion";
import { colors, fonts } from "../theme";
import type { ReactNode } from "react";

const particleKeyframes = `
@keyframes float-particle-1 {
  0%, 100% { transform: translate(0, 0) scale(1); opacity: 0.3; }
  25% { transform: translate(30px, -40px) scale(1.2); opacity: 0.6; }
  50% { transform: translate(-20px, -80px) scale(0.8); opacity: 0.4; }
  75% { transform: translate(40px, -30px) scale(1.1); opacity: 0.5; }
}
@keyframes float-particle-2 {
  0%, 100% { transform: translate(0, 0) scale(1); opacity: 0.2; }
  33% { transform: translate(-50px, -30px) scale(1.3); opacity: 0.5; }
  66% { transform: translate(20px, -60px) scale(0.9); opacity: 0.3; }
}
@keyframes float-particle-3 {
  0%, 100% { transform: translate(0, 0) scale(0.8); opacity: 0.4; }
  20% { transform: translate(60px, 20px) scale(1); opacity: 0.2; }
  50% { transform: translate(-30px, -50px) scale(1.2); opacity: 0.6; }
  80% { transform: translate(10px, 30px) scale(0.9); opacity: 0.3; }
}
`;

const particles = [
  { size: 4, top: "15%", left: "20%", animation: "float-particle-1 8s ease-in-out infinite", color: colors.primary },
  { size: 3, top: "30%", left: "75%", animation: "float-particle-2 10s ease-in-out infinite 1s", color: colors.secondary },
  { size: 5, top: "60%", left: "10%", animation: "float-particle-3 12s ease-in-out infinite 2s", color: colors.tertiary },
  { size: 3, top: "70%", left: "85%", animation: "float-particle-1 9s ease-in-out infinite 3s", color: colors.primary },
  { size: 4, top: "25%", left: "50%", animation: "float-particle-2 11s ease-in-out infinite 0.5s", color: colors.secondary },
  { size: 2, top: "80%", left: "40%", animation: "float-particle-3 14s ease-in-out infinite 1.5s", color: colors.tertiary },
  { size: 3, top: "45%", left: "90%", animation: "float-particle-1 10s ease-in-out infinite 4s", color: colors.primary },
  { size: 4, top: "10%", left: "60%", animation: "float-particle-2 13s ease-in-out infinite 2.5s", color: colors.secondary },
];

export function AuthCard({ children }: { children: ReactNode }) {
  return (
    <Box
      sx={{
        minHeight: "100vh",
        background: colors.gradientHero,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        px: 3,
        py: 10,
        position: "relative",
        overflow: "hidden",
      }}
    >
      {/* Inject keyframes */}
      <style>{particleKeyframes}</style>

      {/* Floating particles */}
      {particles.map((p, i) => (
        <Box
          key={i}
          sx={{
            position: "absolute",
            top: p.top,
            left: p.left,
            width: p.size,
            height: p.size,
            borderRadius: "50%",
            bgcolor: p.color,
            animation: p.animation,
            pointerEvents: "none",
          }}
        />
      ))}

      {/* Glass card */}
      <motion.div
        initial={{ opacity: 0, y: 20, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.5, ease: [0.4, 0, 0.2, 1] as const }}
        style={{ width: "100%", maxWidth: 420 }}
      >
        <Box
          sx={{
            bgcolor: "#fff",
            borderRadius: "16px",
            p: { xs: 4, sm: 5 },
            boxShadow: "0 25px 50px rgba(0,0,0,0.25), 0 0 80px rgba(87,83,78,0.08)",
            position: "relative",
            zIndex: 1,
          }}
        >
          {/* Logo */}
          <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", gap: "10px", mb: 4 }}>
            <RadarRounded
              sx={{
                fontSize: 32,
                background: colors.gradientPrimary,
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
              }}
            />
            <Typography
              sx={{
                fontFamily: fonts.headline,
                fontWeight: 700,
                fontSize: "1.25rem",
                letterSpacing: "-0.02em",
                background: colors.gradientText,
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
              }}
            >
              Dev Radar
            </Typography>
          </Box>

          {children}
        </Box>
      </motion.div>
    </Box>
  );
}
