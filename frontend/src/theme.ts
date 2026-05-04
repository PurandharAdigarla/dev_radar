import { createTheme, alpha } from "@mui/material/styles";

export const colors = {
  primary: "#57534e",
  primaryHover: "#78716c",
  primaryDark: "#44403c",
  secondary: "#78716c",
  tertiary: "#a8a29e",
  success: "#6b7c5e",
  error: "#a05a50",
  warning: "#a08050",

  sidebarBg: "#1c1917",
  sidebarSurface: "rgba(255,255,255,0.04)",
  sidebarActive: "rgba(168,162,158,0.15)",
  sidebarBorder: "rgba(255,255,255,0.06)",

  bgDefault: "#fafaf9",
  bgPaper: "#ffffff",
  bgSubtle: "#f5f5f4",

  text: "#1c1917",
  textSecondary: "#57534e",
  textMuted: "#a8a29e",
  divider: "#e7e5e4",

  gradientHero: "linear-gradient(135deg, #1c1917 0%, #292524 40%, #1c1917 100%)",
  gradientPrimary: "linear-gradient(135deg, #57534e 0%, #44403c 100%)",
  gradientText: "linear-gradient(135deg, #44403c 0%, #78716c 50%, #57534e 100%)",
  gradientCard: "linear-gradient(135deg, rgba(87,83,78,0.04) 0%, rgba(120,113,108,0.04) 100%)",
  glowPrimary: "0 4px 20px rgba(28,25,23,0.15)",
  glowCyan: "0 4px 20px rgba(168,162,158,0.2)",
} as const;

export const fonts = {
  headline: '"Space Grotesk", system-ui, sans-serif',
  body: '"Inter", system-ui, sans-serif',
  mono: '"JetBrains Mono", ui-monospace, "SF Mono", Menlo, monospace',
} as const;

