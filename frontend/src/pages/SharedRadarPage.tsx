import { useParams, Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import { Button } from "../components/Button";
import { ThemeCard } from "../components/ThemeCard";
import { useGetSharedRadarQuery } from "../api/radarApi";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

export function SharedRadarPage() {
  const { shareToken } = useParams<{ shareToken: string }>();
  const { data: radar, isLoading, isError } = useGetSharedRadarQuery(shareToken ?? "", {
    skip: !shareToken,
  });

  if (isLoading) {
    return (
      <Box
        sx={{
          minHeight: "100vh",
          bgcolor: "background.default",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <CircularProgress size={28} />
      </Box>
    );
  }

  if (isError || !radar) {
    return (
      <Box
        sx={{
          minHeight: "100vh",
          bgcolor: "background.default",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          px: 4,
        }}
      >
        <Box sx={{ textAlign: "center" }}>
          <Typography variant="h5" sx={{ mb: 2 }}>
            Radar not found
          </Typography>
          <Typography color="text.secondary" sx={{ mb: 4 }}>
            This shared radar may have been removed or the link is invalid.
          </Typography>
          <Button component={RouterLink} to="/">
            Go home
          </Button>
        </Box>
      </Box>
    );
  }

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Container maxWidth="md" sx={{ pt: 8, pb: 10, px: 4 }}>
        <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 1 }}>
          Shared Radar &middot; Week of {formatDate(radar.periodStart)} &ndash; {formatDate(radar.periodEnd)}
        </Typography>
        <Typography
          component="h1"
          sx={{
            m: 0,
            mb: 1.5,
            fontSize: "2rem",
            lineHeight: "40px",
            fontWeight: 500,
            letterSpacing: "-0.01em",
            color: "text.primary",
          }}
        >
          This week in the stack
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 5 }}>
          {radar.themes.length} themes
        </Typography>

        {radar.themes.map((theme) => (
          <ThemeCard key={theme.id} theme={theme} />
        ))}

        {/* CTA */}
        <Box
          sx={{
            mt: 6,
            p: 4,
            border: "1px solid",
            borderColor: "divider",
            borderRadius: 2,
            textAlign: "center",
          }}
        >
          <Typography sx={{ fontSize: "1.25rem", fontWeight: 500, mb: 1 }}>
            Get your own radar
          </Typography>
          <Typography color="text.secondary" sx={{ mb: 3 }}>
            Connect your GitHub and get a personalized weekly radar tailored to your stack.
          </Typography>
          <Button component={RouterLink} to="/register">
            Create account
          </Button>
        </Box>
      </Container>
    </Box>
  );
}
