import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { theme } from "../theme";
import { Button } from "./Button";
import { TextField } from "./TextField";
import { Card } from "./Card";
import { Alert } from "./Alert";
import { PageLayout } from "./PageLayout";
import type { ReactNode } from "react";

function withTheme(ui: ReactNode) {
  return <ThemeProvider theme={theme}>{ui}</ThemeProvider>;
}

describe("primitives", () => {
  it("Button renders label and handles click", () => {
    render(withTheme(<Button>Sign in</Button>));
    expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument();
  });

  it("TextField renders with label", () => {
    render(withTheme(<TextField label="Email" />));
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
  });

  it("Card renders children", () => {
    render(withTheme(<Card><span>Hello</span></Card>));
    expect(screen.getByText("Hello")).toBeInTheDocument();
  });

  it("Alert renders message with role=alert", () => {
    render(withTheme(<Alert severity="error">Nope</Alert>));
    const alert = screen.getByRole("alert");
    expect(alert).toBeInTheDocument();
    expect(alert).toHaveTextContent("Nope");
  });

  it("PageLayout wraps children in centered container", () => {
    render(withTheme(<PageLayout><div data-testid="kid" /></PageLayout>));
    expect(screen.getByTestId("kid")).toBeInTheDocument();
  });
});
