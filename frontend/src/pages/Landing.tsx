import { useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Paper from "@mui/material/Paper";
import Grid from "@mui/material/Grid";
import CircularProgress from "@mui/material/CircularProgress";
import { Button } from "../components/Button";
import { GitHubButton } from "../components/GitHubButton";
import { ThemeCard } from "../components/ThemeCard";
import { useGetSampleRadarQuery } from "../api/radarApi";
import type { RadarTheme } from "../api/types";

const FALLBACK_THEMES: RadarTheme[] = [
  {
    id: -1,
    displayOrder: 0,
    title: "React 19 Concurrent Features Now Stable",
    summary:
      "React 19 ships stable concurrent rendering, use() hook, and server components. " +
      "If your project uses React 18's experimental concurrent features, you can now " +
      "remove the opt-in flags. The new use() hook simplifies data fetching in client " +
      "components and replaces many useEffect patterns.",
    items: [
      {
        id: -1,
        title: "React 19 Release Notes",
        description: "Official changelog covering concurrent mode, use(), and server component improvements.",
        url: "https://react.dev/blog",
        author: "React Team",
        sourceName: "GH_RELEASES",
      },
      {
        id: -2,
        title: "Migrating to React 19 concurrent features",
        description: "Community guide on removing experimental flags and adopting stable APIs.",
        url: "https://react.dev/blog",
        author: null,
        sourceName: "HN",
      },
    ],
  },
  {
    id: -2,
    displayOrder: 1,
    title: "CVE-2026-21234: Critical Path Traversal in express-static",
    summary:
      "A critical path traversal vulnerability (CVSS 9.1) was disclosed in express-static " +
      "versions < 1.16.3. Attackers can read arbitrary files from the server filesystem. " +
      "If your Node.js services use express.static() middleware, upgrade to 1.16.3+ immediately.",
    items: [
      {
        id: -3,
        title: "GHSA-xxxx-xxxx: express-static path traversal",
        description: "GitHub Advisory detailing the vulnerability and affected versions.",
        url: "https://github.com/advisories",
        author: null,
        sourceName: "GHSA",
      },
    ],
  },
  {
    id: -3,
    displayOrder: 2,
    title: "Bun 1.2 Reaches Feature Parity with Node.js",
    summary:
      "Bun 1.2 adds full node:http2 support, closing the last major Node.js compatibility " +
      "gap. Teams evaluating Bun as a drop-in Node replacement now have fewer blockers. " +
      "Benchmarks show 3x faster cold-start times for typical Express/Fastify apps.",
    items: [
      {
        id: -4,
        title: "Bun 1.2 — full Node.js compat, 3x faster cold starts",
        description: "Announcement post with benchmarks and migration guide.",
        url: "https://bun.sh/blog",
        author: "Jarred Sumner",
        sourceName: "HN",
      },
    ],
  },
];

function HowItWorksStep({ number, title, description }: { number: string; title: string; description: string }) {
  return (
    <Paper
      elevation={0}
      sx={{
        p: 3,
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
        height: "100%",
      }}
    >
      <Typography
        sx={{
          fontSize: "0.8125rem",
          fontWeight: 600,
          color: "text.secondary",
          mb: 1,
          textTransform: "uppercase",
          letterSpacing: "0.06em",
        }}
      >
        {number}
      </Typography>
      <Typography sx={{ fontSize: "1.125rem", fontWeight: 500, color: "text.primary", mb: 1 }}>
        {title}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6 }}>
        {description}
      </Typography>
    </Paper>
  );
}

function SampleRadarPreview() {
  const { data: sampleRadar, isLoading, isError } = useGetSampleRadarQuery();

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
        <CircularProgress size={28} />
      </Box>
    );
  }

  const themes = (isError || !sampleRadar) ? FALLBACK_THEMES : sampleRadar.themes;

  return (
    <Box>
      <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 2 }}>
        Sample Radar
      </Typography>
      {themes.map((theme) => (
        <ThemeCard key={theme.id} theme={theme} />
      ))}
    </Box>
  );
}

export function Landing() {
  const [showSample, setShowSample] = useState(false);

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      {/* Hero */}
      <Container maxWidth="md" sx={{ pt: { xs: 10, md: 16 }, pb: 10, px: 4 }}>
        <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 4 }}>
          Dev Radar
        </Typography>
        <Typography
          variant="h1"
          component="h1"
          sx={{ textWrap: "balance", maxWidth: 640 }}
        >
          Know what changed in your stack before it breaks your build
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
          Dev Radar scans your repos, matches CVEs against your actual dependency tree,
          and opens fix PRs — all in one weekly radar.
        </Typography>
        <Box sx={{ display: "flex", gap: 1.5, flexWrap: "wrap", mb: 3 }}>
          <Button component={RouterLink} to="/register">
            Connect GitHub
          </Button>
          <Button variant="outlined" onClick={() => setShowSample(true)}>
            View Sample Radar
          </Button>
        </Box>

        <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 3 }}>
          <Box sx={{ flex: 1, height: 1, bgcolor: "divider" }} />
          <Typography variant="caption" color="text.secondary" sx={{ textTransform: "uppercase", letterSpacing: "0.08em" }}>
            or
          </Typography>
          <Box sx={{ flex: 1, height: 1, bgcolor: "divider" }} />
        </Box>

        <GitHubButton />
      </Container>

      {/* Sample radar preview */}
      {showSample && (
        <Container maxWidth="md" sx={{ pb: 10, px: 4 }}>
          <SampleRadarPreview />
        </Container>
      )}

      {/* How it works */}
      <Box sx={{ bgcolor: "background.paper", borderTop: 1, borderColor: "divider", py: 10 }}>
        <Container maxWidth="md" sx={{ px: 4 }}>
          <Typography
            variant="overline"
            color="text.secondary"
            sx={{ display: "block", mb: 4, textAlign: "center" }}
          >
            How it works
          </Typography>
          <Grid container spacing={3}>
            <Grid item xs={12} md={4}>
              <HowItWorksStep
                number="01"
                title="Connect your repos"
                description="Link your GitHub account. Dev Radar indexes your dependency files across all repos."
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <HowItWorksStep
                number="02"
                title="AI scans dependencies"
                description="We match your dependencies against CVE databases, release notes, and trending changes."
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <HowItWorksStep
                number="03"
                title="Get actionable radar"
                description="Receive a weekly radar with prioritized themes, citations, and auto-generated fix PRs."
              />
            </Grid>
          </Grid>
        </Container>
      </Box>
    </Box>
  );
}
