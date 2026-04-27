import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Paper from "@mui/material/Paper";
import Grid from "@mui/material/Grid";
import { GitHubButton } from "./GitHubButton";

function ValueProp({ label, description }: { label: string; description: string }) {
  return (
    <Paper
      elevation={0}
      sx={{
        p: 2.5,
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
        textAlign: "center",
        height: "100%",
      }}
    >
      <Typography
        sx={{
          fontSize: "0.8125rem",
          fontWeight: 600,
          color: "text.primary",
          mb: 0.5,
          textTransform: "uppercase",
          letterSpacing: "0.06em",
        }}
      >
        {label}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {description}
      </Typography>
    </Paper>
  );
}

export function ViralCta() {
  return (
    <Box
      sx={{
        mt: 8,
        p: { xs: 3, sm: 5 },
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
        bgcolor: "background.paper",
        textAlign: "center",
      }}
    >
      <Typography
        component="h2"
        sx={{
          fontSize: { xs: "1.5rem", sm: "1.75rem" },
          fontWeight: 500,
          letterSpacing: "-0.01em",
          mb: 1.5,
          color: "text.primary",
        }}
      >
        Get your own personalized radar — free
      </Typography>
      <Typography
        color="text.secondary"
        sx={{ mb: 4, maxWidth: 480, mx: "auto", lineHeight: 1.6 }}
      >
        Dev Radar scans your GitHub repos and surfaces what matters to YOUR stack.
      </Typography>

      <Box sx={{ display: "flex", justifyContent: "center", mb: 5 }}>
        <GitHubButton label="Sign up with GitHub" />
      </Box>

      <Grid container spacing={2} sx={{ maxWidth: 560, mx: "auto" }}>
        <Grid item xs={12} sm={4}>
          <ValueProp label="3 Sources" description="GitHub, GHSA, RSS feeds" />
        </Grid>
        <Grid item xs={12} sm={4}>
          <ValueProp label="AI-Powered" description="Themes, not just links" />
        </Grid>
        <Grid item xs={12} sm={4}>
          <ValueProp label="CVE Alerts" description="Auto-fix PRs included" />
        </Grid>
      </Grid>
    </Box>
  );
}
