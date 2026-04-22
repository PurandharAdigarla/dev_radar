import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { InterestPickerPage } from "./InterestPickerPage";
import { tokenStorage } from "../auth/tokenStorage";

function setup() {
  tokenStorage.setAccess("valid-token");
  render(
    <Provider store={makeStore()}>
      <ThemeProvider theme={theme}>
        <MemoryRouter>
          <InterestPickerPage />
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
  return { user: userEvent.setup() };
}

describe("InterestPickerPage", () => {
  it("renders heading and search input", async () => {
    setup();
    expect(screen.getByRole("heading", { name: /interests/i, level: 1 })).toBeInTheDocument();
    expect(screen.getByLabelText(/search/i)).toBeInTheDocument();
  });

  it("loads tags and marks currently selected ones", async () => {
    setup();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: /^java$/i })).toHaveAttribute("aria-pressed", "true"),
    );
    expect(screen.getByRole("button", { name: /spring boot/i })).toHaveAttribute("aria-pressed", "false");
  });

  it("Save button enables after toggling a tag", async () => {
    const { user } = setup();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: /^java$/i })).toHaveAttribute("aria-pressed", "true"),
    );
    const save = screen.getByRole("button", { name: /save/i });
    expect(save).toBeDisabled();

    await user.click(screen.getByRole("button", { name: /spring boot/i }));
    expect(save).toBeEnabled();
  });

  it("shows selected count", async () => {
    setup();
    await waitFor(() =>
      expect(screen.getByText(/1 selected/i)).toBeInTheDocument(),
    );
  });

  it("filters tags by search query", async () => {
    const { user } = setup();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: /^java$/i })).toBeInTheDocument(),
    );
    await user.type(screen.getByLabelText(/search/i), "spring");
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /^java$/i })).not.toBeInTheDocument(),
    );
    expect(screen.getByRole("button", { name: /spring boot/i })).toBeInTheDocument();
  });
});
