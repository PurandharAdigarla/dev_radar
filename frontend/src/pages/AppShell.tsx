import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useAuth } from "../auth/useAuth";

const SIDEBAR_WIDTH = 240;
const NAV_ITEMS = [
  { key: "radars", label: "Radars" },
  { key: "proposals", label: "Proposals" },
  { key: "settings", label: "Settings" },
];

export function AppShell() {
  const { user, logout } = useAuth();

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default", display: "flex" }}>
      <Box
        component="aside"
        sx={{
          width: SIDEBAR_WIDTH,
          flexShrink: 0,
          borderRight: 1,
          borderColor: "divider",
          bgcolor: "background.default",
          p: "32px 24px",
          display: "flex",
          flexDirection: "column",
        }}
      >
        <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 5 }}>
          Dev Radar
        </Typography>

        <Box component="nav" sx={{ display: "flex", flexDirection: "column", gap: "2px" }}>
          {NAV_ITEMS.map((item) => (
            <Box
              key={item.key}
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                px: "10px",
                py: "8px",
                borderRadius: "6px",
                color: "text.secondary",
                opacity: 0.6,
                cursor: "not-allowed",
                fontSize: "0.9375rem",
                lineHeight: "24px",
              }}
            >
              <span>{item.label}</span>
              <Box
                component="span"
                sx={{
                  fontSize: "0.6875rem",
                  lineHeight: "16px",
                  letterSpacing: "0.06em",
                  textTransform: "uppercase",
                  color: "text.secondary",
                  opacity: 0.8,
                }}
              >
                soon
              </Box>
            </Box>
          ))}
        </Box>

        <Box sx={{ flex: 1 }} />

        <Box sx={{ pt: 2.5, borderTop: 1, borderColor: "divider" }}>
          <Typography sx={{ fontSize: "0.875rem", lineHeight: "20px", fontWeight: 500, color: "text.primary" }}>
            {user?.displayName}
          </Typography>
          <Typography
            sx={{
              fontSize: "0.8125rem",
              lineHeight: "20px",
              color: "text.secondary",
              mb: 1.5,
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {user?.email}
          </Typography>
          <Box
            component="button"
            onClick={logout}
            sx={{
              background: "transparent",
              border: "none",
              padding: 0,
              fontFamily: "inherit",
              fontSize: "0.8125rem",
              lineHeight: "20px",
              color: "text.secondary",
              cursor: "pointer",
              textDecoration: "underline",
              textUnderlineOffset: "3px",
              "&:hover": { color: "text.primary" },
            }}
          >
            Sign out
          </Box>
        </Box>
      </Box>

      <Box
        component="main"
        sx={{
          flex: 1,
          p: { xs: 4, md: "80px 48px" },
          display: "flex",
          justifyContent: "flex-start",
        }}
      >
        <Box sx={{ maxWidth: 720, width: "100%" }}>
          <Typography
            component="h1"
            sx={{ fontSize: "2rem", lineHeight: "40px", fontWeight: 500, letterSpacing: "-0.01em" }}
          >
            Welcome, {user?.displayName ?? "there"}.
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mt: 2, maxWidth: 560 }}>
            Your radars and proposals will appear here soon.
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}
