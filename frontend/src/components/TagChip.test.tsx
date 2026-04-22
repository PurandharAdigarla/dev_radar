import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { theme } from "../theme";
import { TagChip } from "./TagChip";

function withTheme(ui: React.ReactNode) {
  return <ThemeProvider theme={theme}>{ui}</ThemeProvider>;
}

describe("TagChip", () => {
  it("renders label", () => {
    render(withTheme(<TagChip label="Java" selected={false} onToggle={() => {}} />));
    expect(screen.getByRole("button", { name: /java/i })).toBeInTheDocument();
  });

  it("fires onToggle on click", async () => {
    const user = userEvent.setup();
    const onToggle = vi.fn();
    render(withTheme(<TagChip label="Java" selected={false} onToggle={onToggle} />));
    await user.click(screen.getByRole("button", { name: /java/i }));
    expect(onToggle).toHaveBeenCalledOnce();
  });

  it("exposes aria-pressed based on selected", () => {
    const { rerender } = render(
      withTheme(<TagChip label="Java" selected={false} onToggle={() => {}} />),
    );
    expect(screen.getByRole("button")).toHaveAttribute("aria-pressed", "false");
    rerender(withTheme(<TagChip label="Java" selected={true} onToggle={() => {}} />));
    expect(screen.getByRole("button")).toHaveAttribute("aria-pressed", "true");
  });
});
