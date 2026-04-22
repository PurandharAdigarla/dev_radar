import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { Provider } from "react-redux";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "./store";
import { theme } from "./theme";
import { AppRoutes } from "./App";
import { tokenStorage } from "./auth/tokenStorage";

function renderAt(path: string) {
  const store = makeStore();
  render(
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[path]}>
          <AppRoutes />
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
  return { user: userEvent.setup(), store };
}

describe("App routing", () => {
  it("shows landing at /", () => {
    renderAt("/");
    expect(screen.getByText(/weekly brief/i)).toBeInTheDocument();
  });

  it("redirects /app to /login when not authenticated", () => {
    localStorage.clear();
    tokenStorage.clear();
    renderAt("/app");
    expect(screen.getByRole("button", { name: /sign in/i })).toBeInTheDocument();
  });

  it("login flow lands at /app", async () => {
    localStorage.clear();
    tokenStorage.clear();
    const { user } = renderAt("/login");
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/password/i), "ok");
    await user.click(screen.getByRole("button", { name: /sign in/i }));
    await waitFor(() =>
      expect(screen.getByText(/^test user$/i)).toBeInTheDocument(),
    );
  });
});
