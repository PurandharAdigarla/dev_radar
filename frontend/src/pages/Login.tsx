import { useState, type FormEvent } from "react";
import { useNavigate, Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Link from "@mui/material/Link";
import { Button } from "../components/Button";
import { GitHubButton } from "../components/GitHubButton";
import { TextField } from "../components/TextField";
import { Alert } from "../components/Alert";
import { AuthCard } from "../components/AuthCard";
import { useAuth } from "../auth/useAuth";

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
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
      <Typography variant="h2" sx={{ mb: 1 }}>Sign in</Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        Welcome back.
      </Typography>

      <Box component="form" onSubmit={onSubmit} sx={{ display: "flex", flexDirection: "column", gap: 3.5 }}>
        {error && <Alert severity="error">{error}</Alert>}

        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          InputLabelProps={{ shrink: true }}
          required
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          InputLabelProps={{ shrink: true }}
          required
        />

        <Box sx={{ mt: 0.5 }}>
          <Button type="submit" fullWidth disabled={submitting}>
            {submitting ? "Signing in…" : "Sign in"}
          </Button>
        </Box>
      </Box>

      <Box sx={{ mt: 3, display: "flex", alignItems: "center", gap: 2 }}>
        <Box sx={{ flex: 1, height: 1, bgcolor: "divider" }} />
        <Typography variant="caption" color="text.secondary" sx={{ textTransform: "uppercase", letterSpacing: "0.08em" }}>
          or
        </Typography>
        <Box sx={{ flex: 1, height: 1, bgcolor: "divider" }} />
      </Box>

      <GitHubButton fullWidth sx={{ mt: 2 }} />

      <Typography variant="body2" color="text.secondary" align="center" sx={{ mt: 4 }}>
        New here?{" "}
        <Link
          component={RouterLink}
          to="/register"
          sx={{ color: "text.primary", textDecoration: "underline", textUnderlineOffset: "3px" }}
        >
          Create an account
        </Link>
      </Typography>
    </AuthCard>
  );
}
