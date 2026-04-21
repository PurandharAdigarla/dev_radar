import { http, HttpResponse } from "msw";

export const handlers = [
  http.post("/api/auth/login", async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    if (body.password === "wrong") {
      return HttpResponse.json({ message: "Invalid credentials" }, { status: 401 });
    }
    return HttpResponse.json({
      accessToken: "access-1",
      refreshToken: "refresh-1",
      user: { id: 1, email: body.email, displayName: "Test User" },
    });
  }),

  http.post("/api/auth/register", async ({ request }) => {
    const body = (await request.json()) as { email: string };
    if (body.email === "dup@test.com") {
      return HttpResponse.json({ message: "Email already registered" }, { status: 409 });
    }
    return HttpResponse.json({ userId: 42 }, { status: 201 });
  }),

  http.get("/api/users/me", ({ request }) => {
    const auth = request.headers.get("Authorization");
    if (!auth?.startsWith("Bearer ")) {
      return HttpResponse.json({ message: "Unauthorized" }, { status: 401 });
    }
    if (auth === "Bearer expired") {
      return HttpResponse.json({ message: "Token expired" }, { status: 401 });
    }
    return HttpResponse.json({ id: 1, email: "a@b.com", displayName: "Alice" });
  }),

  http.post("/api/auth/refresh", async ({ request }) => {
    const body = (await request.json()) as { refreshToken: string };
    if (body.refreshToken === "bad") {
      return HttpResponse.json({ message: "Invalid refresh" }, { status: 401 });
    }
    return HttpResponse.json({ accessToken: "access-2", refreshToken: "refresh-2" });
  }),
];
