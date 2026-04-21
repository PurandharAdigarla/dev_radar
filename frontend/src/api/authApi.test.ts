import { describe, it, expect, beforeEach } from "vitest";
import { configureStore } from "@reduxjs/toolkit";
import { authApi } from "./authApi";
import { authReducer } from "../auth/authSlice";
import { tokenStorage } from "../auth/tokenStorage";

function makeStore() {
  return configureStore({
    reducer: {
      [authApi.reducerPath]: authApi.reducer,
      auth: authReducer,
    },
    middleware: (gdm) => gdm().concat(authApi.middleware),
  });
}

describe("authApi", () => {
  beforeEach(() => {
    localStorage.clear();
    tokenStorage.clear();
  });

  it("login returns token + user on success", async () => {
    const store = makeStore();
    const result = await store.dispatch(
      authApi.endpoints.login.initiate({ email: "a@b.com", password: "ok" }),
    );
    expect(result.data?.accessToken).toBe("access-1");
    expect(result.data?.user.email).toBe("a@b.com");
  });

  it("login surfaces 401 error on bad credentials", async () => {
    const store = makeStore();
    const result = await store.dispatch(
      authApi.endpoints.login.initiate({ email: "a@b.com", password: "wrong" }),
    );
    expect(result.error).toBeDefined();
    expect((result.error as { status: number }).status).toBe(401);
  });

  it("register succeeds for fresh email", async () => {
    const store = makeStore();
    const result = await store.dispatch(
      authApi.endpoints.register.initiate({
        email: "new@test.com",
        password: "Password1!",
        displayName: "New",
      }),
    );
    expect(result.data?.userId).toBe(42);
  });

  it("me endpoint injects Authorization header from tokenStorage", async () => {
    tokenStorage.setAccess("valid-token");
    const store = makeStore();
    const result = await store.dispatch(authApi.endpoints.me.initiate());
    expect(result.data?.displayName).toBe("Alice");
  });
});
