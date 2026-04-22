import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { loginSucceeded } from "../auth/authSlice";
import { generationStarted } from "../radar/radarGenerationSlice";
import { AppShell } from "./AppShell";

function renderShell(authed: boolean, generating: boolean, initialPath = "/app/radars") {
  const store = makeStore();
  if (authed) {
    store.dispatch(
      loginSucceeded({
        accessToken: "t",
        user: { id: 1, email: "alice@test.com", displayName: "Alice" },
      }),
    );
  }
  if (generating) {
    store.dispatch(generationStarted({ radarId: 100, startedAt: "2026-04-22T10:00:00Z" }));
  }
  render(
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="/app/radars" element={<div data-testid="child">child-content</div>} />
              <Route path="/app/interests" element={<div data-testid="child">interests-content</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
}

describe("AppShell", () => {
  it("renders sidebar with Radars/Interests links and user block", () => {
    renderShell(true, false);
    expect(screen.getByRole("link", { name: /radars/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /interests/i })).toBeInTheDocument();
    expect(screen.getByText(/^alice$/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign out/i })).toBeInTheDocument();
    expect(screen.getByTestId("child")).toHaveTextContent("child-content");
  });

  it("shows Settings as disabled with 'soon' tag", () => {
    renderShell(true, false);
    expect(screen.getByText(/settings/i)).toBeInTheDocument();
    expect(screen.getByText(/^soon$/i)).toBeInTheDocument();
  });

  it("renders Interests child content when on /app/interests", () => {
    renderShell(true, false, "/app/interests");
    expect(screen.getByTestId("child")).toHaveTextContent("interests-content");
  });
});
