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

  http.get("/api/radars", ({ request }) => {
    const auth = request.headers.get("Authorization");
    if (!auth?.startsWith("Bearer ")) return HttpResponse.json({ message: "Unauthorized" }, { status: 401 });
    return HttpResponse.json({
      content: [
        {
          id: 42,
          status: "READY",
          periodStart: "2026-04-13T00:00:00Z",
          periodEnd: "2026-04-20T00:00:00Z",
          generatedAt: "2026-04-20T10:00:00Z",
          generationMs: 12000,
          tokenCount: 4200,
          themeCount: 3,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    });
  }),

  http.get("/api/radars/:id", ({ params, request }) => {
    const auth = request.headers.get("Authorization");
    if (!auth?.startsWith("Bearer ")) return HttpResponse.json({ message: "Unauthorized" }, { status: 401 });
    const id = Number(params.id);
    if (id === 404) {
      return HttpResponse.json({ message: "Not found" }, { status: 404 });
    }
    if (id === 43) {
      return HttpResponse.json({
        id: 43,
        status: "GENERATING",
        periodStart: "2026-04-15T00:00:00Z",
        periodEnd: "2026-04-22T00:00:00Z",
        generatedAt: null,
        generationMs: null,
        tokenCount: null,
        themes: [],
      });
    }
    return HttpResponse.json({
      id,
      status: "READY",
      periodStart: "2026-04-13T00:00:00Z",
      periodEnd: "2026-04-20T00:00:00Z",
      generatedAt: "2026-04-20T10:00:00Z",
      generationMs: 12000,
      tokenCount: 4200,
      themes: [
        {
          id: 1, title: "Spring Boot ecosystem updates", summary: "Summary text.",
          displayOrder: 0,
          items: [
            { id: 1001, title: "Spring Boot 3.5 released", description: "Major release with virtual thread support.", url: "https://spring.io/3.5", author: "spring-io", sourceName: "GH_RELEASES" },
            { id: 1002, title: "What's New in Spring Boot 3.5", description: "Deep dive into the new features.", url: "https://baeldung.com/spring-boot-3-5", author: "Baeldung", sourceName: "ARTICLE" },
          ],
        },
      ],
    });
  }),

  http.post("/api/radars", ({ request }) => {
    const auth = request.headers.get("Authorization");
    if (!auth?.startsWith("Bearer ")) return HttpResponse.json({ message: "Unauthorized" }, { status: 401 });
    return HttpResponse.json({
      id: 100,
      status: "GENERATING",
      periodStart: "2026-04-15T00:00:00Z",
      periodEnd: "2026-04-22T00:00:00Z",
      generatedAt: null,
      generationMs: null,
      tokenCount: null,
    }, { status: 201 });
  }),

  http.get("/api/actions/proposals", ({ request }) => {
    const url = new URL(request.url);
    const radarId = url.searchParams.get("radar_id");
    if (radarId === "42") {
      return HttpResponse.json([
        {
          id: 7, radarId: 42, kind: "auto_pr_cve",
          payloadJson: JSON.stringify({
            ghsa_id: "GHSA-xxxx-yyyy-zzzz", package: "jackson-databind",
            current_version: "2.16.1", repo: "alice/api",
            file_path: "pom.xml", file_sha: "abc123",
          }),
          status: "PROPOSED", prUrl: null, failureReason: null,
          createdAt: "2026-04-20T10:00:00Z", updatedAt: "2026-04-20T10:00:00Z",
        },
      ]);
    }
    return HttpResponse.json([]);
  }),

  http.post("/api/actions/:id/approve", async ({ params }) => {
    return HttpResponse.json({
      id: Number(params.id), radarId: 42, kind: "auto_pr_cve",
      payloadJson: JSON.stringify({
        ghsa_id: "GHSA-xxxx-yyyy-zzzz", package: "jackson-databind",
        current_version: "2.16.1", repo: "alice/api",
        file_path: "pom.xml", file_sha: "abc123",
      }),
      status: "EXECUTED",
      prUrl: "https://github.com/alice/api/pull/99",
      failureReason: null,
      createdAt: "2026-04-20T10:00:00Z", updatedAt: "2026-04-20T10:05:00Z",
    });
  }),

  http.delete("/api/actions/:id", ({ params }) => {
    return HttpResponse.json({
      id: Number(params.id), radarId: 42, kind: "auto_pr_cve",
      payloadJson: "{}",
      status: "DISMISSED", prUrl: null, failureReason: null,
      createdAt: "2026-04-20T10:00:00Z", updatedAt: "2026-04-20T10:06:00Z",
    });
  }),
];
