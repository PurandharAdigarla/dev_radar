import { describe, it, expect, beforeEach } from "vitest";
import { configureStore } from "@reduxjs/toolkit";
import { interestApi } from "./interestApi";
import { tokenStorage } from "../auth/tokenStorage";

function makeStore() {
  return configureStore({
    reducer: { [interestApi.reducerPath]: interestApi.reducer },
    middleware: (gdm) => gdm().concat(interestApi.middleware),
  });
}

describe("interestApi", () => {
  beforeEach(() => {
    tokenStorage.clear();
    tokenStorage.setAccess("valid-token");
  });

  it("lists all interest tags", async () => {
    const store = makeStore();
    const res = await store.dispatch(interestApi.endpoints.listTags.initiate({}));
    expect(res.data?.content).toHaveLength(5);
    expect(res.data?.content[0].slug).toBe("java");
  });

  it("filters by query", async () => {
    const store = makeStore();
    const res = await store.dispatch(interestApi.endpoints.listTags.initiate({ q: "spring" }));
    expect(res.data?.content).toHaveLength(1);
    expect(res.data?.content[0].slug).toBe("spring_boot");
  });

  it("filters by category", async () => {
    const store = makeStore();
    const res = await store.dispatch(interestApi.endpoints.listTags.initiate({ category: "database" }));
    expect(res.data?.content).toHaveLength(1);
    expect(res.data?.content[0].slug).toBe("postgresql");
  });

  it("gets my interests", async () => {
    const store = makeStore();
    const res = await store.dispatch(interestApi.endpoints.getMyInterests.initiate());
    expect(res.data).toHaveLength(1);
    expect(res.data?.[0].slug).toBe("java");
  });

  it("sets my interests", async () => {
    const store = makeStore();
    const res = await store.dispatch(
      interestApi.endpoints.setMyInterests.initiate({ tagSlugs: ["java", "docker"] }),
    );
    expect(res.data).toHaveLength(2);
    expect(res.data?.map((t) => t.slug)).toEqual(["java", "docker"]);
  });
});