export const theme = createTheme({
  palette: {
    mode: "light",
    background: { default: colors.bgDefault, paper: colors.bgPaper },
    text: { primary: colors.text, secondary: colors.textSecondary },
    primary: { main: colors.primary, dark: colors.primaryDark, light: colors.primaryHover, contrastText: "#fff" },
    secondary: { main: colors.secondary },
    error: { main: colors.error },
    success: { main: colors.success },
    warning: { main: colors.warning },
    divider: colors.divider,
  },
  typography: {
    fontFamily: fonts.body,
    h1: {
      fontFamily: fonts.headline,
      fontSize: "clamp(2.25rem, 5vw, 3.5rem)",
      lineHeight: 1.1,
      fontWeight: 700,
      letterSpacing: "-0.03em",
    },
    h2: {
      fontFamily: fonts.headline,
      fontSize: "1.75rem",
      lineHeight: 1.2,
      fontWeight: 700,
      letterSpacing: "-0.02em",
    },
    h3: {
      fontFamily: fonts.headline,
      fontSize: "1.25rem",
      lineHeight: 1.3,
      fontWeight: 600,
      letterSpacing: "-0.01em",
    },
    body1: { fontSize: "0.9375rem", lineHeight: 1.6, fontWeight: 400 },
    body2: { fontSize: "0.875rem", lineHeight: 1.6, fontWeight: 400, color: colors.textSecondary },
    caption: { fontSize: "0.8125rem", lineHeight: 1.5, fontWeight: 400, color: colors.textMuted },
    overline: {
      fontSize: "0.6875rem",
      lineHeight: 1.5,
      fontWeight: 600,
      letterSpacing: "0.1em",
      textTransform: "uppercase",
      color: colors.textMuted,
    },
    button: { textTransform: "none", fontWeight: 600, fontSize: "0.875rem", letterSpacing: "-0.01em" },
  },
  shape: { borderRadius: 12 },
  shadows: [
    "none",
    "0 1px 3px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.02)",
    "0 4px 6px rgba(0,0,0,0.04), 0 2px 4px rgba(0,0,0,0.02)",
    "0 10px 15px rgba(0,0,0,0.04), 0 4px 6px rgba(0,0,0,0.02)",
    "0 20px 25px rgba(0,0,0,0.05), 0 8px 10px rgba(0,0,0,0.02)",
    ...Array(20).fill("0 20px 25px rgba(0,0,0,0.05), 0 8px 10px rgba(0,0,0,0.02)"),
  ] as unknown as typeof createTheme extends (o: infer O) => unknown ? O extends { shadows?: infer S } ? S : never : never,
  components: {
    MuiCssBaseline: {
      styleOverrides: `
        @import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap');
        html { scroll-behavior: smooth; }
        body { -webkit-font-smoothing: antialiased; }
        ::selection { background: ${alpha(colors.primary, 0.15)}; }
      `,
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          borderRadius: 999,
          padding: "10px 24px",
          fontFamily: fonts.body,
          fontWeight: 600,
          fontSize: "0.875rem",
          transition: "all 0.2s cubic-bezier(0.4, 0, 0.2, 1)",
          "&:focus-visible": { boxShadow: `0 0 0 3px ${alpha(colors.primary, 0.25)}` },
        },
        contained: {
          background: colors.primaryDark,
          color: "#fff",
          "&:hover": {
            background: colors.primary,
            transform: "translateY(-1px)",
            boxShadow: colors.glowPrimary,
          },
        },
        outlined: {
          borderColor: colors.divider,
          color: colors.text,
          "&:hover": {
            borderColor: colors.primary,
            color: colors.primary,
            backgroundColor: alpha(colors.primary, 0.04),
          },
        },
        text: {
          color: colors.textSecondary,
          padding: "8px 16px",
          "&:hover": { color: colors.text, backgroundColor: alpha(colors.primary, 0.04) },
        },
      },
    },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: {
          backgroundImage: "none",
          border: `1px solid ${colors.divider}`,
          borderRadius: 12,
          transition: "all 0.2s cubic-bezier(0.4, 0, 0.2, 1)",
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          border: `1px solid ${colors.divider}`,
          borderRadius: 12,
          transition: "all 0.25s cubic-bezier(0.4, 0, 0.2, 1)",
          "&:hover": {
            borderColor: alpha(colors.primary, 0.3),
            boxShadow: `0 8px 30px ${alpha(colors.primary, 0.06)}`,
            transform: "translateY(-2px)",
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          fontWeight: 500,
          fontSize: "0.8125rem",
          transition: "all 0.15s ease",
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 10,
          transition: "all 0.2s ease",
          "& .MuiOutlinedInput-notchedOutline": { borderColor: colors.divider, transition: "all 0.2s ease" },
          "&:hover .MuiOutlinedInput-notchedOutline": { borderColor: alpha(colors.primary, 0.4) },
          "&.Mui-focused .MuiOutlinedInput-notchedOutline": { borderColor: colors.primary, borderWidth: 2 },
          "&.Mui-focused": { boxShadow: `0 0 0 3px ${alpha(colors.primary, 0.1)}` },
        },
        input: { padding: "12px 16px", fontSize: "0.9375rem" },
      },
    },
    MuiInputLabel: {
      styleOverrides: {
        root: {
          position: "static",
          transform: "none",
          fontSize: "0.8125rem",
          fontWeight: 600,
          color: colors.text,
          marginBottom: 6,
          "&.Mui-focused": { color: colors.primary },
        },
      },
    },
    MuiTextField: { defaultProps: { variant: "outlined" } },
    MuiDivider: { styleOverrides: { root: { borderColor: colors.divider } } },
    MuiLink: {
      styleOverrides: {
        root: {
          color: colors.primary,
          textDecorationColor: alpha(colors.primary, 0.3),
          textUnderlineOffset: "3px",
          transition: "all 0.15s ease",
          "&:hover": { textDecorationColor: colors.primary },
        },
      },
    },
    MuiSkeleton: {
      styleOverrides: {
        root: { borderRadius: 8, backgroundColor: alpha(colors.primary, 0.06) },
      },
    },
  },
});
