import { Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Chip from "@mui/material/Chip";
import Skeleton from "@mui/material/Skeleton";
import LinearProgress from "@mui/material/LinearProgress";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import RadioButtonUncheckedIcon from "@mui/icons-material/RadioButtonUnchecked";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import RadarOutlined from "@mui/icons-material/RadarOutlined";
import AutoAwesomeOutlined from "@mui/icons-material/AutoAwesomeOutlined";
import TouchAppOutlined from "@mui/icons-material/TouchAppOutlined";
import { motion } from "framer-motion";
import { Button } from "../components/Button";
import { colors, fonts } from "../theme";
import { useGetUserStatsQuery } from "../api/dashboardApi";
import { useGetTopicsQuery } from "../api/topicApi";

const fade = {
  hidden: { opacity: 0, y: 8 },
  show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: [0.4, 0, 0.2, 1] as const } },
};

const cardSx = {
  bgcolor: colors.bgPaper,
  border: `1px solid ${colors.divider}`,
  borderRadius: "10px",
  p: 2,
  transition: "all 0.2s ease",
  "&:hover": {
    borderColor: "rgba(87,83,78,0.25)",
    boxShadow: "0 2px 12px rgba(0,0,0,0.04)",
  },
} as const;

function getGreeting(): string {
  const h = new Date().getHours();
  if (h < 12) return "Good morning";
  if (h < 17) return "Good afternoon";
  return "Good evening";
}

interface ChecklistStep {
  label: string;
  done: boolean;
  href: string;
}

