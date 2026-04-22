import { describe, it, expect, beforeEach } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { tokenStorage } from "../auth/tokenStorage";
import { GitHubCallback } from "./GitHubCallback";

describe("GitHubCallback", () => {
  beforeEach(() => {
    localStorage.clear();
    tokenStorage.clear();
  });

  it("persists token from URL hash and navigates to /app", async () => {
    // jsdom lets us set window.location.hash
    window.location.hash = "#accessToken=ghjwt-test";

    const store = makeStore();
    render(
      <Provider store={store}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={["/auth/github/complete"]}>
            <Routes>
              <Route path="/auth/github/complete" element={<GitHubCallback />} />
              <Route path="/app" element={<div>app-landed</div>} />
              <Route path="/login" element={<div>login-fallback</div>} />
            </Routes>
          </MemoryRouter>
        </ThemeProvider>
      </Provider>,
    );

    await waitFor(() => expect(tokenStorage.getAccess()).toBe("ghjwt-test"));
    await waitFor(() =>
      expect(document.body.textContent).toContain("app-landed"),
    );
  });

  it("redirects to /login when hash is missing", async () => {
    window.location.hash = "";

    const store = makeStore();
    render(
      <Provider store={store}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={["/auth/github/complete"]}>
            <Routes>
              <Route path="/auth/github/complete" element={<GitHubCallback />} />
              <Route path="/login" element={<div>login-fallback</div>} />
            </Routes>
          </MemoryRouter>
        </ThemeProvider>
      </Provider>,
    );

    await waitFor(() =>
      expect(document.body.textContent).toContain("login-fallback"),
    );
    expect(tokenStorage.getAccess()).toBeNull();
  });
});
