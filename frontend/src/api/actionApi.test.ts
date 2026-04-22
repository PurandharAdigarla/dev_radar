import { describe, it, expect, beforeEach } from "vitest";
import { configureStore } from "@reduxjs/toolkit";
import { actionApi } from "./actionApi";
import { tokenStorage } from "../auth/tokenStorage";

function makeStore() {
  return configureStore({
    reducer: { [actionApi.reducerPath]: actionApi.reducer },
    middleware: (gdm) => gdm().concat(actionApi.middleware),
  });
}

describe("actionApi", () => {
  beforeEach(() => {
    tokenStorage.clear();
    tokenStorage.setAccess("valid-token");
  });

  it("lists proposals for a radar", async () => {
    const store = makeStore();
    const res = await store.dispatch(actionApi.endpoints.listByRadar.initiate(42));
    expect(res.data).toHaveLength(1);
    expect(res.data?.[0].status).toBe("PROPOSED");
  });

  it("returns empty list for radar with no proposals", async () => {
    const store = makeStore();
    const res = await store.dispatch(actionApi.endpoints.listByRadar.initiate(99));
    expect(res.data).toEqual([]);
  });

  it("approves a proposal with fix version", async () => {
    const store = makeStore();
    const res = await store.dispatch(
      actionApi.endpoints.approve.initiate({ id: 7, fixVersion: "2.17.1" }),
    );
    expect(res.data?.status).toBe("EXECUTED");
    expect(res.data?.prUrl).toBe("https://github.com/alice/api/pull/99");
  });

  it("dismisses a proposal", async () => {
    const store = makeStore();
    const res = await store.dispatch(actionApi.endpoints.dismiss.initiate(7));
    expect(res.data?.status).toBe("DISMISSED");
  });
});
