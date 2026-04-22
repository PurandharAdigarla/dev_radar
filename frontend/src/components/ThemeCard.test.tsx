import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
    { id: 1001, title: "Spring Boot 3.5 released", url: "https://spring.io/3.5", author: "spring-io" },
    { id: 1002, title: "Virtual threads deep-dive", url: "https://example.com/vt", author: null },
  ],
};

describe("ThemeCard", () => {
  it("renders title, summary, a SOURCES overline, and numbered citation pills", () => {
    render(withTheme(<ThemeCard theme={sample} />));
    expect(screen.getByRole("heading", { name: /spring boot ecosystem/i })).toBeInTheDocument();
    expect(screen.getByText(/virtual thread support/i)).toBeInTheDocument();
    expect(screen.getByText(/^sources$/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /\[1\]/i })).toHaveAttribute("href", "https://spring.io/3.5");
    expect(screen.getByRole("link", { name: /\[2\]/i })).toHaveAttribute("href", "https://example.com/vt");
  });

  it("omits the SOURCES overline when there are no items", () => {
    render(withTheme(<ThemeCard theme={{ ...sample, items: [] }} />));
    expect(screen.queryByText(/^sources$/i)).not.toBeInTheDocument();
  });

  it("reveals the source title on citation hover", async () => {
    const user = userEvent.setup();
    render(withTheme(<ThemeCard theme={sample} />));
    const pill = screen.getByRole("link", { name: /\[1\]/i });
    await user.hover(pill);
    expect(await screen.findByText(/spring boot 3\.5 released/i)).toBeInTheDocument();
  });
});

describe("ThemeSkeleton", () => {
  it("renders a placeholder with role=status", () => {
    render(withTheme(<ThemeSkeleton />));
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});
