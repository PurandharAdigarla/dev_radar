import { describe, it, expect, beforeEach } from "vitest";
import { tokenStorage } from "./tokenStorage";

describe("tokenStorage", () => {
  beforeEach(() => localStorage.clear());

  it("returns null when access token is absent", () => {
    expect(tokenStorage.getAccess()).toBeNull();
  });

  it("persists and reads access token", () => {
    tokenStorage.setAccess("abc");
    expect(tokenStorage.getAccess()).toBe("abc");
  });

  it("persists and reads refresh token", () => {
    tokenStorage.setRefresh("xyz");
    expect(tokenStorage.getRefresh()).toBe("xyz");
  });

  it("clears both tokens", () => {
    tokenStorage.setAccess("a");
    tokenStorage.setRefresh("r");
    tokenStorage.clear();
    expect(tokenStorage.getAccess()).toBeNull();
    expect(tokenStorage.getRefresh()).toBeNull();
  });
});
