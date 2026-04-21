import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { loginSucceeded } from "../auth/authSlice";
import { AppShell } from "./AppShell";

describe("AppShell", () => {
  it("renders welcome with user's display name", () => {
    const store = makeStore();
    store.dispatch(
      loginSucceeded({
        accessToken: "t",
        user: { id: 1, email: "alice@test.com", displayName: "Alice" },
      }),
    );
    render(
      <Provider store={store}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <AppShell />
          </MemoryRouter>
        </ThemeProvider>
      </Provider>,
    );
    expect(screen.getByText(/welcome, alice/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign out/i })).toBeInTheDocument();
  });
});