export function DashboardPage() {
  const { data: stats, isLoading: statsLoading } = useGetUserStatsQuery();
  const { data: topics } = useGetTopicsQuery();

  const isNewUser = !statsLoading && (!stats || stats.radarCount === 0);
  const hasLatestRadar = stats && stats.radarCount > 0;
  const latestDateStr = stats?.latestRadarDate
    ? new Date(stats.latestRadarDate).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
      })
    : null;

  const checklistSteps: ChecklistStep[] = [
    { label: "Pick topics", done: (topics?.length ?? 0) > 0, href: "/app/topics" },
    { label: "Generate first radar", done: (stats?.radarCount ?? 0) > 0, href: "/app/radars" },
    { label: "Explore your radar", done: (stats?.engagementCount ?? 0) > 0, href: "/app/radars" },
  ];
  const completedCount = checklistSteps.filter((s) => s.done).length;

  const statItems = [
    { value: stats?.radarCount ?? 0, label: "Radars", icon: <RadarOutlined sx={{ fontSize: 16 }} /> },
    { value: stats?.themeCount ?? 0, label: "Themes", icon: <AutoAwesomeOutlined sx={{ fontSize: 16 }} /> },
    { value: stats?.engagementCount ?? 0, label: "Engagements", icon: <TouchAppOutlined sx={{ fontSize: 16 }} /> },
  ];

  return (
    <Box sx={{ maxWidth: 720, width: "100%" }}>
      <motion.div initial="hidden" animate="show" variants={{ show: { transition: { staggerChildren: 0.06 } } }}>
        {/* Header row: greeting + generate button */}
        <motion.div variants={fade}>
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 2.5 }}>
            <Typography
              component="h1"
              sx={{
                m: 0,
                fontFamily: fonts.headline,
                fontSize: "1.5rem",
                fontWeight: 700,
                letterSpacing: "-0.02em",
                color: colors.text,
              }}
            >
              {getGreeting()}
            </Typography>
            <Button component={RouterLink} to="/app/radars" size="small">
              Generate Radar
            </Button>
          </Box>
        </motion.div>

        {/* Stats row */}
        <motion.div variants={fade}>
          <Box sx={{ display: "flex", gap: 1.5, mb: 2 }}>
            {statItems.map((s) => (
              <Box
                key={s.label}
                sx={{
                  ...cardSx,
                  flex: 1,
                  display: "flex",
                  alignItems: "center",
                  gap: 1.5,
                }}
              >
                <Box sx={{ color: colors.textMuted, display: "flex" }}>{s.icon}</Box>
                <Box>
                  <Typography sx={{ fontFamily: fonts.headline, fontSize: "1.25rem", fontWeight: 700, lineHeight: 1, color: colors.text }}>
                    {statsLoading ? <Skeleton width={24} /> : s.value}
                  </Typography>
                  <Typography sx={{ fontSize: "0.6875rem", color: colors.textMuted, fontWeight: 500 }}>
                    {s.label}
                  </Typography>
                </Box>
              </Box>
            ))}
          </Box>
        </motion.div>

        {/* Latest radar card */}
        <motion.div variants={fade}>
          <Box
            sx={{
              ...cardSx,
              mb: 2,
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
            }}
          >
            {statsLoading ? (
              <Box sx={{ flex: 1 }}>
                <Skeleton variant="text" width="50%" height={24} />
                <Skeleton variant="text" width="30%" height={18} sx={{ mt: 0.5 }} />
              </Box>
            ) : hasLatestRadar ? (
              <>
                <Box>
                  <Typography sx={{ fontFamily: fonts.headline, fontSize: "0.9375rem", fontWeight: 600, color: colors.text }}>
                    Latest radar ready
                  </Typography>
                  <Typography sx={{ fontSize: "0.8125rem", color: colors.textSecondary }}>
                    {latestDateStr} &middot; {stats!.themeCount} themes
                  </Typography>
                </Box>
                <Button component={RouterLink} to="/app/radars" size="small" variant="text" sx={{ gap: 0.5 }}>
                  View <ArrowForwardIcon sx={{ fontSize: 14 }} />
                </Button>
              </>
            ) : (
              <>
                <Box>
                  <Typography sx={{ fontFamily: fonts.headline, fontSize: "0.9375rem", fontWeight: 600, color: colors.text }}>
                    No radars yet
                  </Typography>
                  <Typography sx={{ fontSize: "0.8125rem", color: colors.textSecondary }}>
                    Pick topics and generate your first radar.
                  </Typography>
                </Box>
                <Button component={RouterLink} to="/app/radars" size="small">
                  Get started
                </Button>
              </>
            )}
          </Box>
        </motion.div>

        {/* Topics + Onboarding row */}
        <motion.div variants={fade}>
          <Box sx={{ display: "grid", gap: 1.5, gridTemplateColumns: isNewUser ? { xs: "1fr", md: "1fr 1fr" } : "1fr", mb: 2 }}>
            {/* Topics */}
            <Box sx={cardSx}>
              <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 1.5 }}>
                <Typography variant="overline" sx={{ color: colors.textMuted, m: 0 }}>
                  Your Topics
                </Typography>
                <Button component={RouterLink} to="/app/topics" variant="text" size="small" sx={{ fontSize: "0.75rem", p: 0, minWidth: 0 }}>
                  Edit
                </Button>
              </Box>
              {topics && topics.length > 0 ? (
                <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
                  {topics.map((t) => (
                    <Chip
                      key={t.id}
                      label={t.topic}
                      size="small"
                      sx={{
                        height: 24,
                        fontSize: "0.75rem",
                        bgcolor: colors.bgSubtle,
                        color: colors.textSecondary,
                        border: `1px solid ${colors.divider}`,
                      }}
                    />
                  ))}
                </Box>
              ) : (
                <Typography sx={{ fontSize: "0.8125rem", color: colors.textMuted }}>
                  No topics added yet.
                </Typography>
              )}
            </Box>

            {/* Onboarding checklist -- only for new users */}
            {isNewUser && (
              <Box sx={cardSx}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}>
                  <Typography variant="overline" sx={{ color: colors.textMuted, m: 0 }}>
                    Setup
                  </Typography>
                  <Typography sx={{ fontSize: "0.6875rem", fontWeight: 600, color: colors.textMuted }}>
                    {completedCount}/{checklistSteps.length}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={(completedCount / checklistSteps.length) * 100}
                  sx={{
                    height: 4,
                    borderRadius: 2,
                    mb: 1.5,
                    bgcolor: "rgba(87,83,78,0.08)",
                    "& .MuiLinearProgress-bar": { borderRadius: 2, bgcolor: colors.primary },
                  }}
                />
                <Box sx={{ display: "flex", flexDirection: "column", gap: 0.25 }}>
                  {checklistSteps.map((step) => (
                    <Box
                      key={step.label}
                      component={step.done ? "div" : RouterLink}
                      {...(!step.done && { to: step.href })}
                      sx={{
                        display: "flex",
                        alignItems: "center",
                        gap: 1,
                        py: 0.5,
                        px: 1,
                        borderRadius: "6px",
                        textDecoration: "none",
                        color: "inherit",
                        ...(!step.done && { "&:hover": { bgcolor: "rgba(87,83,78,0.04)" } }),
                      }}
                    >
                      {step.done ? (
                        <CheckCircleIcon sx={{ fontSize: 16, color: colors.success }} />
                      ) : (
                        <RadioButtonUncheckedIcon sx={{ fontSize: 16, color: colors.textMuted }} />
                      )}
                      <Typography
                        sx={{
                          fontSize: "0.8125rem",
                          fontWeight: step.done ? 400 : 500,
                          color: step.done ? colors.textMuted : colors.text,
                          textDecoration: step.done ? "line-through" : "none",
                        }}
                      >
                        {step.label}
                      </Typography>
                    </Box>
                  ))}
                </Box>
              </Box>
            )}
          </Box>
        </motion.div>
      </motion.div>
    </Box>
  );
}
