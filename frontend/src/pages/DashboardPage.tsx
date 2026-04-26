import { Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import Skeleton from "@mui/material/Skeleton";
import { Button } from "../components/Button";
import { useGetUserStatsQuery, useGetDependencySummaryQuery } from "../api/dashboardApi";

function StatValue({ value, label }: { value: string | number; label: string }) {
  return (
    <Box sx={{ textAlign: "center" }}>
      <Typography sx={{ fontSize: "1.75rem", fontWeight: 600, lineHeight: 1.2, color: "text.primary" }}>
        {value}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
    </Box>
  );
}

export function DashboardPage() {
  const { data: stats, isLoading: statsLoading } = useGetUserStatsQuery();
  const { data: deps, isLoading: depsLoading } = useGetDependencySummaryQuery();

  return (
    <Box sx={{ maxWidth: 900, width: "100%" }}>
      <Typography
        component="h1"
        sx={{
          m: 0,
          mb: 4,
          fontSize: "2rem",
          lineHeight: "40px",
          fontWeight: 500,
          letterSpacing: "-0.01em",
          color: "text.primary",
        }}
      >
        Dashboard
      </Typography>

      <Grid container spacing={3}>
        {/* Dependency Health Card */}
        <Grid item xs={12} md={6}>
          <Card variant="outlined" sx={{ height: "100%" }}>
            <CardContent>
              <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 2 }}>
                Dependency Health
              </Typography>
              {depsLoading ? (
                <Skeleton variant="rectangular" height={60} />
              ) : deps && deps.repoCount > 0 ? (
                <Box sx={{ display: "flex", gap: 3, justifyContent: "space-around" }}>
                  <StatValue value={deps.repoCount} label="Repos" />
                  <StatValue value={deps.dependencyCount} label="Dependencies" />
                  <StatValue value={deps.vulnerabilityCount} label="Vulnerabilities" />
                </Box>
              ) : (
                <Box sx={{ textAlign: "center", py: 2 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    No dependency data yet. Connect your GitHub to start scanning.
                  </Typography>
                  <Button component={RouterLink} to="/app/settings" variant="outlined" size="small">
                    Connect GitHub
                  </Button>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Radar Status Card */}
        <Grid item xs={12} md={6}>
          <Card variant="outlined" sx={{ height: "100%" }}>
            <CardContent>
              <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 2 }}>
                Radar Status
              </Typography>
              {statsLoading ? (
                <Skeleton variant="rectangular" height={60} />
              ) : stats && stats.radarCount > 0 ? (
                <Box>
                  <Typography sx={{ fontSize: "1rem", fontWeight: 500, color: "text.primary", mb: 1 }}>
                    Your latest radar is ready
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    {stats.latestRadarDate
                      ? `Generated ${new Date(stats.latestRadarDate).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}`
                      : ""}
                  </Typography>
                  <Button component={RouterLink} to="/app/radars" size="small">
                    View radars
                  </Button>
                </Box>
              ) : (
                <Box sx={{ textAlign: "center", py: 2 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    Generate your first radar to see what matters in your stack this week.
                  </Typography>
                  <Button component={RouterLink} to="/app/radars" size="small">
                    Generate your first radar
                  </Button>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Stats Card */}
        <Grid item xs={12} md={6}>
          <Card variant="outlined" sx={{ height: "100%" }}>
            <CardContent>
              <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 2 }}>
                Your Stats
              </Typography>
              {statsLoading ? (
                <Skeleton variant="rectangular" height={60} />
              ) : stats ? (
                <Box sx={{ display: "flex", gap: 3, justifyContent: "space-around" }}>
                  <StatValue value={stats.radarCount} label="Radars" />
                  <StatValue value={stats.themeCount} label="Themes" />
                  <StatValue value={stats.engagementCount} label="Engagements" />
                </Box>
              ) : null}
            </CardContent>
          </Card>
        </Grid>

        {/* Quick Actions Card */}
        <Grid item xs={12} md={6}>
          <Card variant="outlined" sx={{ height: "100%" }}>
            <CardContent>
              <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 2 }}>
                Quick Actions
              </Typography>
              <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
                <Button component={RouterLink} to="/app/radars" variant="outlined" size="small" fullWidth>
                  Generate Radar
                </Button>
                <Button component={RouterLink} to="/app/teams" variant="outlined" size="small" fullWidth>
                  View Teams
                </Button>
                <Button component={RouterLink} to="/app/settings" variant="outlined" size="small" fullWidth>
                  Settings
                </Button>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}
