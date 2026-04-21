import { describe, it, expect, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { makeStore } from "../store";
import { useAuth } from "./useAuth";
import { tokenStorage } from "./tokenStorage";
import type { ReactNode } from "react";

function wrap(store = makeStore()) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <Provider store={store}>{children}</Provider>;
  };
}

describe("useAuth", () => {
  beforeEach(() => {
    localStorage.clear();
    tokenStorage.clear();
  });

  it("starts unauthenticated", () => {
    const { result } = renderHook(() => useAuth(), { wrapper: wrap() });
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
  });

  it("login sets tokens + user and flips isAuthenticated", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper: wrap() });
    await act(async () => {
      await result.current.login("a@b.com", "ok");
    });
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
    expect(result.current.user?.email).toBe("a@b.com");
    expect(tokenStorage.getAccess()).toBe("access-1");
    expect(tokenStorage.getRefresh()).toBe("refresh-1");
  });

  it("logout clears tokens + user", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper: wrap() });
    await act(async () => {
      await result.current.login("a@b.com", "ok");
    });
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
    act(() => result.current.logout());
    expect(result.current.isAuthenticated).toBe(false);
    expect(tokenStorage.getAccess()).toBeNull();
  });
});
