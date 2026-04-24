import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { theme } from "../theme";
import { RadarRow } from "./RadarRow";
import type { RadarSummary } from "../api/types";

function withWrappers(ui: React.ReactNode) {
  return (
    <ThemeProvider theme={theme}>
      <MemoryRouter>{ui}</MemoryRouter>
    </ThemeProvider>
  );
}

const ready: RadarSummary = {
  id: 42, status: "READY",
  periodStart: "2026-04-13T00:00:00Z",
  periodEnd: "2026-04-20T00:00:00Z",
  generatedAt: "2026-04-20T10:00:00Z",
  generationMs: 12000, tokenCount: 4200, themeCount: 3,
};

describe("RadarRow", () => {
  it("renders the period as the primary line", () => {
    render(withWrappers(<RadarRow radar={ready} />));
    expect(screen.getByText(/week of/i)).toBeInTheDocument();
  });

  it("renders metadata caption with tokens and seconds", () => {
    render(withWrappers(<RadarRow radar={ready} />));
    expect(screen.getByText(/4\.2k tokens/i)).toBeInTheDocument();
    expect(screen.getByText(/12\.0s/)).toBeInTheDocument();
  });

  it("renders 'Ready' as plain uppercase text", () => {
    render(withWrappers(<RadarRow radar={ready} />));
    expect(screen.getByText(/^ready$/i)).toBeInTheDocument();
  });

  it("links to /app/radars/:id", () => {
    render(withWrappers(<RadarRow radar={ready} />));
    expect(screen.getByRole("link")).toHaveAttribute("href", "/app/radars/42");
  });

  it("shows 'Generating' with a pulsing dot and streaming caption", () => {
    render(withWrappers(<RadarRow radar={{ ...ready, status: "GENERATING", generatedAt: null, generationMs: null }} />));
    expect(screen.getByText(/generating/i)).toBeInTheDocument();
    expect(screen.getByText(/streaming themes/i)).toBeInTheDocument();
  });
});
