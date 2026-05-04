import { useEffect } from "react";
import { Outlet } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import RadarRounded from "@mui/icons-material/RadarRounded";
import { useAuth } from "../auth/useAuth";
import { useMeQuery } from "../api/authApi";
import { colors, fonts } from "../theme";

export function AppShell() {
  const { user, logout, isAuthenticated, _setUser } = useAuth();
  const { data: meData } = useMeQuery(undefined, { skip: !isAuthenticated || user !== null });
  useEffect(() => {
    if (meData && !user) _setUser(meData);
  }, [meData, user, _setUser]);

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: colors.bgDefault }}>
      {/* Top bar */}
      <Box
        component="header"
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          px: { xs: 2, md: 4 },
          py: 1.5,
          borderBottom: `1px solid ${colors.divider}`,
          bgcolor: colors.bgPaper,
          position: "sticky",
          top: 0,
          zIndex: 50,
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <RadarRounded sx={{ fontSize: 22, color: colors.primary }} />
          <Typography
            sx={{
              fontFamily: fonts.headline,
              fontWeight: 700,
              fontSize: "1rem",
              letterSpacing: "-0.02em",
              color: colors.text,
            }}
          >
            Dev Radar
          </Typography>
        </Box>

        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          {user && (
            <Typography sx={{ fontSize: "0.8125rem", color: colors.textSecondary, fontWeight: 500 }}>
              {user.displayName}
            </Typography>
          )}
          <Box
            component="button"
            onClick={logout}
            sx={{
              background: "none",
              border: `1px solid ${colors.divider}`,
              borderRadius: "6px",
              px: 1.5,
              py: 0.5,
              fontFamily: "inherit",
              fontSize: "0.75rem",
              fontWeight: 500,
              color: colors.textSecondary,
              cursor: "pointer",
              transition: "all 0.15s ease",
              "&:hover": {
                borderColor: colors.primary,
                color: colors.text,
              },
            }}
          >
            Sign out
          </Box>
        </Box>
      </Box>

      {/* Content */}
      <Box component="main" sx={{ px: { xs: 2, md: 4 }, py: 3 }}>
        <Outlet />
      </Box>
    </Box>
  );
}
