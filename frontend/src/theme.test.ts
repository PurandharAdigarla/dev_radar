import { describe, it, expect } from "vitest";
import { theme } from "./theme";

describe("theme", () => {
  it("uses warm neutral background", () => {
    expect(theme.palette.background.default).toBe("#faf9f7");
  });

  it("uses Ink as the monochrome primary accent", () => {
    expect(theme.palette.primary.main).toBe("#2d2a26");
  });

  it("uses Inter as the sans font", () => {
    expect(theme.typography.fontFamily).toContain("Inter");
  });

  it("sets default border radius to 8px", () => {
    expect(theme.shape.borderRadius).toBe(8);
  });

  it("applies negative letter-spacing on h1", () => {
    expect(theme.typography.h1.letterSpacing).toBe("-0.02em");
  });

  it("uses responsive clamp on h1 so it collapses on mobile", () => {
    expect(theme.typography.h1.fontSize).toContain("clamp");
  });

  it("applies negative letter-spacing on h2", () => {
    expect(theme.typography.h2.letterSpacing).toBe("-0.01em");
  });

  it("provides a single subtle shadow level", () => {
    expect(theme.shadows[1]).toContain("rgba(45,42,38");
  });

  it("overrides Link hover color to stay monochrome", () => {
    const mui = (theme.components as unknown as {
      MuiLink?: { styleOverrides?: { root?: { "&:hover"?: { color?: string } } } };
    }).MuiLink;
    expect(mui?.styleOverrides?.root?.["&:hover"]?.color).toBe("#2d2a26");
  });
});
