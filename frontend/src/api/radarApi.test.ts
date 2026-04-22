import { describe, it, expect, beforeEach } from "vitest";
import { configureStore } from "@reduxjs/toolkit";
import { radarApi } from "./radarApi";
import { tokenStorage } from "../auth/tokenStorage";

function makeStore() {
  return configureStore({
    reducer: { [radarApi.reducerPath]: radarApi.reducer },
    middleware: (gdm) => gdm().concat(radarApi.middleware),
  });
}

describe("radarApi", () => {
  beforeEach(() => {
    tokenStorage.clear();
    tokenStorage.setAccess("valid-token");
  });

  it("lists radars", async () => {
    const store = makeStore();
    const res = await store.dispatch(radarApi.endpoints.list.initiate({ page: 0, size: 20 }));
    expect(res.data?.content).toHaveLength(1);
    expect(res.data?.content[0].id).toBe(42);
  });

  it("gets a radar by id", async () => {
    const store = makeStore();
    const res = await store.dispatch(radarApi.endpoints.get.initiate(42));
    expect(res.data?.id).toBe(42);
    expect(res.data?.themes).toHaveLength(1);
  });

  it("returns 404 for unknown radar", async () => {
    const store = makeStore();
    const res = await store.dispatch(radarApi.endpoints.get.initiate(404));
    expect(res.error).toBeDefined();
    expect((res.error as { status: number }).status).toBe(404);
  });

  it("creates a new radar", async () => {
    const store = makeStore();
    const res = await store.dispatch(radarApi.endpoints.create.initiate());
    expect(res.data?.status).toBe("GENERATING");
    expect(res.data?.id).toBe(100);
  });
});
