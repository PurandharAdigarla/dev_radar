import { useState, type FormEvent } from "react";
import { useNavigate, Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Link from "@mui/material/Link";
import InputAdornment from "@mui/material/InputAdornment";
import MailOutlineRounded from "@mui/icons-material/MailOutlineRounded";
import LockOutlined from "@mui/icons-material/LockOutlined";
import VisibilityOutlined from "@mui/icons-material/VisibilityOutlined";
import VisibilityOffOutlined from "@mui/icons-material/VisibilityOffOutlined";
import IconButton from "@mui/material/IconButton";
import { motion } from "framer-motion";
import { Button } from "../components/Button";
import { TextField } from "../components/TextField";
import { Alert } from "../components/Alert";
import { AuthCard } from "../components/AuthCard";
import { useAuth } from "../auth/useAuth";
import { colors, fonts } from "../theme";

const stagger = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.08, delayChildren: 0.1 } },
};

const fadeUp = {
  hidden: { opacity: 0, y: 16 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.22, 1, 0.36, 1] as const } },
};

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate("/app");
    } catch (err) {
      const status = (err as { status?: number }).status;
      const serverMsg = (err as { data?: { message?: string } }).data?.message;
      setError(serverMsg ?? (status === 401 ? "Invalid credentials" : "Sign-in failed"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard>
      <motion.div initial="hidden" animate="visible" variants={stagger}>
        <motion.div variants={fadeUp}>
          <Typography
            variant="h2"
            sx={{
              mb: 1,
              fontFamily: fonts.headline,
              background: colors.gradientText,
              backgroundClip: "text",
              WebkitBackgroundClip: "text",
              WebkitTextFillColor: "transparent",
              display: "inline-block",
            }}
          >
            Sign in
          </Typography>
        </motion.div>

        <motion.div variants={fadeUp}>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
            Welcome back.
          </Typography>
        </motion.div>

        <Box
          component="form"
          onSubmit={onSubmit}
          sx={{ display: "flex", flexDirection: "column", gap: 3.5 }}
        >
          {error && (
            <motion.div variants={fadeUp}>
              <Alert severity="error">{error}</Alert>
            </motion.div>
          )}

          <motion.div variants={fadeUp}>
            <TextField
              label="Email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              InputLabelProps={{ shrink: true }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <MailOutlineRounded sx={{ fontSize: 20, color: colors.textMuted }} />
                  </InputAdornment>
                ),
                notched: false,
              }}
              required
            />
          </motion.div>

          <motion.div variants={fadeUp}>
            <TextField
              label="Password"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              InputLabelProps={{ shrink: true }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockOutlined sx={{ fontSize: 20, color: colors.textMuted }} />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      onClick={() => setShowPassword((prev) => !prev)}
                      edge="end"
                      size="small"
                      aria-label={showPassword ? "Hide password" : "Show password"}
                      sx={{ color: colors.textMuted }}
                    >
                      {showPassword ? (
                        <VisibilityOffOutlined sx={{ fontSize: 20 }} />
                      ) : (
                        <VisibilityOutlined sx={{ fontSize: 20 }} />
                      )}
                    </IconButton>
                  </InputAdornment>
                ),
                notched: false,
              }}
              required
            />
          </motion.div>

          <motion.div variants={fadeUp}>
            <Box sx={{ mt: 0.5 }}>
              <Button
                type="submit"
                fullWidth
                disabled={submitting}
                sx={{
                  py: 1.5,
                  fontSize: "0.9375rem",
                  background: colors.gradientPrimary,
                  boxShadow: colors.glowPrimary,
                  "&:hover": {
                    background: colors.gradientPrimary,
                    filter: "brightness(1.1)",
                    boxShadow: `0 0 30px rgba(87,83,78,0.4)`,
                  },
                }}
              >
                {submitting ? "Signing in..." : "Sign in"}
              </Button>
            </Box>
          </motion.div>
        </Box>

        <motion.div variants={fadeUp}>
          <Typography variant="body2" color="text.secondary" align="center" sx={{ mt: 4 }}>
            New here?{" "}
            <Link
              component={RouterLink}
              to="/register"
              sx={{
                color: colors.primary,
                fontWeight: 600,
                textDecoration: "underline",
                textUnderlineOffset: "3px",
                textDecorationColor: `rgba(87,83,78,0.3)`,
                "&:hover": { textDecorationColor: colors.primary },
              }}
            >
              Create an account
            </Link>
          </Typography>
        </motion.div>
      </motion.div>
    </AuthCard>
  );
}
