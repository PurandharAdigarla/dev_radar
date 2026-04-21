import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { makeStore } from "../store";
import { loginSucceeded } from "./authSlice";
import { ProtectedRoute } from "./ProtectedRoute";

function renderAt(path: string, authed: boolean) {
  const store = makeStore();
  if (authed) {
    store.dispatch(
      loginSucceeded({
        accessToken: "t",
        user: { id: 1, email: "a@b.com", displayName: "Alice" },
      }),
    );
  }
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/app" element={<div>protected</div>} />
          </Route>
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

describe("ProtectedRoute", () => {
  it("renders outlet when authenticated", () => {
    renderAt("/app", true);
    expect(screen.getByText("protected")).toBeInTheDocument();
  });

  it("redirects to /login when not authenticated", () => {
    renderAt("/app", false);
    expect(screen.getByText("login page")).toBeInTheDocument();
  });
});
