import { useParams, Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import RadarRounded from "@mui/icons-material/RadarRounded";
import { Button } from "../components/Button";
import { ThemeCard } from "../components/ThemeCard";
import { useGetPublicWeeklyRadarQuery } from "../api/radarApi";
import { colors, fonts } from "../theme";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

export function PublicStackRadarPage() {
  const { tagSlug, weekNumber } = useParams<{ tagSlug: string; weekNumber: string }>();
  const week = Number(weekNumber);

  const { data: radar, isLoading, isError } = useGetPublicWeeklyRadarQuery(
    { tagSlug: tagSlug ?? "", weekNumber: week },
    { skip: !tagSlug || !weekNumber || isNaN(week) },
  );

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
            No radar data found for this technology and week.
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
            Public Radar &middot; {formatDate(radar.periodStart)} &ndash; {formatDate(radar.periodEnd)}
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
            {radar.title}
          </Typography>
          <Typography sx={{ fontSize: "0.875rem", color: "rgba(255,255,255,0.6)", mt: 1 }}>
            {radar.themes.length} {radar.themes.length === 1 ? "theme" : "themes"}
          </Typography>
        </Container>
      </Box>

      <Container maxWidth="md" sx={{ pt: 5, pb: 10, px: 4 }}>
        {radar.themes.length === 0 ? (
          <Box
            sx={{
              p: 4,
              border: `1px solid ${colors.divider}`,
              borderRadius: 3,
              textAlign: "center",
            }}
          >
            <Typography color="text.secondary">
              No themes found for {radar.tagDisplayName} in week {radar.weekNumber}, {radar.year}.
            </Typography>
          </Box>
        ) : (
          radar.themes.map((theme) => <ThemeCard key={theme.id} theme={theme} />)
        )}

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
