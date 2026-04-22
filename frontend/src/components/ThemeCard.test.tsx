import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { theme } from "../theme";
import { ThemeCard } from "./ThemeCard";
import { ThemeSkeleton } from "./ThemeSkeleton";
import type { RadarTheme } from "../api/types";

function withTheme(ui: React.ReactNode) {
  return <ThemeProvider theme={theme}>{ui}</ThemeProvider>;
}

const sample: RadarTheme = {
  id: 1,
  title: "Spring Boot ecosystem updates",
  summary: "Spring Boot 3.5 ships with virtual thread support.",
  displayOrder: 0,
  items: [
    { id: 1001, title: "Spring Boot 3.5 released", description: "Major release with virtual thread support.", url: "https://spring.io/3.5", author: "spring-io", sourceName: "HN" },
    { id: 1002, title: "Virtual threads deep-dive", description: null, url: "https://example.com/vt", author: null, sourceName: "GH_TRENDING" },
  ],
};

describe("ThemeCard", () => {
  it("renders title, summary, and source cards for each item", () => {
    render(withTheme(<ThemeCard theme={sample} />));
    expect(screen.getByRole("heading", { name: /spring boot ecosystem/i })).toBeInTheDocument();
    expect(screen.getByText(/virtual thread support/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /spring boot 3\.5 released/i })).toHaveAttribute("href", "https://spring.io/3.5");
    expect(screen.getByRole("link", { name: /virtual threads deep-dive/i })).toHaveAttribute("href", "https://example.com/vt");
  });

  it("omits the source list when there are no items", () => {
    render(withTheme(<ThemeCard theme={{ ...sample, items: [] }} />));
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("renders description when present", () => {
    render(withTheme(<ThemeCard theme={sample} />));
    expect(screen.getByText(/major release with virtual thread support/i)).toBeInTheDocument();
  });
});

describe("ThemeSkeleton", () => {
  it("renders a placeholder with role=status", () => {
    render(withTheme(<ThemeSkeleton />));
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});
