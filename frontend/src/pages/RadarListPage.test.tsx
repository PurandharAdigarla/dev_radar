import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { RadarListPage } from "./RadarListPage";
import { tokenStorage } from "../auth/tokenStorage";

function setup() {
  tokenStorage.setAccess("valid-token");
  render(
    <Provider store={makeStore()}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={["/app/radars"]}>
          <Routes>
            <Route path="/app/radars" element={<RadarListPage />} />
            <Route path="/app/radars/:id" element={<div>detail-page</div>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
  return { user: userEvent.setup() };
}

describe("RadarListPage", () => {
  it("renders heading and list of radars", async () => {
    setup();
    expect(screen.getByRole("heading", { name: /radars/i, level: 1 })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(/week of/i)).toBeInTheDocument());
  });

  it("enables the Generate button once interests load", async () => {
    setup();
    const btn = await screen.findByRole("button", { name: /generate new radar/i });
    await waitFor(() => expect(btn).toBeEnabled());
  });

  it("navigates to detail page on Generate", async () => {
    const { user } = setup();
    const btn = await screen.findByRole("button", { name: /generate new radar/i });
    await waitFor(() => expect(btn).toBeEnabled());
    await user.click(btn);
    await waitFor(() => expect(screen.getByText("detail-page")).toBeInTheDocument());
  });
});
