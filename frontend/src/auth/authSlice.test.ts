import { describe, it, expect } from "vitest";
import { authReducer, loginSucceeded, loggedOut, setUser } from "./authSlice";
import type { User } from "../api/types";

const initial = authReducer(undefined, { type: "@@INIT" });

describe("authSlice", () => {
  it("starts with no token and no user", () => {
    expect(initial.accessToken).toBeNull();
    expect(initial.user).toBeNull();
  });

  it("stores token + user on loginSucceeded", () => {
    const u: User = { id: 1, email: "a@b.com", displayName: "A" };
    const s = authReducer(initial, loginSucceeded({ accessToken: "t", user: u }));
    expect(s.accessToken).toBe("t");
    expect(s.user).toEqual(u);
  });

  it("clears token + user on loggedOut", () => {
    const u: User = { id: 1, email: "a@b.com", displayName: "A" };
    const loggedIn = authReducer(initial, loginSucceeded({ accessToken: "t", user: u }));
    const out = authReducer(loggedIn, loggedOut());
    expect(out.accessToken).toBeNull();
    expect(out.user).toBeNull();
  });

  it("setUser updates the user profile", () => {
    const u: User = { id: 1, email: "a@b.com", displayName: "Alice" };
    const s = authReducer(initial, setUser(u));
    expect(s.user).toEqual(u);
  });
});
