import { useState, useMemo, type FormEvent } from "react";
import { useNavigate, Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Link from "@mui/material/Link";
import InputAdornment from "@mui/material/InputAdornment";
import LinearProgress from "@mui/material/LinearProgress";
import MailOutlineRounded from "@mui/icons-material/MailOutlineRounded";
import LockOutlined from "@mui/icons-material/LockOutlined";
import VisibilityOutlined from "@mui/icons-material/VisibilityOutlined";
import VisibilityOffOutlined from "@mui/icons-material/VisibilityOffOutlined";
import PersonOutlineRounded from "@mui/icons-material/PersonOutlineRounded";
import IconButton from "@mui/material/IconButton";
import { motion } from "framer-motion";
import { Button } from "../components/Button";
import { TextField } from "../components/TextField";
import { Alert } from "../components/Alert";
import { AuthCard } from "../components/AuthCard";
import { useAuth } from "../auth/useAuth";
import { colors, fonts } from "../theme";

type ErrorKind = "email-taken" | "generic" | null;

const stagger = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.08, delayChildren: 0.1 } },
};

const fadeUp = {
  hidden: { opacity: 0, y: 16 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.22, 1, 0.36, 1] as const } },
};

interface PasswordStrength {
  score: 0 | 1 | 2 | 3;
  label: string;
  color: string;
}

function getPasswordStrength(pw: string): PasswordStrength {
  if (pw.length === 0) return { score: 0, label: "", color: colors.divider };
  let score = 0;
  if (pw.length >= 8) score++;
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) score++;
  if (/\d/.test(pw) || /[^A-Za-z0-9]/.test(pw)) score++;

  const map: Record<number, PasswordStrength> = {
    0: { score: 0, label: "Too short", color: colors.error },
    1: { score: 1, label: "Weak", color: colors.warning },
    2: { score: 2, label: "Fair", color: colors.primary },
    3: { score: 3, label: "Strong", color: colors.success },
  };
  return map[score];
}

export function Register() {
  const { register, login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<ErrorKind>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const strength = useMemo(() => getPasswordStrength(password), [password]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setErrorMsg(null);
    setSubmitting(true);
    try {
      await register(email, password, displayName);
      await login(email, password);
      navigate("/app");
    } catch (err) {
      const status = (err as { status?: number }).status;
      const serverMsg = (err as { data?: { message?: string } }).data?.message;
      if (status === 409) {
        setError("email-taken");
      } else {
        setError("generic");
        setErrorMsg(serverMsg ?? "Registration failed");
      }
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
            Create your account
          </Typography>
        </motion.div>

        <motion.div variants={fadeUp}>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
            Start getting your daily radar.
          </Typography>
        </motion.div>

        <Box
          component="form"
          onSubmit={onSubmit}
          sx={{ display: "flex", flexDirection: "column", gap: 3.5 }}
        >
          {error === "email-taken" && (
            <motion.div variants={fadeUp}>
              <Alert severity="error">
                That email is already registered.{" "}
                <Link
                  component={RouterLink}
                  to="/login"
                  sx={{
                    color: "inherit",
                    textDecoration: "underline",
                    textUnderlineOffset: "3px",
                  }}
                >
                  Sign in instead?
                </Link>
              </Alert>
            </motion.div>
          )}
          {error === "generic" && errorMsg && (
            <motion.div variants={fadeUp}>
              <Alert severity="error">{errorMsg}</Alert>
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
              label="Display name"
              autoComplete="nickname"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              InputLabelProps={{ shrink: true }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <PersonOutlineRounded sx={{ fontSize: 20, color: colors.textMuted }} />
                  </InputAdornment>
                ),
                notched: false,
              }}
              helperText="Shown on your radar header. You can change this later."
              required
            />
          </motion.div>

          <motion.div variants={fadeUp}>
            <TextField
              label="Password"
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
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
            {/* Password strength indicator */}
            {password.length > 0 && (
              <Box sx={{ mt: 1.5 }}>
                <LinearProgress
                  variant="determinate"
                  value={(strength.score / 3) * 100}
                  sx={{
                    height: 4,
                    borderRadius: 2,
                    bgcolor: "rgba(0,0,0,0.06)",
                    "& .MuiLinearProgress-bar": {
                      borderRadius: 2,
                      bgcolor: strength.color,
                      transition: "all 0.3s ease",
                    },
                  }}
                />
                <Typography
                  sx={{
                    fontSize: "0.75rem",
                    fontWeight: 500,
                    color: strength.color,
                    mt: 0.5,
                  }}
                >
                  {strength.label}
                </Typography>
              </Box>
            )}
            {password.length === 0 && (
              <Typography
                sx={{ fontSize: "0.75rem", color: colors.textMuted, mt: 0.75 }}
              >
                At least 8 characters.
              </Typography>
            )}
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
                {submitting ? "Creating..." : "Create account"}
              </Button>
            </Box>
          </motion.div>
        </Box>

        <motion.div variants={fadeUp}>
          <Typography variant="body2" color="text.secondary" align="center" sx={{ mt: 4 }}>
            Have an account?{" "}
            <Link
              component={RouterLink}
              to="/login"
              sx={{
                color: colors.primary,
                fontWeight: 600,
                textDecoration: "underline",
                textUnderlineOffset: "3px",
                textDecorationColor: `rgba(87,83,78,0.3)`,
                "&:hover": { textDecorationColor: colors.primary },
              }}
            >
              Sign in
            </Link>
          </Typography>
        </motion.div>
      </motion.div>
    </AuthCard>
  );
}
