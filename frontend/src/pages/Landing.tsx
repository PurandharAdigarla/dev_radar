import { Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "../components/Button";
import { GitHubButton } from "../components/GitHubButton";

export function Landing() {
  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "background.default",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        px: 4,
        py: 10,
      }}
    >
      <Box sx={{ maxWidth: 640, width: "100%" }}>
        <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 4 }}>
          Dev Radar
        </Typography>
        <Typography variant="h1" component="h1" sx={{ textWrap: "balance" }}>
          A weekly brief for what you care about.
        </Typography>
        <Typography
          variant="body1"
          color="text.secondary"
          sx={{
            fontSize: "1.0625rem",
            lineHeight: 1.6,
            mt: 3,
            mb: 5,
            maxWidth: 560,
            textWrap: "pretty",
          }}
        >
          Personalized radars synthesized from Hacker News, GitHub Trending, and security
          advisories, with citations you can trust.
        </Typography>
        <Box sx={{ display: "flex", gap: 1.5, flexWrap: "wrap" }}>
          <Button component={RouterLink} to="/register">
            Create account
          </Button>
          <Button component={RouterLink} to="/login" variant="outlined">
            Sign in
          </Button>
        </Box>

        <Box sx={{ mt: 4, display: "flex", alignItems: "center", gap: 2 }}>
          <Box sx={{ flex: 1, height: 1, bgcolor: "divider" }} />
          <Typography variant="caption" color="text.secondary" sx={{ textTransform: "uppercase", letterSpacing: "0.08em" }}>
            or
          </Typography>
          <Box sx={{ flex: 1, height: 1, bgcolor: "divider" }} />
        </Box>

        <Box sx={{ mt: 3 }}>
          <GitHubButton />
        </Box>
      </Box>
    </Box>
  );
}
