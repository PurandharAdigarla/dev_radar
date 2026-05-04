import { useParams, Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import RadarRounded from "@mui/icons-material/RadarRounded";
import { Button } from "../components/Button";
import { ThemeCard } from "../components/ThemeCard";
import { useGetSharedRadarQuery } from "../api/radarApi";
import { colors, fonts } from "../theme";

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
        <CircularProgress size={28} sx={{ color: colors.primary }} />
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
          <Typography variant="h2" sx={{ mb: 2 }}>Radar not found</Typography>
          <Typography color="text.secondary" sx={{ mb: 4 }}>
            This shared radar may have been removed or the link is invalid.
          </Typography>
          <Button component={RouterLink} to="/">Go home</Button>
        </Box>
      </Box>
    );
  }

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Box
        sx={{
          background: colors.gradientHero,
          py: 6,
          borderBottom: `1px solid ${colors.sidebarBorder}`,
        }}
      >
        <Container maxWidth="md" sx={{ px: 4 }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
            <RadarRounded sx={{ color: colors.primary, fontSize: 20 }} />
            <Typography
              sx={{
                fontFamily: fonts.headline,
                fontWeight: 700,
                fontSize: "1rem",
                color: "#fff",
              }}
            >
              Dev Radar
            </Typography>
          </Box>
          <Typography variant="overline" sx={{ display: "block", mb: 1, color: "rgba(255,255,255,0.5)" }}>
            Shared Radar &middot; {formatDate(radar.periodStart)} &ndash; {formatDate(radar.periodEnd)}
          </Typography>
          <Typography
            component="h1"
            sx={{
              m: 0,
              fontFamily: fonts.headline,
              fontSize: "2rem",
              fontWeight: 700,
              color: "#fff",
              letterSpacing: "-0.02em",
            }}
          >
            This week in the stack
          </Typography>
          <Typography sx={{ fontSize: "0.875rem", color: "rgba(255,255,255,0.6)", mt: 1 }}>
            {radar.themes.length} themes
          </Typography>
        </Container>
      </Box>

      <Container maxWidth="md" sx={{ pt: 5, pb: 10, px: 4 }}>
        {radar.themes.map((theme) => (
          <ThemeCard key={theme.id} theme={theme} />
        ))}

        <Box
          sx={{
            mt: 8,
            p: 4,
            borderRadius: 3,
            background: colors.gradientCard,
            border: `1px solid rgba(87,83,78,0.15)`,
            textAlign: "center",
          }}
        >
          <Typography
            sx={{ fontFamily: fonts.headline, fontWeight: 600, fontSize: "1.125rem", mb: 1 }}
          >
            Want your own personalized radar?
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Dev Radar uses AI to deliver daily tech briefings tailored to your interests.
          </Typography>
          <Button component={RouterLink} to="/register">Get Started Free</Button>
        </Box>
      </Container>
    </Box>
  );
}
