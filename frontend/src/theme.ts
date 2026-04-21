import { createTheme } from "@mui/material/styles";

const subtleShadow = "0 1px 2px rgba(45,42,38,0.04), 0 1px 1px rgba(45,42,38,0.03)";

const INK = "#2d2a26";
const INK_HOVER = "#000000";
const DIVIDER = "#e8e4df";

export const theme = createTheme({
  palette: {
    mode: "light",
    background: {
      default: "#faf9f7",
      paper: "#ffffff",
    },
    text: {
      primary: INK,
      secondary: "#6b655e",
    },
    primary: {
      main: INK,
      dark: INK_HOVER,
      contrastText: "#ffffff",
    },
    divider: DIVIDER,
    error: { main: "#b3261e" },
    success: { main: "#2d7a3e" },
  },
  typography: {
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif',
    h1: {
      // Responsive clamp — collapses from 48px to 32px on small viewports so
      // `textWrap: balance` doesn't force awkward line breaks on mobile.
      fontSize: "clamp(2rem, 6vw, 3rem)",
      lineHeight: 1.15,
      fontWeight: 500,
      letterSpacing: "-0.02em",
    },
    h2: {
      fontSize: "1.5rem",
      lineHeight: "32px",
      fontWeight: 500,
      letterSpacing: "-0.01em",
    },
    h3: { fontSize: "1.125rem", lineHeight: 1.44, fontWeight: 500 },
    body1: { fontSize: "0.9375rem", lineHeight: 1.6, fontWeight: 400 },
    body2: { fontSize: "0.875rem", lineHeight: "20px", fontWeight: 400 },
    caption: { fontSize: "0.8125rem", lineHeight: "20px", fontWeight: 400 },
    overline: {
      fontSize: "0.6875rem",
      lineHeight: "16px",
      fontWeight: 500,
      letterSpacing: "0.08em",
      textTransform: "uppercase",
    },
    button: {
      textTransform: "none",
      fontWeight: 500,
      fontSize: "0.9375rem",
      lineHeight: 1,
      letterSpacing: 0,
    },
  },
  shape: { borderRadius: 8 },
  shadows: [
    "none",
    subtleShadow,
    subtleShadow, subtleShadow, subtleShadow, subtleShadow, subtleShadow,
    subtleShadow, subtleShadow, subtleShadow, subtleShadow, subtleShadow,
    subtleShadow, subtleShadow, subtleShadow, subtleShadow, subtleShadow,
    subtleShadow, subtleShadow, subtleShadow, subtleShadow, subtleShadow,
    subtleShadow, subtleShadow, subtleShadow,
  ],
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true, disableRipple: false },
      styleOverrides: {
        root: {
          borderRadius: 999,
          padding: "12px 20px",
          boxShadow: "none",
          "&:focus-visible": {
            boxShadow: `0 0 0 2px ${INK}`,
          },
        },
        contained: {
          "&:hover": { boxShadow: "none", backgroundColor: INK_HOVER },
        },
        outlined: {
          borderColor: DIVIDER,
          color: INK,
          "&:hover": { borderColor: DIVIDER, backgroundColor: "rgba(45,42,38,0.04)" },
        },
        text: {
          padding: "6px 8px",
          borderRadius: 6,
          color: "#6b655e",
          fontSize: "0.875rem",
          "&:hover": { color: INK, backgroundColor: "transparent" },
        },
      },
    },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: { root: { backgroundImage: "none" } },
    },
    MuiDivider: { styleOverrides: { root: { borderColor: DIVIDER } } },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          "& .MuiOutlinedInput-notchedOutline": { borderColor: DIVIDER },
          "&:hover .MuiOutlinedInput-notchedOutline": { borderColor: DIVIDER },
          "&.Mui-focused .MuiOutlinedInput-notchedOutline": { borderColor: INK, borderWidth: 1 },
          "&.Mui-focused": { boxShadow: `0 0 0 2px ${INK}` },
        },
        input: { padding: "10px 14px", fontSize: "0.9375rem", lineHeight: "24px" },
      },
    },
    MuiInputLabel: {
      styleOverrides: {
        root: {
          position: "static",
          transform: "none",
          fontSize: "0.8125rem",
          lineHeight: "20px",
          fontWeight: 500,
          color: INK,
          marginBottom: 8,
          "&.Mui-focused": { color: INK },
        },
      },
    },
    MuiTextField: { defaultProps: { variant: "outlined" } },
    MuiLink: {
      defaultProps: { underline: "always" },
      styleOverrides: {
        root: {
          color: INK,
          textUnderlineOffset: "3px",
          textDecorationColor: DIVIDER,
          transition: "text-decoration-color 120ms ease",
          "&:hover": {
            color: INK,
            textDecorationColor: INK,
          },
        },
      },
    },
  },
});

/*
 * Curvature note — intentional:
 * Buttons use `border-radius: 999` (pill), inputs use `border-radius: 8`.
 * These mirror the Claude Design export and match Claude.ai's own pattern —
 * pill buttons read as "action chips," rounded-rect inputs read as "fields."
 * A true pill input looks off; fully square buttons lose the Anthropic feel.
 * The contrast is deliberate, not sloppy.
 */

export const serifStack = '"Source Serif Pro", "Source Serif 4", Georgia, serif';
export const monoStack = '"JetBrains Mono", ui-monospace, "SF Mono", Menlo, monospace';
