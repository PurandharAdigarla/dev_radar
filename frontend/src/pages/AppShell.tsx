import { useEffect } from "react";
import { NavLink, Outlet, useLocation, Link } from "react-router-dom";
import { useSelector } from "react-redux";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useTheme } from "@mui/material/styles";
import { PulseDot } from "../components/PulseDot";
import { useAuth } from "../auth/useAuth";
import { useMeQuery } from "../api/authApi";
import type { RootState } from "../store";

const SIDEBAR_WIDTH = 240;

type NavItem =
  | { key: string; label: string; to: string; disabled?: false }
  | { key: string; label: string; disabled: true };

const NAV_ITEMS: NavItem[] = [
  { key: "radars", label: "Radars", to: "/app/radars" },
  { key: "interests", label: "Interests", to: "/app/interests" },
  { key: "settings", label: "Settings", to: "/app/settings" },
];

interface NavLinkItemProps {
  item: NavItem;
  active: boolean;
  showGeneratingDot: boolean;
  variant: "sidebar" | "topbar";
}

function NavLinkItem({ item, active, showGeneratingDot, variant }: NavLinkItemProps) {
  const isTopbar = variant === "topbar";
  const sharedSx = {
    display: "flex",
    alignItems: "center",
    gap: "6px",
    justifyContent: isTopbar ? "center" : "space-between",
    px: isTopbar ? "12px" : "10px",
    py: "8px",
    borderRadius: "6px",
    textDecoration: "none",
    fontSize: "0.9375rem",
    lineHeight: "24px",
    fontWeight: active ? 500 : 400,
    color: active ? "text.primary" : "text.secondary",
    bgcolor: active ? "rgba(45,42,38,0.04)" : "transparent",
    whiteSpace: "nowrap" as const,
    "&:hover": { color: "text.primary", bgcolor: "rgba(45,42,38,0.04)" },
  };

  if (item.disabled) {
    return (
      <Box sx={{ ...sharedSx, opacity: 0.6, cursor: "not-allowed", "&:hover": undefined }}>
        <span>{item.label}</span>
        {!isTopbar && (
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
        )}
      </Box>
    );
  }

  return (
    <Box component={NavLink} to={item.to} sx={sharedSx}>
      <span>{item.label}</span>
      {showGeneratingDot && <PulseDot size={8} />}
    </Box>
  );
}

export function AppShell() {
  const { user, logout, isAuthenticated, _setUser } = useAuth();
  const { data: meData } = useMeQuery(undefined, { skip: !isAuthenticated || user !== null });
  useEffect(() => {
    if (meData && !user) _setUser(meData);
  }, [meData, user, _setUser]);
  const generatingRadarId = useSelector((s: RootState) => s.radarGeneration.currentGeneratingRadarId);
  const location = useLocation();
  const muiTheme = useTheme();
  // Below 900px → collapsed top bar; at/above 900px → desktop sidebar.
  const isNarrow = useMediaQuery(muiTheme.breakpoints.down("md"));

  const navItemsRendered = NAV_ITEMS.map((item) => {
    const isActive = !item.disabled && location.pathname.startsWith(item.to);
    const showGeneratingDot = item.key === "radars" && generatingRadarId !== null;
    return (
      <NavLinkItem
        key={item.key}
        item={item}
        active={isActive}
        showGeneratingDot={showGeneratingDot}
        variant={isNarrow ? "topbar" : "sidebar"}
      />
    );
  });

  // ─── Mobile: top bar ──────────────────────────────────────────────
  if (isNarrow) {
    return (
      <Box sx={{ minHeight: "100vh", bgcolor: "background.default", display: "flex", flexDirection: "column" }}>
        <Box
          component="header"
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            px: 3,
            py: 2,
            borderBottom: 1,
            borderColor: "divider",
            bgcolor: "background.default",
            gap: 2,
            flexWrap: "wrap",
          }}
        >
          <Typography variant="overline" color="text.secondary">
            Dev Radar
          </Typography>

          <Box component="nav" sx={{ display: "flex", gap: "4px", flexWrap: "wrap" }}>
            {navItemsRendered}
          </Box>

          <Box
            component="button"
            onClick={logout}
            sx={{
              background: "transparent",
              border: "none",
              padding: 0,
              fontFamily: "inherit",
              fontSize: "0.8125rem",
              color: "text.secondary",
              cursor: "pointer",
              textDecoration: "underline",
              textUnderlineOffset: "3px",
              "&:hover": { color: "text.primary" },
            }}
          >
            Sign out · {user?.displayName ?? "me"}
          </Box>
        </Box>

        <Box
          component="main"
          sx={{ flex: 1, p: 3, display: "flex", justifyContent: "flex-start" }}
        >
          <Outlet />
        </Box>
      </Box>
    );
  }

  // ─── Desktop: sidebar ─────────────────────────────────────────────
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
          {navItemsRendered}
        </Box>

        <Box sx={{ flex: 1 }} />

        <Box sx={{ mb: 2 }}>
          <Box
            component={Link}
            to="/observability"
            sx={{
              display: "block",
              fontSize: "0.8125rem",
              color: "text.secondary",
              textDecoration: "none",
              "&:hover": { color: "text.primary" },
            }}
          >
            Public dashboard →
          </Box>
        </Box>

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
          p: "80px 48px",
          display: "flex",
          justifyContent: "flex-start",
        }}
      >
        <Outlet />
      </Box>
    </Box>
  );
}
