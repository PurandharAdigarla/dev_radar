import { useParams } from "react-router-dom";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import { ThemeCard } from "../components/ThemeCard";
import { ViralCta } from "../components/ViralCta";
import { useGetPublicWeeklyRadarQuery } from "../api/radarApi";

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
            No radar data found for this technology and week.
          </Typography>
        </Box>
      </Box>
    );
  }

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Container maxWidth="md" sx={{ pt: 8, pb: 10, px: 4 }}>
        <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 1 }}>
          Public Radar &middot; {formatDate(radar.periodStart)} &ndash; {formatDate(radar.periodEnd)}
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
          {radar.title}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 5 }}>
          {radar.themes.length} {radar.themes.length === 1 ? "theme" : "themes"}
        </Typography>

        {radar.themes.length === 0 ? (
          <Box
            sx={{
              p: 4,
              border: "1px solid",
              borderColor: "divider",
              borderRadius: 2,
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

        <ViralCta />
      </Container>
    </Box>
  );
}
