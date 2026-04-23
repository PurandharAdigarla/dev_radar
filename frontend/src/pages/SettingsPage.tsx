import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { PageHeader } from "../components/PageHeader";
import { Button } from "../components/Button";
import { useGitHubStatusQuery } from "../api/githubApi";
import { useAuth } from "../auth/useAuth";
import { serifStack } from "../theme";

function CheckIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  );
}

export function SettingsPage() {
  const { data: ghStatus, isLoading } = useGitHubStatusQuery();
  const { accessToken } = useAuth();

  function handleLink() {
    if (accessToken) {
      window.location.href = `/api/auth/github/link?token=${encodeURIComponent(accessToken)}`;
    }
  }

  return (
    <Box sx={{ maxWidth: 960, width: "100%" }}>
      <PageHeader title="Settings" sub="Manage your account and integrations." />

      <Box sx={{ mb: 5 }}>
        <Typography
          component="h2"
          sx={{ fontSize: "1.125rem", fontWeight: 500, color: "text.primary", mb: 2 }}
        >
          GitHub
        </Typography>
        <Typography sx={{ fontSize: "0.9375rem", color: "text.secondary", mb: 3, lineHeight: "24px" }}>
          Connect your GitHub account to personalize your radar with releases from repos you've starred.
        </Typography>

        {isLoading && (
          <Typography variant="body2" color="text.secondary">Checking connection…</Typography>
        )}

        {!isLoading && ghStatus?.linked && (
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 2,
              py: "14px",
              px: 2,
              border: 1,
              borderColor: "divider",
              borderRadius: 2,
              bgcolor: "background.paper",
            }}
          >
            <Box sx={{ color: "success.main", display: "flex", alignItems: "center" }}>
              <CheckIcon />
            </Box>
            <Box sx={{ flex: 1 }}>
              <Typography sx={{ fontSize: "0.9375rem", fontWeight: 500, color: "text.primary" }}>
                Connected as @{ghStatus.login}
              </Typography>
              <Typography sx={{ fontSize: "0.8125rem", color: "text.secondary", mt: 0.25 }}>
                Starred repo releases will appear in your next radar.
              </Typography>
            </Box>
          </Box>
        )}

        {!isLoading && !ghStatus?.linked && (
          <Box
            sx={{
              padding: "48px 24px",
              textAlign: "center",
              border: "1px dashed",
              borderColor: "divider",
              borderRadius: 3,
              bgcolor: "background.paper",
            }}
          >
            <Box
              sx={{
                fontFamily: serifStack,
                fontSize: 18,
                lineHeight: "26px",
                fontStyle: "italic",
                color: "text.primary",
                mb: 1,
              }}
            >
              GitHub not connected
            </Box>
            <Typography sx={{ fontSize: 14, color: "text.secondary", mb: 3, lineHeight: "22px" }}>
              Link your account to get personalized release updates from your starred repos.
            </Typography>
            <Button onClick={handleLink}>Connect to GitHub</Button>
          </Box>
        )}
      </Box>
    </Box>
  );
}
