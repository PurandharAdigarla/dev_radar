import { describe, it, expect } from "vitest";
import { makeStore, type RootState } from "./index";

describe("store", () => {
  it("includes auth slice and authApi reducer", () => {
    const store = makeStore();
    const state = store.getState() as RootState;
    expect(state.auth).toBeDefined();
    expect(state.authApi).toBeDefined();
  });
});
