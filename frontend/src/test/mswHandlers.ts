import { http, HttpResponse } from "msw";

export const handlers = [
  http.post("/api/auth/login", async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    if (body.password === "wrong") {
      return HttpResponse.json({ message: "Invalid credentials" }, { status: 401 });
    }
    // Match real backend — login returns only tokens.
    void body;
    return HttpResponse.json({
      accessToken: "access-1",
      refreshToken: "refresh-1",
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
    // displayName "Test User" is asserted in App.test.tsx's login flow
    // (`welcome, test user`), and email "a@b.com" is asserted in useAuth.test.tsx.
    return HttpResponse.json({ id: 1, email: "a@b.com", displayName: "Test User" });
  }),

  http.post("/api/auth/refresh", async ({ request }) => {
    const body = (await request.json()) as { refreshToken: string };
    if (body.refreshToken === "bad") {
      return HttpResponse.json({ message: "Invalid refresh" }, { status: 401 });
    }
    return HttpResponse.json({ accessToken: "access-2", refreshToken: "refresh-2" });
  }),

  http.get("/api/interest-tags", ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get("q")?.toLowerCase() ?? "";
    const category = url.searchParams.get("category");
    const all = [
      { id: 1, slug: "java", displayName: "Java", category: "language" },
      { id: 2, slug: "spring_boot", displayName: "Spring Boot", category: "framework" },
      { id: 3, slug: "postgresql", displayName: "PostgreSQL", category: "database" },
      { id: 4, slug: "docker", displayName: "Docker", category: "devops" },
      { id: 5, slug: "security", displayName: "Security", category: "security" },
    ];
    const filtered = all.filter((t) => {
      if (q && !t.displayName.toLowerCase().includes(q) && !t.slug.includes(q)) return false;
      if (category && t.category !== category) return false;
      return true;
    });
    return HttpResponse.json({
      content: filtered,
      totalElements: filtered.length,
      totalPages: 1,
      number: 0,
      size: filtered.length,
    });
  }),

  http.get("/api/users/me/interests", ({ request }) => {
    const auth = request.headers.get("Authorization");
    if (!auth?.startsWith("Bearer ")) {
      return HttpResponse.json({ message: "Unauthorized" }, { status: 401 });
    }
    return HttpResponse.json([
      { id: 1, slug: "java", displayName: "Java", category: "language" },
    ]);
  }),

  http.put("/api/users/me/interests", async ({ request }) => {
    const body = (await request.json()) as { tagSlugs: string[] };
    const all = [
      { id: 1, slug: "java", displayName: "Java", category: "language" },
      { id: 2, slug: "spring_boot", displayName: "Spring Boot", category: "framework" },
      { id: 3, slug: "postgresql", displayName: "PostgreSQL", category: "database" },
      { id: 4, slug: "docker", displayName: "Docker", category: "devops" },
      { id: 5, slug: "security", displayName: "Security", category: "security" },
    ];
    return HttpResponse.json(all.filter((t) => body.tagSlugs.includes(t.slug)));
  }),
];
