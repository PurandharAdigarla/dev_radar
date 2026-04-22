# Dev Radar — Plan 8: Frontend Core Product Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the three core product screens — Interest picker, Radar list, and Radar detail with live SSE streaming themes + embedded CVE action proposals — turning Plan 7's empty shell into a demoable product.

**Architecture:** Three new pages under `/app`, three RTK Query slices (`interestApi`, `radarApi`, `actionApi`), one tiny Redux slice tracking the currently-streaming radar, and a custom `useRadarStream` hook wrapping native `EventSource`. The Radar Detail page is the portfolio hero — two columns, editorial serif summaries in the read column, compact proposal cards in a right-side panel, themes fading in as server-sent events arrive. A small backend change accepts `?token=` query param on the SSE endpoint so browser `EventSource` can authenticate.

**Tech Stack:** Existing Plan 7 frontend (Vite 5 + React 19 + TypeScript + Redux Toolkit 2 + RTK Query + React Router v7 + MUI v6 themed with monochrome Claude Design tokens) + backend JWT query-param auth for SSE.

**Spec reference:** `docs/superpowers/specs/2026-04-22-frontend-core-product-design.md`.
**Design reference:** Claude Design export preserved at `docs/superpowers/design-assets/2026-04-22-plan8-frontend/Dev-Radar-Product.html` — canonical source of truth for markup and visual choices. The component tasks below derive directly from it.

**Visual decisions locked by the Claude Design export:**

- **Tag chip**: pill-shape (border-radius 999), selected = filled Ink + white text on Ink border, unselected = Ink on white surface with divider border. 14/20px label, 6/14px padding.
- **Category headers**: `DROverline` (11px uppercase, 0.08em tracking, text.secondary). Category labels use human-friendly names: "Languages", "Frameworks", "Databases", "DevOps & cloud", "Security", "Other".
- **Search input**: standard themed input, not hero-sized. Left-aligned with "N selected" and Save button in the same flex row. Save is a filled Ink pill; disabled state is `rgba(45,42,38,0.15)`.
- **Radar row**: editorial list row (no card border by default), 20px vertical padding, hover tint `rgba(45,42,38,0.025)`, 1px divider between rows. **Hierarchy inverted from my earlier draft** — the period ("Week of Apr 13 – Apr 20") is the top line at 16px/500, metadata (themes · tokens · seconds) is a caption row below in text.secondary.
- **Status indicator** (not a chip): plain uppercase text at 11px/500 with 0.06em letter-spacing. `READY` in `text.secondary`; `GENERATING` = pulsing 7px dot + text in `text.primary`; `FAILED` = text in `error.main`. No filled pill — the editorial brand doesn't need it.
- **First-time nudge banner**: surface with `rgba(45,42,38,0.03)` bg and divider border, not a left-accent or red alert. Quiet and invitational.
- **Empty state (no radars)**: dashed divider border (`1px dashed var(--dr-divider)`), 80px vertical padding, serif italic headline "No radars yet." at 20/28, secondary caption, primary CTA.
- **Theme card**: title in sans h2 (24/32, -0.01em, `fontWeight: 500`), summary in Source Serif 4 at 17/28 with `textWrap: pretty`. 48px margin-bottom between themes (no dividers). Below summary: a "SOURCES" overline in text.secondary, then wrap-flow of citation pills.
- **Citation pills**: numbered `[n]` in JetBrains Mono at 11px, 24×20 min size, 4px radius (not full pill), divider border, `rgba(45,42,38,0.04)` bg. **Hover reveals a dark tooltip** showing source title + author (if available), not a native `title=""` attribute. Anchor tag with `target="_blank" rel="noreferrer noopener"`.
- **Radar detail header**: `DROverline` "Week of Apr 13 – Apr 20" above an h1 in sans (32/40, -0.01em). Title falls back to "This week in your stack" if the radar has no explicit title field (it doesn't in our backend — always use the fallback). Below h1 is a metadata caption row: while streaming, `PulseDot + "Generating themes…" + "·" + "{n} of {expected}"`; when done, `{themes} themes · {seconds}s · {tokens}k tokens`.
- **ThemeSkeleton** (new): while streaming, render shimmer placeholders for themes not yet arrived. Based on `expectedThemeCount` which we estimate from the user's interest count (cap 5). Two blocks per skeleton: a 24px-tall width-58% title bar, then four 14px-tall paragraph bars at decreasing widths (100/96/100/78). `dr-shimmer` keyframe: opacity 0.7 ⇄ 1 over 1.4s, staggered 120ms per bar.
- **Proposals panel**: 300px right column (grid `minmax(0, 720px) 300px`, 48px gap, `align-items: flex-start`). Cards are outlined (1px divider, 8px radius, white surface), 16px padding, 12px vertical gap between cards. `DROverline` "Action proposals" above the stack.
- **Proposal card body**: CVE id at top in JetBrains Mono 11px, package name in sans 14/500, then `fromVersion → toVersion` in mono 13 (arrow rendered as inline SVG, not unicode). Below: actions.
- **Approve modal**: 420px centered overlay, 12px radius, white surface, `0 12px 40px rgba(45,42,38,0.15)` shadow. Contains — h3 "Open migration PR" (20/28 -0.01em), caption paragraph, then a proposal-preview row (CVE id + package + fromVersion in a rounded secondary-bg box), then a labeled `toVersion` text field in mono, then right-aligned `Cancel` (ghost) + `Open PR` (primary pill). Loading state: primary button swaps to `PulseDot + "Opening PR…"`. We implement via MUI `Dialog` styled to match.
- **Executed state**: card shows an inline `[✓ icon] PR opened →` link in `success.main`. No success-tinted border — text color carries it.
- **Failure state**: inline tinted error block inside the card (matching Alert primitive) + "Retry" ghost pill button.
- **Dismissed state**: card opacity drops to 0.5, "Dismissed" caption replaces actions.
- **PageHeader component** (new, reusable): title + optional sub + right slot (for action buttons). Used by Interest / Radar list / Radar detail pages for consistent header rhythm.
- **Animations**: 400ms ease-out `dr-fade-in` for new themes (slides up 6px). Pulsing 7–8px dot (`dr-pulse`) for GENERATING indicators. `dr-shimmer` for theme skeletons.

**Kept from my original plan (not in the export, judgment call):**

- **Sidebar items**: Radars + Interests + Settings(soon). The export includes a "Proposals" item — but per spec §4, proposals live inside radar detail, not on a standalone page. Skipping Proposals sidebar item in Plan 8; may restore in a future plan.
- **SSE mechanics + Redux + API slice structure**: unchanged — those are backend/plumbing decisions not visual ones.
- **MUI Dialog for the approve modal**: using Dialog + styled theme rather than bespoke overlay divmodal, so we inherit focus-trap + escape-to-close for free.

---

## File Structure

```
backend/src/main/java/com/devradar/
├── security/
│   ├── JwtAuthenticationFilter.java                    (modify: accept ?token= query param on SSE path)
└── ... (unchanged)

backend/src/test/java/com/devradar/
└── web/rest/RadarSseAuthIT.java                        (new: query-param auth test)

frontend/src/
├── api/
│   ├── types.ts                                         (modify: + Radar, Theme, Item, Proposal, InterestTag DTOs)
│   ├── interestApi.ts                                   (new)
│   ├── interestApi.test.ts                              (new)
│   ├── radarApi.ts                                      (new)
│   ├── radarApi.test.ts                                 (new)
│   ├── actionApi.ts                                     (new)
│   └── actionApi.test.ts                                (new)
├── radar/
│   ├── useRadarStream.ts                                (new)
│   ├── useRadarStream.test.tsx                          (new)
│   ├── radarGenerationSlice.ts                          (new)
│   └── radarGenerationSlice.test.ts                     (new)
├── components/
│   ├── TagChip.tsx                                      (new)
│   ├── TagChip.test.tsx                                 (new)
│   ├── StatusChip.tsx                                   (new)
│   ├── RadarRow.tsx                                     (new)
│   ├── CitationPill.tsx                                 (new)
│   ├── ThemeCard.tsx                                    (new)
│   ├── ProposalCard.tsx                                 (new)
│   └── ProposalApproveModal.tsx                         (new)
├── pages/
│   ├── InterestPickerPage.tsx                           (new)
│   ├── InterestPickerPage.test.tsx                      (new)
│   ├── RadarListPage.tsx                                (new)
│   ├── RadarListPage.test.tsx                           (new)
│   ├── RadarDetailPage.tsx                              (new)
│   ├── RadarDetailPage.test.tsx                         (new)
│   └── AppShell.tsx                                     (modify: real sidebar links, generating indicator)
├── store/
│   └── index.ts                                         (modify: + three APIs + generation slice)
├── App.tsx                                              (modify: + three new routes + first-time redirect logic)
├── test/
│   ├── mswHandlers.ts                                   (modify: + radar/interest/action mock endpoints)
│   └── eventSourceMock.ts                               (new)
```

**Anti-pattern to avoid:** No business logic in pages — pages wire components + hooks + API slices. API slices are pure transport. Components are presentational. Hooks own effects + subscriptions. TagChip doesn't know about radars; ThemeCard doesn't know about RTK Query.

---

## Task 1: Backend — accept `?token=` query param on SSE endpoint (TDD)

**Files:**
- Modify: `backend/src/main/java/com/devradar/security/JwtAuthenticationFilter.java`
- Create: `backend/src/test/java/com/devradar/web/rest/RadarSseAuthIT.java`

This is the ONLY backend change in Plan 8. Browser `EventSource` can't set `Authorization` headers. We allow a `?token=<jwt>` query param on `/api/radars/*/stream` as an alternative.

- [ ] **Step 1: Read the current filter**

```bash
cat backend/src/main/java/com/devradar/security/JwtAuthenticationFilter.java
```

Expected current shape (from Plan 7 memory): extracts `Authorization: Bearer <jwt>`, parses via `JwtTokenProvider`, sets `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken` carrying `JwtUserDetails` as `details`.

- [ ] **Step 2: Write the failing IT**

Create `backend/src/test/java/com/devradar/web/rest/RadarSseAuthIT.java`:

```java
package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RadarSseAuthIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;

    @Test
    void streamEndpointAcceptsQueryParamToken() throws Exception {
        // Register a user so we have a valid userId + jwt
        var body = json.writeValueAsString(java.util.Map.of(
            "email", "sse-qp@test.com", "password", "Password1!", "displayName", "Sse"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        // Create a radar to stream
        var loginResp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                    "email", "sse-qp@test.com", "password", "Password1!"))))
            .andReturn().getResponse().getContentAsString();
        var tok = json.readTree(loginResp).get("accessToken").asText();

        var createResp = mvc.perform(post("/api/radars").header("Authorization", "Bearer " + tok))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        var radarId = json.readTree(createResp).get("id").asLong();

        // Hit /stream with ?token= query param (no Authorization header)
        mvc.perform(get("/api/radars/" + radarId + "/stream").param("token", tok))
            .andExpect(status().isOk());
    }

    @Test
    void streamEndpointRejectsMissingToken() throws Exception {
        mvc.perform(get("/api/radars/999/stream"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void otherEndpointsDoNotAcceptQueryParamToken() throws Exception {
        // Register + log in to get a real token
        var body = json.writeValueAsString(java.util.Map.of(
            "email", "qp-rest@test.com", "password", "Password1!", "displayName", "Qp"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        var loginResp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                    "email", "qp-rest@test.com", "password", "Password1!"))))
            .andReturn().getResponse().getContentAsString();
        var tok = json.readTree(loginResp).get("accessToken").asText();

        // /api/users/me should NOT accept ?token=
        mvc.perform(get("/api/users/me").param("token", tok))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd backend && mvn test -Dtest=RadarSseAuthIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL — `streamEndpointAcceptsQueryParamToken` fails because current filter only reads the header.

- [ ] **Step 4: Modify JwtAuthenticationFilter to accept query-param token on `/stream` paths**

Replace the body of `backend/src/main/java/com/devradar/security/JwtAuthenticationFilter.java`:

```java
package com.devradar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider provider;

    public JwtAuthenticationFilter(JwtTokenProvider provider) { this.provider = provider; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            JwtUserDetails details = provider.parseAccessToken(token);
            if (details != null) {
                var auth = new UsernamePasswordAuthenticationToken(details.email(), null, List.of());
                auth.setDetails(details);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Tokens are normally in the Authorization header. SSE streams are an exception —
     * browser-native EventSource cannot set headers — so we also accept a ?token= query
     * param, but ONLY for endpoints matching /api/radars/*/stream.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (isSseStreamPath(request.getRequestURI())) {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken;
            }
        }
        return null;
    }

    private boolean isSseStreamPath(String uri) {
        // Matches /api/radars/{anything}/stream but nothing longer.
        if (uri == null || !uri.startsWith("/api/radars/")) return false;
        if (!uri.endsWith("/stream")) return false;
        String middle = uri.substring("/api/radars/".length(), uri.length() - "/stream".length());
        // middle must be a non-empty segment with no further slashes
        return !middle.isEmpty() && middle.indexOf('/') < 0;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
cd backend && mvn test -Dtest=RadarSseAuthIT
```

Expected: PASS (3/3).

- [ ] **Step 6: Run full backend suite to guard against regressions**

```bash
cd backend && mvn test
```

Expected: all tests PASS.

- [ ] **Step 7: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add backend/src/main/java/com/devradar/security/JwtAuthenticationFilter.java \
        backend/src/test/java/com/devradar/web/rest/RadarSseAuthIT.java
git commit -m "feat(auth): accept ?token= query param on /api/radars/:id/stream for browser EventSource"
```

---

## Task 2: Frontend — extend API types

**Files:**
- Modify: `frontend/src/api/types.ts`

Adds the DTOs mirroring backend `RadarSummaryDTO`, `RadarDetailDTO`, `RadarThemeDTO`, `RadarItemDTO`, `InterestTagResponseDTO`, `ActionProposalDTO`. Also defines the SSE event envelopes.

- [ ] **Step 1: Append new types to `frontend/src/api/types.ts`**

Add everything below to the existing file (keep current `User`, `LoginRequest`, etc. intact):

```ts
// ─── Radar ──────────────────────────────────────────────────────────────

export type RadarStatus = "GENERATING" | "READY" | "FAILED";

export interface RadarSummary {
  id: number;
  status: RadarStatus;
  periodStart: string; // ISO
  periodEnd: string;
  generatedAt: string | null;
  generationMs: number | null;
  tokenCount: number | null;
}

export interface RadarItem {
  id: number;
  title: string;
  url: string;
  author: string | null;
}

export interface RadarTheme {
  id: number;
  title: string;
  summary: string;
  displayOrder: number;
  items: RadarItem[];
}

export interface RadarDetail extends RadarSummary {
  themes: RadarTheme[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ─── Interest tag ───────────────────────────────────────────────────────

export type InterestCategory =
  | "language"
  | "framework"
  | "database"
  | "devops"
  | "security"
  | "other";

export interface InterestTag {
  id: number;
  slug: string;
  displayName: string;
  category: InterestCategory | null;
}

// ─── Action proposals ───────────────────────────────────────────────────

export type ActionProposalKind = "CVE_FIX_PR";
export type ActionProposalStatus = "PROPOSED" | "EXECUTED" | "DISMISSED" | "FAILED";

export interface ActionProposal {
  id: number;
  radarId: number;
  kind: ActionProposalKind;
  payloadJson: string;       // raw JSON string; parsed client-side
  status: ActionProposalStatus;
  prUrl: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Shape of `payloadJson` for CVE_FIX_PR kind once parsed. */
export interface CveFixPayload {
  cveId: string;
  packageName: string;
  currentVersion: string;
  fixVersion: string;
  repoOwner: string;
  repoName: string;
}

// ─── SSE events ─────────────────────────────────────────────────────────

export interface RadarStartedEvent { radarId: number }
export interface ThemeCompleteEvent {
  radarId: number;
  themeId: number;
  title: string;
  summary: string;
  itemIds: number[];
  displayOrder: number;
}
export interface RadarCompleteEvent { radarId: number; elapsedMs: number; totalTokens: number }
export interface RadarFailedEvent { radarId: number; errorCode: string; errorMessage: string }
export interface ActionProposedEvent {
  radarId: number;
  proposalId: number;
  kind: string;
  payloadJson: string;
}
```

- [ ] **Step 2: Run typecheck to confirm no regressions**

```bash
cd frontend && npm run typecheck
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/api/types.ts
git commit -m "feat(frontend): add Radar, Theme, Item, Interest, Proposal, and SSE event types"
```

---

## Task 3: Frontend — `interestApi` RTK Query slice (TDD)

**Files:**
- Create: `frontend/src/api/interestApi.ts`
- Create: `frontend/src/api/interestApi.test.ts`
- Modify: `frontend/src/test/mswHandlers.ts` (add interest endpoints)

- [ ] **Step 1: Add MSW handlers for interests**

Append to `frontend/src/test/mswHandlers.ts` (inside the `handlers` array, before the closing `]`):

```ts
  // Interest tag catalog
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

  // Current user's interests
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
```

- [ ] **Step 2: Write the failing test**

Create `frontend/src/api/interestApi.test.ts`:

```ts
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
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd frontend && npm run test -- interestApi
```

Expected: FAIL (module not found).

- [ ] **Step 4: Implement `interestApi`**

Create `frontend/src/api/interestApi.ts`:

```ts
import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { InterestCategory, InterestTag, PageResponse } from "./types";

export interface ListTagsArgs {
  q?: string;
  category?: InterestCategory;
  page?: number;
  size?: number;
}

export const interestApi = createApi({
  reducerPath: "interestApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["MyInterests"],
  endpoints: (b) => ({
    listTags: b.query<PageResponse<InterestTag>, ListTagsArgs>({
      query: ({ q, category, page = 0, size = 200 }) => ({
        url: "/api/interest-tags",
        params: {
          ...(q ? { q } : {}),
          ...(category ? { category } : {}),
          page,
          size,
        },
      }),
    }),
    getMyInterests: b.query<InterestTag[], void>({
      query: () => ({ url: "/api/users/me/interests" }),
      providesTags: ["MyInterests"],
    }),
    setMyInterests: b.mutation<InterestTag[], { tagSlugs: string[] }>({
      query: (body) => ({
        url: "/api/users/me/interests",
        method: "PUT",
        body,
      }),
      invalidatesTags: ["MyInterests"],
    }),
  }),
});

export const {
  useListTagsQuery,
  useGetMyInterestsQuery,
  useSetMyInterestsMutation,
} = interestApi;
```

- [ ] **Step 5: Run test**

```bash
cd frontend && npm run test -- interestApi
```

Expected: PASS (5/5).

- [ ] **Step 6: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/api/interestApi.ts frontend/src/api/interestApi.test.ts frontend/src/test/mswHandlers.ts
git commit -m "feat(frontend): add interestApi RTK Query slice"
```

---

## Task 4: Frontend — `radarApi` RTK Query slice (TDD)

**Files:**
- Create: `frontend/src/api/radarApi.ts`
- Create: `frontend/src/api/radarApi.test.ts`
- Modify: `frontend/src/test/mswHandlers.ts`

- [ ] **Step 1: Add radar handlers to MSW**

Append to `frontend/src/test/mswHandlers.ts` inside the handlers array:

```ts
  // Radar list
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
          items: [{ id: 1001, title: "Spring Boot 3.5 released", url: "https://spring.io/3.5", author: "spring-io" }],
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
```

- [ ] **Step 2: Write the failing test**

Create `frontend/src/api/radarApi.test.ts`:

```ts
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
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd frontend && npm run test -- radarApi
```

Expected: FAIL.

- [ ] **Step 4: Implement `radarApi`**

Create `frontend/src/api/radarApi.ts`:

```ts
import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { PageResponse, RadarDetail, RadarSummary } from "./types";

export interface ListRadarsArgs {
  page?: number;
  size?: number;
}

export const radarApi = createApi({
  reducerPath: "radarApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["Radar", "RadarList"],
  endpoints: (b) => ({
    list: b.query<PageResponse<RadarSummary>, ListRadarsArgs>({
      query: ({ page = 0, size = 20 } = {}) => ({
        url: "/api/radars",
        params: { page, size, sort: "generatedAt,desc" },
      }),
      providesTags: ["RadarList"],
    }),
    get: b.query<RadarDetail, number>({
      query: (id) => ({ url: `/api/radars/${id}` }),
      providesTags: (_r, _e, id) => [{ type: "Radar", id }],
    }),
    create: b.mutation<RadarSummary, void>({
      query: () => ({ url: "/api/radars", method: "POST" }),
      invalidatesTags: ["RadarList"],
    }),
  }),
});

export const {
  useListQuery: useListRadarsQuery,
  useGetQuery: useGetRadarQuery,
  useCreateMutation: useCreateRadarMutation,
} = radarApi;
```

- [ ] **Step 5: Run test**

```bash
cd frontend && npm run test -- radarApi
```

Expected: PASS (4/4).

- [ ] **Step 6: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/api/radarApi.ts frontend/src/api/radarApi.test.ts frontend/src/test/mswHandlers.ts
git commit -m "feat(frontend): add radarApi RTK Query slice (list/get/create)"
```

---

## Task 5: Frontend — `actionApi` RTK Query slice (TDD)

**Files:**
- Create: `frontend/src/api/actionApi.ts`
- Create: `frontend/src/api/actionApi.test.ts`
- Modify: `frontend/src/test/mswHandlers.ts`

- [ ] **Step 1: Add action proposal handlers to MSW**

Append to `frontend/src/test/mswHandlers.ts`:

```ts
  http.get("/api/actions/proposals", ({ request }) => {
    const url = new URL(request.url);
    const radarId = url.searchParams.get("radar_id");
    if (radarId === "42") {
      return HttpResponse.json([
        {
          id: 7, radarId: 42, kind: "CVE_FIX_PR",
          payloadJson: JSON.stringify({
            cveId: "CVE-2024-1234", packageName: "jackson-databind",
            currentVersion: "2.16.1", fixVersion: "2.17.0",
            repoOwner: "alice", repoName: "api",
          }),
          status: "PROPOSED", prUrl: null, failureReason: null,
          createdAt: "2026-04-20T10:00:00Z", updatedAt: "2026-04-20T10:00:00Z",
        },
      ]);
    }
    return HttpResponse.json([]);
  }),

  http.post("/api/actions/:id/approve", async ({ params, request }) => {
    const body = (await request.json()) as { fix_version?: string };
    return HttpResponse.json({
      id: Number(params.id), radarId: 42, kind: "CVE_FIX_PR",
      payloadJson: JSON.stringify({
        cveId: "CVE-2024-1234", packageName: "jackson-databind",
        currentVersion: "2.16.1", fixVersion: body.fix_version ?? "2.17.0",
        repoOwner: "alice", repoName: "api",
      }),
      status: "EXECUTED",
      prUrl: "https://github.com/alice/api/pull/99",
      failureReason: null,
      createdAt: "2026-04-20T10:00:00Z", updatedAt: "2026-04-20T10:05:00Z",
    });
  }),

  http.delete("/api/actions/:id", ({ params }) => {
    return HttpResponse.json({
      id: Number(params.id), radarId: 42, kind: "CVE_FIX_PR",
      payloadJson: "{}",
      status: "DISMISSED", prUrl: null, failureReason: null,
      createdAt: "2026-04-20T10:00:00Z", updatedAt: "2026-04-20T10:06:00Z",
    });
  }),
```

- [ ] **Step 2: Write the failing test**

Create `frontend/src/api/actionApi.test.ts`:

```ts
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
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd frontend && npm run test -- actionApi
```

Expected: FAIL.

- [ ] **Step 4: Implement `actionApi`**

Create `frontend/src/api/actionApi.ts`:

```ts
import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { ActionProposal } from "./types";

export const actionApi = createApi({
  reducerPath: "actionApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["Proposals"],
  endpoints: (b) => ({
    listByRadar: b.query<ActionProposal[], number>({
      query: (radarId) => ({ url: "/api/actions/proposals", params: { radar_id: radarId } }),
      providesTags: (_r, _e, radarId) => [{ type: "Proposals", id: radarId }],
    }),
    approve: b.mutation<ActionProposal, { id: number; fixVersion: string }>({
      query: ({ id, fixVersion }) => ({
        url: `/api/actions/${id}/approve`,
        method: "POST",
        body: { fix_version: fixVersion },
      }),
      invalidatesTags: (result) =>
        result ? [{ type: "Proposals", id: result.radarId }] : [],
    }),
    dismiss: b.mutation<ActionProposal, number>({
      query: (id) => ({ url: `/api/actions/${id}`, method: "DELETE" }),
      invalidatesTags: (result) =>
        result ? [{ type: "Proposals", id: result.radarId }] : [],
    }),
  }),
});

export const {
  useListByRadarQuery: useListProposalsByRadarQuery,
  useApproveMutation: useApproveProposalMutation,
  useDismissMutation: useDismissProposalMutation,
} = actionApi;
```

- [ ] **Step 5: Run test**

```bash
cd frontend && npm run test -- actionApi
```

Expected: PASS (4/4).

- [ ] **Step 6: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/api/actionApi.ts frontend/src/api/actionApi.test.ts frontend/src/test/mswHandlers.ts
git commit -m "feat(frontend): add actionApi RTK Query slice (list/approve/dismiss)"
```

---

## Task 6: Frontend — `radarGenerationSlice` (TDD)

**Files:**
- Create: `frontend/src/radar/radarGenerationSlice.ts`
- Create: `frontend/src/radar/radarGenerationSlice.test.ts`

Tiny Redux slice tracking which radar is currently streaming. Lets the sidebar show a "generating…" indicator when the user navigates away mid-stream.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/radar/radarGenerationSlice.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import {
  radarGenerationReducer,
  generationStarted,
  generationFinished,
} from "./radarGenerationSlice";

const initial = radarGenerationReducer(undefined, { type: "@@INIT" });

describe("radarGenerationSlice", () => {
  it("starts with no generating radar", () => {
    expect(initial.currentGeneratingRadarId).toBeNull();
    expect(initial.startedAt).toBeNull();
  });

  it("tracks a radar when generation starts", () => {
    const s = radarGenerationReducer(
      initial,
      generationStarted({ radarId: 100, startedAt: "2026-04-22T10:00:00Z" }),
    );
    expect(s.currentGeneratingRadarId).toBe(100);
    expect(s.startedAt).toBe("2026-04-22T10:00:00Z");
  });

  it("clears on generationFinished", () => {
    const running = radarGenerationReducer(
      initial,
      generationStarted({ radarId: 100, startedAt: "2026-04-22T10:00:00Z" }),
    );
    const done = radarGenerationReducer(running, generationFinished());
    expect(done.currentGeneratingRadarId).toBeNull();
    expect(done.startedAt).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- radarGenerationSlice
```

Expected: FAIL.

- [ ] **Step 3: Implement slice**

Create `frontend/src/radar/radarGenerationSlice.ts`:

```ts
import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

export interface RadarGenerationState {
  currentGeneratingRadarId: number | null;
  startedAt: string | null;
}

const initialState: RadarGenerationState = {
  currentGeneratingRadarId: null,
  startedAt: null,
};

const slice = createSlice({
  name: "radarGeneration",
  initialState,
  reducers: {
    generationStarted(state, action: PayloadAction<{ radarId: number; startedAt: string }>) {
      state.currentGeneratingRadarId = action.payload.radarId;
      state.startedAt = action.payload.startedAt;
    },
    generationFinished(state) {
      state.currentGeneratingRadarId = null;
      state.startedAt = null;
    },
  },
});

export const { generationStarted, generationFinished } = slice.actions;
export const radarGenerationReducer = slice.reducer;
```

- [ ] **Step 4: Run test**

```bash
cd frontend && npm run test -- radarGenerationSlice
```

Expected: PASS (3/3).

- [ ] **Step 5: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/radar/radarGenerationSlice.ts frontend/src/radar/radarGenerationSlice.test.ts
git commit -m "feat(frontend): add radarGenerationSlice tracking currently-streaming radar"
```

---

## Task 7: Frontend — wire new APIs + slice into the store

**Files:**
- Modify: `frontend/src/store/index.ts`

- [ ] **Step 1: Replace `frontend/src/store/index.ts`**

```ts
import { configureStore } from "@reduxjs/toolkit";
import { authApi } from "../api/authApi";
import { authReducer } from "../auth/authSlice";
import { interestApi } from "../api/interestApi";
import { radarApi } from "../api/radarApi";
import { actionApi } from "../api/actionApi";
import { radarGenerationReducer } from "../radar/radarGenerationSlice";

export function makeStore() {
  return configureStore({
    reducer: {
      auth: authReducer,
      radarGeneration: radarGenerationReducer,
      [authApi.reducerPath]: authApi.reducer,
      [interestApi.reducerPath]: interestApi.reducer,
      [radarApi.reducerPath]: radarApi.reducer,
      [actionApi.reducerPath]: actionApi.reducer,
    },
    middleware: (getDefault) =>
      getDefault()
        .concat(authApi.middleware)
        .concat(interestApi.middleware)
        .concat(radarApi.middleware)
        .concat(actionApi.middleware),
  });
}

export const store = makeStore();

export type AppStore = ReturnType<typeof makeStore>;
export type RootState = ReturnType<AppStore["getState"]>;
export type AppDispatch = AppStore["dispatch"];
```

- [ ] **Step 2: Run full test suite to confirm nothing regresses**

```bash
cd frontend && npm run test
```

Expected: all existing tests still PASS.

- [ ] **Step 3: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/store/index.ts
git commit -m "feat(frontend): register interestApi/radarApi/actionApi and radarGeneration slice in store"
```

---

## Task 8: Frontend — `useRadarStream` SSE hook (TDD with mock EventSource)

**Files:**
- Create: `frontend/src/test/eventSourceMock.ts`
- Create: `frontend/src/radar/useRadarStream.ts`
- Create: `frontend/src/radar/useRadarStream.test.tsx`

- [ ] **Step 1: Create the EventSource mock**

Create `frontend/src/test/eventSourceMock.ts`:

```ts
// Minimal EventSource mock for tests. Exposes .__emit(name, payload) so tests
// can deterministically trigger events.

type Listener = (ev: MessageEvent<string>) => void;

export class MockEventSource {
  static instances: MockEventSource[] = [];

  readonly url: string;
  readyState = 0; // CONNECTING
  onopen: ((ev: Event) => void) | null = null;
  onerror: ((ev: Event) => void) | null = null;
  onmessage: Listener | null = null;
  private listeners = new Map<string, Set<Listener>>();
  closed = false;

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
    queueMicrotask(() => {
      if (!this.closed) {
        this.readyState = 1;
        this.onopen?.(new Event("open"));
      }
    });
  }

  addEventListener(name: string, listener: Listener) {
    let set = this.listeners.get(name);
    if (!set) { set = new Set(); this.listeners.set(name, set); }
    set.add(listener);
  }

  removeEventListener(name: string, listener: Listener) {
    this.listeners.get(name)?.delete(listener);
  }

  close() {
    this.closed = true;
    this.readyState = 2;
  }

  /** Test helper: synthesize a server event. */
  __emit(name: string, data: unknown) {
    const ev = new MessageEvent(name, { data: JSON.stringify(data) });
    this.listeners.get(name)?.forEach((l) => l(ev));
    if (name === "message") this.onmessage?.(ev);
  }

  /** Test helper: synthesize an error event. */
  __error() {
    this.onerror?.(new Event("error"));
  }

  static reset() {
    MockEventSource.instances = [];
  }

  static last(): MockEventSource | undefined {
    return MockEventSource.instances[MockEventSource.instances.length - 1];
  }
}

export function installMockEventSource() {
  MockEventSource.reset();
  (globalThis as unknown as { EventSource: typeof MockEventSource }).EventSource =
    MockEventSource;
}
```

- [ ] **Step 2: Write the failing test**

Create `frontend/src/radar/useRadarStream.test.tsx`:

```tsx
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { useRadarStream } from "./useRadarStream";
import { installMockEventSource, MockEventSource } from "../test/eventSourceMock";
import { tokenStorage } from "../auth/tokenStorage";

describe("useRadarStream", () => {
  beforeEach(() => {
    installMockEventSource();
    tokenStorage.clear();
    tokenStorage.setAccess("jwt-for-stream");
  });

  afterEach(() => {
    MockEventSource.reset();
  });

  it("does not open when disabled", () => {
    renderHook(() => useRadarStream(42, false));
    expect(MockEventSource.instances).toHaveLength(0);
  });

  it("opens with ?token= query param and reports open status", async () => {
    const { result } = renderHook(() => useRadarStream(42, true));
    await waitFor(() => expect(MockEventSource.last()?.url).toContain("?token=jwt-for-stream"));
    await waitFor(() => expect(result.current.status).toBe("open"));
  });

  it("appends themes on theme.complete events", async () => {
    const { result } = renderHook(() => useRadarStream(42, true));
    await waitFor(() => expect(MockEventSource.last()?.readyState).toBe(1));

    act(() => {
      MockEventSource.last()!.__emit("theme.complete", {
        radarId: 42, themeId: 1, title: "Theme A", summary: "Summary A",
        itemIds: [1, 2], displayOrder: 0,
      });
    });
    await waitFor(() => expect(result.current.themes).toHaveLength(1));
    expect(result.current.themes[0].title).toBe("Theme A");

    act(() => {
      MockEventSource.last()!.__emit("theme.complete", {
        radarId: 42, themeId: 2, title: "Theme B", summary: "Summary B",
        itemIds: [3], displayOrder: 1,
      });
    });
    await waitFor(() => expect(result.current.themes).toHaveLength(2));
  });

  it("appends proposals on action.proposed events", async () => {
    const { result } = renderHook(() => useRadarStream(42, true));
    await waitFor(() => expect(MockEventSource.last()?.readyState).toBe(1));

    act(() => {
      MockEventSource.last()!.__emit("action.proposed", {
        radarId: 42, proposalId: 7, kind: "CVE_FIX_PR", payloadJson: "{}",
      });
    });
    await waitFor(() => expect(result.current.proposalIds).toEqual([7]));
  });

  it("flips status to complete and captures elapsedMs on radar.complete", async () => {
    const { result } = renderHook(() => useRadarStream(42, true));
    await waitFor(() => expect(MockEventSource.last()?.readyState).toBe(1));
    act(() => {
      MockEventSource.last()!.__emit("radar.complete", {
        radarId: 42, elapsedMs: 12500, totalTokens: 4200,
      });
    });
    await waitFor(() => expect(result.current.status).toBe("complete"));
    expect(result.current.completionMs).toBe(12500);
    expect(MockEventSource.last()!.closed).toBe(true);
  });

  it("flips status to failed and captures error on radar.failed", async () => {
    const { result } = renderHook(() => useRadarStream(42, true));
    await waitFor(() => expect(MockEventSource.last()?.readyState).toBe(1));
    act(() => {
      MockEventSource.last()!.__emit("radar.failed", {
        radarId: 42, errorCode: "GENERATION_FAILED", errorMessage: "Timed out",
      });
    });
    await waitFor(() => expect(result.current.status).toBe("failed"));
    expect(result.current.error).toBe("Timed out");
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd frontend && npm run test -- useRadarStream
```

Expected: FAIL.

- [ ] **Step 4: Implement the hook**

Create `frontend/src/radar/useRadarStream.ts`:

```ts
import { useEffect, useRef, useState } from "react";
import { tokenStorage } from "../auth/tokenStorage";
import type { ThemeCompleteEvent, RadarCompleteEvent, RadarFailedEvent, ActionProposedEvent } from "../api/types";

export type StreamStatus = "idle" | "open" | "complete" | "failed";

export interface StreamedTheme {
  themeId: number;
  title: string;
  summary: string;
  itemIds: number[];
  displayOrder: number;
}

export interface UseRadarStreamResult {
  status: StreamStatus;
  themes: StreamedTheme[];
  proposalIds: number[];
  completionMs: number | null;
  error: string | null;
}

export function useRadarStream(radarId: number, enabled: boolean): UseRadarStreamResult {
  const [status, setStatus] = useState<StreamStatus>("idle");
  const [themes, setThemes] = useState<StreamedTheme[]>([]);
  const [proposalIds, setProposalIds] = useState<number[]>([]);
  const [completionMs, setCompletionMs] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!enabled) return;

    const token = tokenStorage.getAccess() ?? "";
    const es = new EventSource(
      `/api/radars/${radarId}/stream?token=${encodeURIComponent(token)}`,
    );
    esRef.current = es;

    const handleOpen = () => setStatus("open");
    const handleTheme = (ev: MessageEvent<string>) => {
      const payload = JSON.parse(ev.data) as ThemeCompleteEvent;
      setThemes((prev) => [
        ...prev,
        {
          themeId: payload.themeId,
          title: payload.title,
          summary: payload.summary,
          itemIds: payload.itemIds,
          displayOrder: payload.displayOrder,
        },
      ]);
    };
    const handleProposal = (ev: MessageEvent<string>) => {
      const payload = JSON.parse(ev.data) as ActionProposedEvent;
      setProposalIds((prev) => (prev.includes(payload.proposalId) ? prev : [...prev, payload.proposalId]));
    };
    const handleComplete = (ev: MessageEvent<string>) => {
      const payload = JSON.parse(ev.data) as RadarCompleteEvent;
      setCompletionMs(payload.elapsedMs);
      setStatus("complete");
      es.close();
    };
    const handleFailed = (ev: MessageEvent<string>) => {
      const payload = JSON.parse(ev.data) as RadarFailedEvent;
      setError(payload.errorMessage);
      setStatus("failed");
      es.close();
    };
    const handleError = () => {
      // Network/server error. We don't auto-retry — keep it simple.
      if (es.readyState === EventSource.CLOSED) {
        setStatus((cur) => (cur === "complete" || cur === "failed" ? cur : "failed"));
        setError((cur) => cur ?? "Connection lost");
      }
    };

    es.addEventListener("open", handleOpen as EventListener);
    es.addEventListener("theme.complete", handleTheme as EventListener);
    es.addEventListener("action.proposed", handleProposal as EventListener);
    es.addEventListener("radar.complete", handleComplete as EventListener);
    es.addEventListener("radar.failed", handleFailed as EventListener);
    es.onerror = handleError;

    return () => {
      es.close();
      esRef.current = null;
    };
  }, [radarId, enabled]);

  return { status, themes, proposalIds, completionMs, error };
}
```

- [ ] **Step 5: Run test**

```bash
cd frontend && npm run test -- useRadarStream
```

Expected: PASS (6/6).

- [ ] **Step 6: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/radar/useRadarStream.ts frontend/src/radar/useRadarStream.test.tsx frontend/src/test/eventSourceMock.ts
git commit -m "feat(frontend): add useRadarStream hook wrapping browser EventSource"
```

---

## Task 9: Frontend — `TagChip` component (TDD)

**Files:**
- Create: `frontend/src/components/TagChip.tsx`
- Create: `frontend/src/components/TagChip.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/components/TagChip.test.tsx`:

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { theme } from "../theme";
import { TagChip } from "./TagChip";

function withTheme(ui: React.ReactNode) {
  return <ThemeProvider theme={theme}>{ui}</ThemeProvider>;
}

describe("TagChip", () => {
  it("renders label", () => {
    render(withTheme(<TagChip label="Java" selected={false} onToggle={() => {}} />));
    expect(screen.getByRole("button", { name: /java/i })).toBeInTheDocument();
  });

  it("fires onToggle on click", async () => {
    const user = userEvent.setup();
    const onToggle = vi.fn();
    render(withTheme(<TagChip label="Java" selected={false} onToggle={onToggle} />));
    await user.click(screen.getByRole("button", { name: /java/i }));
    expect(onToggle).toHaveBeenCalledOnce();
  });

  it("exposes aria-pressed based on selected", () => {
    const { rerender } = render(
      withTheme(<TagChip label="Java" selected={false} onToggle={() => {}} />),
    );
    expect(screen.getByRole("button")).toHaveAttribute("aria-pressed", "false");
    rerender(withTheme(<TagChip label="Java" selected={true} onToggle={() => {}} />));
    expect(screen.getByRole("button")).toHaveAttribute("aria-pressed", "true");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- TagChip
```

Expected: FAIL.

- [ ] **Step 3: Implement**

Matches the export's `TagChip` with the "filled" chipStyle (default). 14/20px label, 6/14px padding, white surface when unselected, Ink fill when selected.

Create `frontend/src/components/TagChip.tsx`:

```tsx
import Box from "@mui/material/Box";

export interface TagChipProps {
  label: string;
  selected: boolean;
  onToggle: () => void;
}

export function TagChip({ label, selected, onToggle }: TagChipProps) {
  return (
    <Box
      component="button"
      type="button"
      role="button"
      aria-pressed={selected}
      onClick={onToggle}
      sx={{
        display: "inline-flex",
        alignItems: "center",
        gap: "6px",
        fontFamily: "inherit",
        fontSize: 14,
        lineHeight: "20px",
        fontWeight: 500,
        padding: "6px 14px",
        borderRadius: 999,
        cursor: "pointer",
        userSelect: "none",
        border: "1px solid",
        borderColor: selected ? "text.primary" : "divider",
        bgcolor: selected ? "text.primary" : "background.paper",
        color: selected ? "#ffffff" : "text.primary",
        transition: "background 120ms, border-color 120ms, color 120ms",
        "&:hover": {
          bgcolor: selected ? "#000000" : "rgba(45,42,38,0.04)",
          borderColor: selected ? "text.primary" : "divider",
        },
        "&:focus-visible": {
          outline: "none",
          boxShadow: (t) => `0 0 0 2px ${t.palette.text.primary}`,
        },
      }}
    >
      {label}
    </Box>
  );
}
```

- [ ] **Step 4: Run test**

```bash
cd frontend && npm run test -- TagChip
```

Expected: PASS (3/3).

- [ ] **Step 5: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/components/TagChip.tsx frontend/src/components/TagChip.test.tsx
git commit -m "feat(frontend): add TagChip toggleable pill (filled when selected, outlined when not)"
```

---

## Task 10: Frontend — `InterestPickerPage` (TDD)

**Files:**
- Create: `frontend/src/pages/InterestPickerPage.tsx`
- Create: `frontend/src/pages/InterestPickerPage.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/pages/InterestPickerPage.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { InterestPickerPage } from "./InterestPickerPage";
import { tokenStorage } from "../auth/tokenStorage";

function setup() {
  tokenStorage.setAccess("valid-token");
  render(
    <Provider store={makeStore()}>
      <ThemeProvider theme={theme}>
        <MemoryRouter>
          <InterestPickerPage />
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
  return { user: userEvent.setup() };
}

describe("InterestPickerPage", () => {
  it("renders heading and search input", async () => {
    setup();
    expect(screen.getByRole("heading", { name: /interests/i, level: 1 })).toBeInTheDocument();
    expect(screen.getByLabelText(/search/i)).toBeInTheDocument();
  });

  it("loads tags and marks currently selected ones", async () => {
    setup();
    // MSW returns 5 tags; "Java" is currently selected per the /me/interests handler
    await waitFor(() =>
      expect(screen.getByRole("button", { name: /^java$/i })).toHaveAttribute("aria-pressed", "true"),
    );
    expect(screen.getByRole("button", { name: /spring boot/i })).toHaveAttribute("aria-pressed", "false");
  });

  it("Save button enables after toggling a tag", async () => {
    const { user } = setup();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: /^java$/i })).toHaveAttribute("aria-pressed", "true"),
    );
    const save = screen.getByRole("button", { name: /save/i });
    expect(save).toBeDisabled();

    await user.click(screen.getByRole("button", { name: /spring boot/i }));
    expect(save).toBeEnabled();
  });

  it("shows selected count", async () => {
    setup();
    await waitFor(() =>
      expect(screen.getByText(/1 selected/i)).toBeInTheDocument(),
    );
  });

  it("filters tags by search query", async () => {
    const { user } = setup();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: /^java$/i })).toBeInTheDocument(),
    );
    await user.type(screen.getByLabelText(/search/i), "spring");
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /^java$/i })).not.toBeInTheDocument(),
    );
    expect(screen.getByRole("button", { name: /spring boot/i })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- InterestPickerPage
```

Expected: FAIL.

- [ ] **Step 3: Implement**

Per the export: use `PageHeader`, a compact search input in the same row as `{n} selected` counter and Save button, and a "No tags match" empty state.

Create `frontend/src/pages/InterestPickerPage.tsx`:

```tsx
import { useEffect, useMemo, useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "../components/Button";
import { TextField } from "../components/TextField";
import { Alert } from "../components/Alert";
import { PageHeader } from "../components/PageHeader";
import { TagChip } from "../components/TagChip";
import {
  useGetMyInterestsQuery,
  useListTagsQuery,
  useSetMyInterestsMutation,
} from "../api/interestApi";
import type { InterestCategory, InterestTag } from "../api/types";

const CATEGORY_ORDER: InterestCategory[] = [
  "language",
  "framework",
  "database",
  "devops",
  "security",
  "other",
];

const CATEGORY_LABEL: Record<InterestCategory, string> = {
  language: "Languages",
  framework: "Frameworks",
  database: "Databases",
  devops: "DevOps & cloud",
  security: "Security",
  other: "Other",
};

export function InterestPickerPage() {
  const { data: tagsPage, isLoading: tagsLoading } = useListTagsQuery({});
  const { data: myInterests } = useGetMyInterestsQuery();
  const [setMyInterests, saveState] = useSetMyInterestsMutation();

  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [hydrated, setHydrated] = useState(false);

  // Hydrate local state once when server state arrives
  useEffect(() => {
    if (myInterests && !hydrated) {
      setSelected(new Set(myInterests.map((t) => t.slug)));
      setHydrated(true);
    }
  }, [myInterests, hydrated]);

  const tags = tagsPage?.content ?? [];
  const filteredTags = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return tags;
    return tags.filter(
      (t) => t.displayName.toLowerCase().includes(q) || t.slug.includes(q),
    );
  }, [tags, query]);

  const byCategory = useMemo(() => {
    const map = new Map<string, InterestTag[]>();
    for (const t of filteredTags) {
      const key = t.category ?? "other";
      const arr = map.get(key) ?? [];
      arr.push(t);
      map.set(key, arr);
    }
    return map;
  }, [filteredTags]);

  const serverSlugs = useMemo(
    () => new Set((myInterests ?? []).map((t) => t.slug)),
    [myInterests],
  );
  const dirty = useMemo(() => {
    if (selected.size !== serverSlugs.size) return true;
    for (const s of selected) if (!serverSlugs.has(s)) return true;
    return false;
  }, [selected, serverSlugs]);

  function toggle(slug: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(slug)) next.delete(slug);
      else next.add(slug);
      return next;
    });
  }

  async function onSave() {
    await setMyInterests({ tagSlugs: Array.from(selected) }).unwrap().catch(() => {
      /* error shown via saveState.isError */
    });
  }

  return (
    <Box sx={{ maxWidth: 960, width: "100%" }}>
      <PageHeader title="Interests" sub="Pick topics your weekly radar should cover." />

      <Box
        sx={{
          display: "flex",
          gap: 2,
          alignItems: "center",
          mb: 5,
          flexWrap: "wrap",
        }}
      >
        <Box sx={{ flex: 1, minWidth: 280, maxWidth: 420 }}>
          <TextField
            label="Search"
            placeholder="Search tags…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </Box>
        <Typography
          sx={{
            fontSize: 14,
            color: "text.secondary",
            fontVariantNumeric: "tabular-nums",
          }}
        >
          <Box component="strong" sx={{ color: "text.primary", fontWeight: 500 }}>
            {selected.size}
          </Box>{" "}
          selected
        </Typography>
        <Box sx={{ flex: 1 }} />
        <Button onClick={onSave} disabled={!dirty || saveState.isLoading}>
          {saveState.isLoading ? "Saving…" : "Save"}
        </Button>
      </Box>

      {saveState.isError && (
        <Box sx={{ mb: 4 }}>
          <Alert severity="error">Couldn't save your interests. Try again.</Alert>
        </Box>
      )}

      {tagsLoading && (
        <Typography variant="body2" color="text.secondary">Loading tags…</Typography>
      )}

      {!tagsLoading && filteredTags.length === 0 && (
        <Box sx={{ py: 5, textAlign: "center", color: "text.secondary", fontSize: 14 }}>
          No tags match “{query}”.
        </Box>
      )}

      {CATEGORY_ORDER.map((cat) => {
        const catTags = byCategory.get(cat);
        if (!catTags || catTags.length === 0) return null;
        return (
          <Box key={cat} sx={{ mb: 5 }}>
            <Typography
              variant="overline"
              color="text.secondary"
              sx={{ display: "block", mb: 2 }}
            >
              {CATEGORY_LABEL[cat]}
            </Typography>
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
              {catTags.map((t) => (
                <TagChip
                  key={t.slug}
                  label={t.displayName}
                  selected={selected.has(t.slug)}
                  onToggle={() => toggle(t.slug)}
                />
              ))}
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}
```

- [ ] **Step 4: Run test**

```bash
cd frontend && npm run test -- InterestPickerPage
```

Expected: PASS (5/5).

- [ ] **Step 5: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/pages/InterestPickerPage.tsx frontend/src/pages/InterestPickerPage.test.tsx
git commit -m "feat(frontend): add InterestPickerPage with search, categories, and save"
```

---

## Task 11: Frontend — `StatusTag`, `PulseDot`, `PageHeader` + `RadarRow` (TDD)

**Files:**
- Create: `frontend/src/components/PulseDot.tsx`
- Create: `frontend/src/components/StatusTag.tsx`
- Create: `frontend/src/components/PageHeader.tsx`
- Create: `frontend/src/components/RadarRow.tsx`
- Create: `frontend/src/components/RadarRow.test.tsx`

Per the Claude Design export: `StatusTag` is plain uppercase text (not a filled pill). `PulseDot` is the shared animated 7–8px dot used on GENERATING status, radar-detail header, and sidebar generating indicator. `PageHeader` is a reusable title+sub+right-slot composed by all three pages.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/components/RadarRow.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { theme } from "../theme";
import { RadarRow } from "./RadarRow";
import type { RadarSummary } from "../api/types";

function withWrappers(ui: React.ReactNode) {
  return (
    <ThemeProvider theme={theme}>
      <MemoryRouter>{ui}</MemoryRouter>
    </ThemeProvider>
  );
}

const ready: RadarSummary = {
  id: 42, status: "READY",
  periodStart: "2026-04-13T00:00:00Z",
  periodEnd: "2026-04-20T00:00:00Z",
  generatedAt: "2026-04-20T10:00:00Z",
  generationMs: 12000, tokenCount: 4200,
};

describe("RadarRow", () => {
  it("renders the period as the primary line", () => {
    render(withWrappers(<RadarRow radar={ready} />));
    expect(screen.getByText(/week of/i)).toBeInTheDocument();
  });

  it("renders metadata caption with tokens and seconds", () => {
    render(withWrappers(<RadarRow radar={ready} />));
    expect(screen.getByText(/4\.2k tokens/i)).toBeInTheDocument();
    expect(screen.getByText(/12\.0s/)).toBeInTheDocument();
  });

  it("renders 'Ready' as plain uppercase text", () => {
    render(withWrappers(<RadarRow radar={ready} />));
    expect(screen.getByText(/^ready$/i)).toBeInTheDocument();
  });

  it("links to /app/radars/:id", () => {
    render(withWrappers(<RadarRow radar={ready} />));
    expect(screen.getByRole("link")).toHaveAttribute("href", "/app/radars/42");
  });

  it("shows 'Generating' with a pulsing dot", () => {
    render(withWrappers(<RadarRow radar={{ ...ready, status: "GENERATING", generatedAt: null, generationMs: null }} />));
    expect(screen.getByText(/generating/i)).toBeInTheDocument();
    expect(screen.getByText(/streaming themes/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- RadarRow
```

Expected: FAIL.

- [ ] **Step 3: Create `PulseDot`**

Create `frontend/src/components/PulseDot.tsx`:

```tsx
import Box from "@mui/material/Box";
import { keyframes } from "@mui/system";

const pulse = keyframes`
  0%, 100% { opacity: 1; transform: scale(1); }
  50%      { opacity: 0.3; transform: scale(0.85); }
`;

export interface PulseDotProps {
  size?: number;
  color?: string;
}

export function PulseDot({ size = 7, color = "text.primary" }: PulseDotProps) {
  return (
    <Box
      component="span"
      sx={{
        display: "inline-block",
        width: size,
        height: size,
        borderRadius: 999,
        bgcolor: color,
        animation: `${pulse} 1.2s ease-in-out infinite`,
      }}
    />
  );
}
```

- [ ] **Step 4: Create `StatusTag`**

Create `frontend/src/components/StatusTag.tsx`:

```tsx
import Box from "@mui/material/Box";
import { PulseDot } from "./PulseDot";
import type { RadarStatus } from "../api/types";

const STYLES: Record<RadarStatus, { color: string; label: string }> = {
  READY:      { color: "text.secondary", label: "Ready" },
  GENERATING: { color: "text.primary",   label: "Generating" },
  FAILED:     { color: "error.main",     label: "Failed" },
};

export function StatusTag({ status }: { status: RadarStatus }) {
  const { color, label } = STYLES[status];
  return (
    <Box
      component="span"
      sx={{
        display: "inline-flex",
        alignItems: "center",
        gap: "6px",
        fontSize: 11,
        lineHeight: "16px",
        fontWeight: 500,
        letterSpacing: "0.06em",
        textTransform: "uppercase",
        color,
      }}
    >
      {status === "GENERATING" && <PulseDot />}
      {label}
    </Box>
  );
}
```

- [ ] **Step 5: Create `PageHeader`**

Create `frontend/src/components/PageHeader.tsx`:

```tsx
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import type { ReactNode } from "react";

export interface PageHeaderProps {
  title: string;
  sub?: string;
  right?: ReactNode;
}

export function PageHeader({ title, sub, right }: PageHeaderProps) {
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "flex-end",
        justifyContent: "space-between",
        gap: 3,
        mb: 5,
        flexWrap: "wrap",
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography
          component="h1"
          sx={{
            m: 0,
            fontSize: "2rem",
            lineHeight: "40px",
            fontWeight: 500,
            letterSpacing: "-0.01em",
            color: "text.primary",
          }}
        >
          {title}
        </Typography>
        {sub && (
          <Typography
            variant="body1"
            color="text.secondary"
            sx={{ mt: 1, fontSize: "0.9375rem", lineHeight: "24px" }}
          >
            {sub}
          </Typography>
        )}
      </Box>
      {right}
    </Box>
  );
}
```

- [ ] **Step 6: Create `RadarRow`**

Per the export, the row's top line is the period at 16px/500 and the metadata is a caption row below. For GENERATING status the metadata swaps to "Streaming themes now…". For FAILED it swaps to "Generation failed — retry from the detail view".

Create `frontend/src/components/RadarRow.tsx`:

```tsx
import { Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { StatusTag } from "./StatusTag";
import type { RadarSummary } from "../api/types";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function formatPeriod(startIso: string, endIso: string): string {
  return `Week of ${formatDate(startIso)} – ${formatDate(endIso)}`;
}

export interface RadarRowProps {
  radar: RadarSummary;
}

export function RadarRow({ radar }: RadarRowProps) {
  const tokensK = radar.tokenCount != null ? (radar.tokenCount / 1000).toFixed(1) : null;
  const seconds = radar.generationMs != null ? (radar.generationMs / 1000).toFixed(1) : null;

  return (
    <Box
      component={RouterLink}
      to={`/app/radars/${radar.id}`}
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 3,
        padding: "20px 12px",
        mx: "-12px",
        borderBottom: 1,
        borderColor: "divider",
        textDecoration: "none",
        color: "text.primary",
        transition: "background 120ms",
        "&:hover": { bgcolor: "rgba(45,42,38,0.025)" },
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography
          sx={{
            fontSize: 16,
            lineHeight: "24px",
            fontWeight: 500,
            letterSpacing: "-0.005em",
            color: "text.primary",
            mb: "4px",
          }}
        >
          {formatPeriod(radar.periodStart, radar.periodEnd)}
        </Typography>
        <Box
          sx={{
            display: "flex",
            gap: 1.5,
            flexWrap: "wrap",
            fontSize: 13,
            lineHeight: "20px",
            color: "text.secondary",
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {radar.status === "READY" && tokensK != null && seconds != null && (
            <>
              <span>3 themes</span>
              <span>·</span>
              <span>{tokensK}k tokens</span>
              <span>·</span>
              <span>{seconds}s</span>
            </>
          )}
          {radar.status === "GENERATING" && <span>Streaming themes now…</span>}
          {radar.status === "FAILED" && <span>Generation failed — retry from the detail view</span>}
        </Box>
      </Box>
      <StatusTag status={radar.status} />
    </Box>
  );
}
```

Note: the fixed "3 themes" string is a placeholder — our `RadarSummary` doesn't carry `themeCount` yet. If the backend DTO gains that field in a future task, swap `"3 themes"` for `{radar.themeCount} themes`. For Plan 8 it's acceptable — the detail page shows the real count.

- [ ] **Step 7: Run test**

```bash
cd frontend && npm run test -- RadarRow
```

Expected: PASS (5/5).

- [ ] **Step 8: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/components/PulseDot.tsx \
        frontend/src/components/StatusTag.tsx \
        frontend/src/components/PageHeader.tsx \
        frontend/src/components/RadarRow.tsx \
        frontend/src/components/RadarRow.test.tsx
git commit -m "feat(frontend): add PulseDot, StatusTag (plain uppercase), PageHeader, and RadarRow"
```

---

## Task 12: Frontend — `RadarListPage` (TDD)

**Files:**
- Create: `frontend/src/pages/RadarListPage.tsx`
- Create: `frontend/src/pages/RadarListPage.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/pages/RadarListPage.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { RadarListPage } from "./RadarListPage";
import { tokenStorage } from "../auth/tokenStorage";

function setup() {
  tokenStorage.setAccess("valid-token");
  render(
    <Provider store={makeStore()}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={["/app/radars"]}>
          <Routes>
            <Route path="/app/radars" element={<RadarListPage />} />
            <Route path="/app/radars/:id" element={<div>detail-page</div>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
  return { user: userEvent.setup() };
}

describe("RadarListPage", () => {
  it("renders heading and list of radars", async () => {
    setup();
    expect(screen.getByRole("heading", { name: /radars/i, level: 1 })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(/week of/i)).toBeInTheDocument());
  });

  it("enables the Generate button once interests load", async () => {
    setup();
    const btn = await screen.findByRole("button", { name: /generate new radar/i });
    await waitFor(() => expect(btn).toBeEnabled());
  });

  it("navigates to detail page on Generate", async () => {
    const { user } = setup();
    const btn = await screen.findByRole("button", { name: /generate new radar/i });
    await waitFor(() => expect(btn).toBeEnabled());
    await user.click(btn);
    await waitFor(() => expect(screen.getByText("detail-page")).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- RadarListPage
```

Expected: FAIL.

- [ ] **Step 3: Implement**

Per the export: first-time banner uses a calm `rgba(45,42,38,0.03)` bg + divider border (not a left-accent). Empty state is a dashed-border centered block with a serif italic "No radars yet." headline at 20/28.

Create `frontend/src/pages/RadarListPage.tsx`:

```tsx
import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "../components/Button";
import { Alert } from "../components/Alert";
import { PageHeader } from "../components/PageHeader";
import { RadarRow } from "../components/RadarRow";
import { serifStack } from "../theme";
import { useCreateRadarMutation, useListRadarsQuery } from "../api/radarApi";
import { useGetMyInterestsQuery } from "../api/interestApi";

export function RadarListPage() {
  const navigate = useNavigate();
  const { data: page, isLoading } = useListRadarsQuery({ page: 0, size: 20 });
  const { data: interests } = useGetMyInterestsQuery();
  const [createRadar, createState] = useCreateRadarMutation();

  const hasInterests = (interests?.length ?? 0) > 0;
  const radars = page?.content ?? [];
  const hasRadars = radars.length > 0;

  async function onGenerate() {
    if (!hasInterests) return;
    const created = await createRadar().unwrap().catch(() => null);
    if (created) navigate(`/app/radars/${created.id}`);
  }

  return (
    <Box sx={{ maxWidth: 960, width: "100%" }}>
      <PageHeader
        title="Radars"
        sub="Your weekly briefs."
        right={
          <Button
            onClick={onGenerate}
            disabled={!hasInterests || createState.isLoading}
            title={hasInterests ? undefined : "Pick at least one interest first"}
          >
            {createState.isLoading ? "Starting…" : "Generate new radar"}
          </Button>
        }
      />

      {createState.isError && (
        <Box sx={{ mb: 4 }}>
          <Alert severity="error">Couldn't start a new radar. Try again.</Alert>
        </Box>
      )}

      {!hasInterests && interests !== undefined && (
        <Box
          sx={{
            mb: 4,
            padding: "16px 20px",
            bgcolor: "rgba(45,42,38,0.03)",
            border: "1px solid",
            borderColor: "divider",
            borderRadius: 1,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 2,
            flexWrap: "wrap",
          }}
        >
          <Typography sx={{ fontSize: 14, lineHeight: "22px", color: "text.primary" }}>
            Pick a few interests to generate your first radar.
          </Typography>
          <Box
            component="button"
            onClick={() => navigate("/app/interests")}
            sx={{
              background: "transparent",
              border: "none",
              padding: 0,
              fontFamily: "inherit",
              fontSize: 14,
              fontWeight: 500,
              color: "text.primary",
              cursor: "pointer",
              textDecoration: "underline",
              textUnderlineOffset: "3px",
            }}
          >
            Pick interests →
          </Box>
        </Box>
      )}

      {isLoading && (
        <Typography variant="body2" color="text.secondary">Loading radars…</Typography>
      )}

      {!isLoading && !hasRadars && hasInterests && (
        <Box
          sx={{
            padding: "80px 24px",
            textAlign: "center",
            border: "1px dashed",
            borderColor: "divider",
            borderRadius: 3,
            bgcolor: "background.paper",
          }}
        >
          <Box
            sx={{
              fontFamily: serifStack,
              fontSize: 20,
              lineHeight: "28px",
              fontStyle: "italic",
              color: "text.primary",
              mb: 1,
            }}
          >
            No radars yet.
          </Box>
          <Typography
            sx={{ fontSize: 14, color: "text.secondary", mb: 3, lineHeight: "22px" }}
          >
            Generate one to see how the weekly brief comes together.
          </Typography>
          <Button onClick={onGenerate} disabled={createState.isLoading}>
            Generate your first radar
          </Button>
        </Box>
      )}

      {hasRadars && (
        <Box sx={{ borderTop: 1, borderColor: "divider" }}>
          {radars.map((r) => (
            <RadarRow key={r.id} radar={r} />
          ))}
        </Box>
      )}
    </Box>
  );
}
```

- [ ] **Step 4: Run test**

```bash
cd frontend && npm run test -- RadarListPage
```

Expected: PASS (3/3).

- [ ] **Step 5: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/pages/RadarListPage.tsx frontend/src/pages/RadarListPage.test.tsx
git commit -m "feat(frontend): add RadarListPage with first-time nudge, empty state, and generate flow"
```

---

## Task 13: Frontend — `CitationPill`, `ThemeCard`, `ThemeSkeleton` components (TDD)

**Files:**
- Create: `frontend/src/components/CitationPill.tsx`
- Create: `frontend/src/components/ThemeCard.tsx`
- Create: `frontend/src/components/ThemeSkeleton.tsx`
- Create: `frontend/src/components/ThemeCard.test.tsx`

Per the Claude Design export: citation pills are mono `[n]` at 11px with a hover tooltip (not a native title attribute); ThemeCard has a "SOURCES" overline above the pills; ThemeSkeleton renders shimmer placeholders for themes that haven't streamed in yet.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/components/ThemeCard.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { theme } from "../theme";
import { ThemeCard } from "./ThemeCard";
import { ThemeSkeleton } from "./ThemeSkeleton";
import type { RadarTheme } from "../api/types";

function withTheme(ui: React.ReactNode) {
  return <ThemeProvider theme={theme}>{ui}</ThemeProvider>;
}

const sample: RadarTheme = {
  id: 1,
  title: "Spring Boot ecosystem updates",
  summary: "Spring Boot 3.5 ships with virtual thread support.",
  displayOrder: 0,
  items: [
    { id: 1001, title: "Spring Boot 3.5 released", url: "https://spring.io/3.5", author: "spring-io" },
    { id: 1002, title: "Virtual threads deep-dive", url: "https://example.com/vt", author: null },
  ],
};

describe("ThemeCard", () => {
  it("renders title, summary, a SOURCES overline, and numbered citation pills", () => {
    render(withTheme(<ThemeCard theme={sample} />));
    expect(screen.getByRole("heading", { name: /spring boot ecosystem/i })).toBeInTheDocument();
    expect(screen.getByText(/virtual thread support/i)).toBeInTheDocument();
    expect(screen.getByText(/^sources$/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /\[1\]/i })).toHaveAttribute("href", "https://spring.io/3.5");
    expect(screen.getByRole("link", { name: /\[2\]/i })).toHaveAttribute("href", "https://example.com/vt");
  });

  it("omits the SOURCES overline when there are no items", () => {
    render(withTheme(<ThemeCard theme={{ ...sample, items: [] }} />));
    expect(screen.queryByText(/^sources$/i)).not.toBeInTheDocument();
  });

  it("reveals the source title on citation hover", async () => {
    const user = userEvent.setup();
    render(withTheme(<ThemeCard theme={sample} />));
    const pill = screen.getByRole("link", { name: /\[1\]/i });
    await user.hover(pill);
    expect(await screen.findByText(/spring boot 3\.5 released/i)).toBeInTheDocument();
  });
});

describe("ThemeSkeleton", () => {
  it("renders a placeholder with role=status", () => {
    render(withTheme(<ThemeSkeleton />));
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- ThemeCard
```

Expected: FAIL.

- [ ] **Step 3: Create `CitationPill`**

Create `frontend/src/components/CitationPill.tsx`:

```tsx
import { useState } from "react";
import Box from "@mui/material/Box";
import { monoStack } from "../theme";

export interface CitationPillSource {
  title: string;
  url: string;
  author?: string | null;
}

export interface CitationPillProps {
  index: number; // 1-based
  source: CitationPillSource;
}

export function CitationPill({ index, source }: CitationPillProps) {
  const [hover, setHover] = useState(false);
  return (
    <Box component="span" sx={{ position: "relative", display: "inline-block" }}>
      <Box
        component="a"
        href={source.url}
        target="_blank"
        rel="noreferrer noopener"
        onMouseEnter={() => setHover(true)}
        onMouseLeave={() => setHover(false)}
        sx={{
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          minWidth: 24,
          height: 20,
          padding: "0 6px",
          borderRadius: "4px",
          fontFamily: monoStack,
          fontSize: 11,
          lineHeight: 1,
          color: "text.primary",
          bgcolor: hover ? "rgba(45,42,38,0.08)" : "rgba(45,42,38,0.04)",
          border: "1px solid",
          borderColor: "divider",
          textDecoration: "none",
          verticalAlign: "baseline",
          cursor: "pointer",
          transition: "background 120ms",
        }}
      >
        [{index}]
      </Box>
      {hover && (
        <Box
          component="span"
          role="tooltip"
          sx={{
            position: "absolute",
            bottom: "100%",
            left: 0,
            mb: "6px",
            padding: "8px 12px",
            bgcolor: "text.primary",
            color: "#fff",
            fontSize: 12,
            lineHeight: "16px",
            borderRadius: "6px",
            whiteSpace: "nowrap",
            maxWidth: 280,
            overflow: "hidden",
            textOverflow: "ellipsis",
            zIndex: 10,
            pointerEvents: "none",
            boxShadow: "0 2px 8px rgba(0,0,0,0.15)",
          }}
        >
          {source.title}
          {source.author && (
            <Box component="span" sx={{ opacity: 0.6, ml: "6px" }}>· {source.author}</Box>
          )}
        </Box>
      )}
    </Box>
  );
}
```

- [ ] **Step 4: Create `ThemeSkeleton`**

Create `frontend/src/components/ThemeSkeleton.tsx`:

```tsx
import Box from "@mui/material/Box";
import { keyframes } from "@mui/system";

const shimmer = keyframes`
  0%, 100% { opacity: 0.7; }
  50%      { opacity: 1; }
`;

const LINE_WIDTHS = ["100%", "96%", "100%", "78%"];

export function ThemeSkeleton() {
  return (
    <Box component="article" role="status" aria-label="Loading theme" sx={{ mb: 6, opacity: 0.9 }}>
      <Box
        sx={{
          height: 24,
          width: "58%",
          mb: "20px",
          bgcolor: "rgba(45,42,38,0.07)",
          borderRadius: "4px",
          animation: `${shimmer} 1.4s ease-in-out infinite`,
        }}
      />
      {LINE_WIDTHS.map((w, i) => (
        <Box
          key={i}
          sx={{
            height: 14,
            width: w,
            mb: "10px",
            bgcolor: "rgba(45,42,38,0.05)",
            borderRadius: "4px",
            animation: `${shimmer} 1.4s ease-in-out infinite`,
            animationDelay: `${i * 120}ms`,
          }}
        />
      ))}
    </Box>
  );
}
```

- [ ] **Step 5: Create `ThemeCard`**

Create `frontend/src/components/ThemeCard.tsx`:

```tsx
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { keyframes } from "@mui/system";
import { serifStack } from "../theme";
import { CitationPill } from "./CitationPill";
import type { RadarTheme } from "../api/types";

const fadeIn = keyframes`
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
`;

export interface ThemeCardProps {
  theme: RadarTheme;
}

export function ThemeCard({ theme }: ThemeCardProps) {
  return (
    <Box component="article" sx={{ mb: 6, animation: `${fadeIn} 400ms ease-out` }}>
      <Typography
        component="h2"
        sx={{
          m: 0,
          mb: 2,
          fontSize: "1.5rem",
          lineHeight: "32px",
          fontWeight: 500,
          letterSpacing: "-0.01em",
          color: "text.primary",
        }}
      >
        {theme.title}
      </Typography>

      <Box
        sx={{
          fontFamily: serifStack,
          fontSize: "1.0625rem",  // 17px
          lineHeight: "28px",
          color: "text.primary",
          whiteSpace: "pre-line",
          textWrap: "pretty",
        }}
      >
        {theme.summary}
      </Box>

      {theme.items.length > 0 && (
        <Box sx={{ mt: "20px", display: "flex", gap: "6px", flexWrap: "wrap", alignItems: "center" }}>
          <Typography
            variant="overline"
            color="text.secondary"
            sx={{ mr: "6px" }}
          >
            Sources
          </Typography>
          {theme.items.map((item, idx) => (
            <CitationPill
              key={item.id}
              index={idx + 1}
              source={{ title: item.title, url: item.url, author: item.author }}
            />
          ))}
        </Box>
      )}
    </Box>
  );
}
```

- [ ] **Step 6: Run test**

```bash
cd frontend && npm run test -- ThemeCard
```

Expected: PASS (4/4).

- [ ] **Step 7: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/components/CitationPill.tsx \
        frontend/src/components/ThemeCard.tsx \
        frontend/src/components/ThemeSkeleton.tsx \
        frontend/src/components/ThemeCard.test.tsx
git commit -m "feat(frontend): add CitationPill (mono [n] + hover tooltip), ThemeCard (SOURCES overline), and ThemeSkeleton"
```

---

## Task 14: Frontend — `ProposalCard` + `ProposalApproveModal` (TDD)

**Files:**
- Create: `frontend/src/components/ProposalApproveModal.tsx`
- Create: `frontend/src/components/ProposalCard.tsx`
- Create: `frontend/src/components/ProposalCard.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/components/ProposalCard.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { ProposalCard } from "./ProposalCard";
import { tokenStorage } from "../auth/tokenStorage";
import type { ActionProposal } from "../api/types";

function setup(proposal: ActionProposal) {
  tokenStorage.setAccess("valid-token");
  render(
    <Provider store={makeStore()}>
      <ThemeProvider theme={theme}>
        <ProposalCard proposal={proposal} />
      </ThemeProvider>
    </Provider>,
  );
  return { user: userEvent.setup() };
}

const PROPOSED: ActionProposal = {
  id: 7, radarId: 42, kind: "CVE_FIX_PR",
  payloadJson: JSON.stringify({
    cveId: "CVE-2024-1234", packageName: "jackson-databind",
    currentVersion: "2.16.1", fixVersion: "2.17.0",
    repoOwner: "alice", repoName: "api",
  }),
  status: "PROPOSED", prUrl: null, failureReason: null,
  createdAt: "2026-04-20T10:00:00Z", updatedAt: "2026-04-20T10:00:00Z",
};

describe("ProposalCard", () => {
  it("renders CVE id, package, and version bump", () => {
    setup(PROPOSED);
    expect(screen.getByText(/CVE-2024-1234/)).toBeInTheDocument();
    expect(screen.getByText(/jackson-databind/)).toBeInTheDocument();
    expect(screen.getByText(/2\.16\.1/)).toBeInTheDocument();
    expect(screen.getByText(/2\.17\.0/)).toBeInTheDocument();
  });

  it("opens approve modal on Approve click", async () => {
    const { user } = setup(PROPOSED);
    await user.click(screen.getByRole("button", { name: /approve/i }));
    expect(screen.getByRole("dialog", { name: /open migration pr/i })).toBeInTheDocument();
    // Version field prefilled
    expect(screen.getByDisplayValue("2.17.0")).toBeInTheDocument();
  });

  it("shows PR link when executed", () => {
    setup({
      ...PROPOSED,
      status: "EXECUTED",
      prUrl: "https://github.com/alice/api/pull/99",
    });
    expect(screen.getByRole("link", { name: /pr opened/i })).toHaveAttribute(
      "href",
      "https://github.com/alice/api/pull/99",
    );
  });

  it("shows failure reason on FAILED", () => {
    setup({ ...PROPOSED, status: "FAILED", failureReason: "Branch name already exists" });
    expect(screen.getByText(/branch name already exists/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- ProposalCard
```

Expected: FAIL.

- [ ] **Step 3: Implement `ProposalApproveModal`**

Per the export: the modal opens with a contextual preview row (CVE id + package name + current version in a secondary-tinted box) above the editable version field. Primary button shows a pulse dot + "Opening PR…" while submitting.

Create `frontend/src/components/ProposalApproveModal.tsx`:

```tsx
import { useState, type FormEvent } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import { Button } from "./Button";
import { TextField } from "./TextField";
import { Alert } from "./Alert";
import { PulseDot } from "./PulseDot";
import { monoStack } from "../theme";

export interface ProposalApproveContext {
  cveId: string;
  packageName: string;
  fromVersion: string;
}

export interface ProposalApproveModalProps {
  open: boolean;
  context: ProposalApproveContext;
  initialFixVersion: string;
  onCancel: () => void;
  onSubmit: (fixVersion: string) => Promise<void>;
}

export function ProposalApproveModal(props: ProposalApproveModalProps) {
  const { open, context, initialFixVersion, onCancel, onSubmit } = props;
  const [fixVersion, setFixVersion] = useState(initialFixVersion);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit(fixVersion);
    } catch (err) {
      const msg = (err as { data?: { message?: string } }).data?.message;
      setError(msg ?? "Could not open PR. Try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={submitting ? undefined : onCancel}
      maxWidth="xs"
      fullWidth
      aria-labelledby="approve-pr-title"
      PaperProps={{
        sx: {
          borderRadius: 3,
          boxShadow: "0 1px 2px rgba(45,42,38,0.04), 0 12px 40px rgba(45,42,38,0.15)",
        },
      }}
    >
      <DialogTitle
        id="approve-pr-title"
        sx={{ fontSize: 20, lineHeight: "28px", fontWeight: 500, letterSpacing: "-0.01em" }}
      >
        Open migration PR
      </DialogTitle>
      <Box component="form" onSubmit={handleSubmit}>
        <DialogContent>
          <Typography
            sx={{ fontSize: 14, lineHeight: "22px", color: "text.secondary", mb: 3 }}
          >
            This will push a branch to your GitHub repo and open a PR. You can review it before merging.
          </Typography>

          <Box
            sx={{
              padding: "12px 14px",
              bgcolor: "rgba(45,42,38,0.03)",
              borderRadius: 1,
              mb: 2.5,
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              gap: 1.5,
              fontSize: 13,
            }}
          >
            <Box sx={{ minWidth: 0 }}>
              <Box sx={{ fontFamily: monoStack, fontSize: 11, color: "text.secondary" }}>
                {context.cveId}
              </Box>
              <Box sx={{ fontWeight: 500, mt: "2px" }}>{context.packageName}</Box>
            </Box>
            <Box sx={{ fontFamily: monoStack, color: "text.secondary" }}>
              {context.fromVersion}
            </Box>
          </Box>

          {error && (
            <Box sx={{ mb: 2.5 }}>
              <Alert severity="error">{error}</Alert>
            </Box>
          )}

          <TextField
            label="Upgrade to version"
            value={fixVersion}
            onChange={(e) => setFixVersion(e.target.value)}
            disabled={submitting}
            required
            autoFocus
            InputProps={{ sx: { fontFamily: monoStack } }}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button variant="text" onClick={onCancel} disabled={submitting}>Cancel</Button>
          <Button
            type="submit"
            disabled={submitting || !fixVersion.trim()}
            startIcon={submitting ? <PulseDot size={6} color="#ffffff" /> : undefined}
          >
            {submitting ? "Opening PR…" : "Open PR"}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
```

- [ ] **Step 4: Implement `ProposalCard`**

Per the export: card body is CVE id (mono 11px) → package name (sans 14/500) → inline SVG arrow between `fromVersion` and `toVersion` (mono 13). PROPOSED shows primary "Approve" + ghost "Dismiss". EXECUTED shows success-green `[✓] PR opened →`. FAILED shows tinted error block + "Retry" outlined pill. DISMISSED fades to opacity 0.5 + "Dismissed" caption.

Create `frontend/src/components/ProposalCard.tsx`:

```tsx
import { useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "./Button";
import { Alert } from "./Alert";
import { ProposalApproveModal } from "./ProposalApproveModal";
import { monoStack } from "../theme";
import { useApproveProposalMutation, useDismissProposalMutation } from "../api/actionApi";
import type { ActionProposal, CveFixPayload } from "../api/types";

function parsePayload(json: string): Partial<CveFixPayload> {
  try { return JSON.parse(json) as Partial<CveFixPayload>; }
  catch { return {}; }
}

function ArrowIcon() {
  return (
    <Box
      component="svg"
      width="14"
      height="10"
      viewBox="0 0 14 10"
      fill="none"
      aria-hidden="true"
      sx={{ flexShrink: 0, width: 14, height: 10, opacity: 0.5 }}
    >
      <path
        d="M1 5h12M9 1l4 4-4 4"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Box>
  );
}

function CheckIcon() {
  return (
    <Box
      component="svg"
      width="14"
      height="14"
      viewBox="0 0 14 14"
      fill="none"
      aria-hidden="true"
      sx={{ flexShrink: 0, width: 14, height: 14 }}
    >
      <path
        d="M2 7.5l3 3 7-7"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Box>
  );
}

export interface ProposalCardProps {
  proposal: ActionProposal;
}

export function ProposalCard({ proposal }: ProposalCardProps) {
  const payload = parsePayload(proposal.payloadJson);
  const [modalOpen, setModalOpen] = useState(false);
  const [approve] = useApproveProposalMutation();
  const [dismiss, dismissState] = useDismissProposalMutation();

  const isProposed = proposal.status === "PROPOSED";
  const isExecuted = proposal.status === "EXECUTED";
  const isDismissed = proposal.status === "DISMISSED";
  const isFailed = proposal.status === "FAILED";

  async function handleApprove(fixVersion: string) {
    await approve({ id: proposal.id, fixVersion }).unwrap();
    setModalOpen(false);
  }

  async function handleRetry() {
    if (payload.fixVersion) {
      await approve({ id: proposal.id, fixVersion: payload.fixVersion }).unwrap().catch(() => {});
    }
  }

  async function handleDismiss() {
    await dismiss(proposal.id).unwrap().catch(() => {});
  }

  return (
    <Box
      sx={{
        padding: 2,
        bgcolor: "background.paper",
        border: 1,
        borderColor: "divider",
        borderRadius: 1,
        mb: "12px",
        opacity: isDismissed ? 0.5 : 1,
        transition: "opacity 200ms",
      }}
    >
      <Box
        sx={{
          fontFamily: monoStack,
          fontSize: 11,
          letterSpacing: "0.04em",
          color: "text.secondary",
          mb: "6px",
        }}
      >
        {payload.cveId ?? "CVE"}
      </Box>
      <Typography
        sx={{
          fontSize: 14,
          lineHeight: "20px",
          fontWeight: 500,
          color: "text.primary",
          mb: "10px",
        }}
      >
        {payload.packageName ?? "package"}
      </Typography>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 1,
          fontFamily: monoStack,
          fontSize: 13,
          color: "text.primary",
          mb: 2,
        }}
      >
        <Box component="span" sx={{ color: "text.secondary" }}>
          {payload.currentVersion ?? "—"}
        </Box>
        <ArrowIcon />
        <Box component="span" sx={{ fontWeight: 500 }}>
          {payload.fixVersion ?? "—"}
        </Box>
      </Box>

      {isFailed && (
        <Box sx={{ mb: 1.5 }}>
          <Alert severity="error">{proposal.failureReason ?? "PR creation failed."}</Alert>
        </Box>
      )}

      {isProposed && (
        <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
          <Button size="small" onClick={() => setModalOpen(true)}>
            Approve
          </Button>
          <Button variant="text" onClick={handleDismiss} disabled={dismissState.isLoading}>
            Dismiss
          </Button>
        </Box>
      )}

      {isExecuted && proposal.prUrl && (
        <Box
          component="a"
          href={proposal.prUrl}
          target="_blank"
          rel="noreferrer noopener"
          sx={{
            display: "inline-flex",
            alignItems: "center",
            gap: "6px",
            fontSize: 13,
            fontWeight: 500,
            color: "success.main",
            textDecoration: "none",
          }}
        >
          <CheckIcon />
          PR opened →
        </Box>
      )}

      {isDismissed && (
        <Typography sx={{ fontSize: 13, color: "text.secondary" }}>Dismissed</Typography>
      )}

      {isFailed && (
        <Button variant="outlined" size="small" onClick={handleRetry}>
          Retry
        </Button>
      )}

      <ProposalApproveModal
        open={modalOpen}
        context={{
          cveId: payload.cveId ?? "CVE",
          packageName: payload.packageName ?? "package",
          fromVersion: payload.currentVersion ?? "—",
        }}
        initialFixVersion={payload.fixVersion ?? ""}
        onCancel={() => setModalOpen(false)}
        onSubmit={handleApprove}
      />
    </Box>
  );
}
```

- [ ] **Step 5: Run test**

```bash
cd frontend && npm run test -- ProposalCard
```

Expected: PASS (4/4).

- [ ] **Step 6: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/components/ProposalApproveModal.tsx frontend/src/components/ProposalCard.tsx frontend/src/components/ProposalCard.test.tsx
git commit -m "feat(frontend): add ProposalCard and ProposalApproveModal for CVE PR flow"
```

---

## Task 15: Frontend — `RadarDetailPage` (TDD — the hero screen)

**Files:**
- Create: `frontend/src/pages/RadarDetailPage.tsx`
- Create: `frontend/src/pages/RadarDetailPage.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/pages/RadarDetailPage.test.tsx`:

```tsx
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { RadarDetailPage } from "./RadarDetailPage";
import { tokenStorage } from "../auth/tokenStorage";
import { installMockEventSource, MockEventSource } from "../test/eventSourceMock";

function setup(initialPath = "/app/radars/42") {
  tokenStorage.setAccess("valid-token");
  installMockEventSource();
  render(
    <Provider store={makeStore()}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/app/radars/:id" element={<RadarDetailPage />} />
            <Route path="/app/radars" element={<div>list-page</div>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
}

describe("RadarDetailPage", () => {
  afterEach(() => MockEventSource.reset());

  it("renders persisted themes for a READY radar", async () => {
    setup();
    await waitFor(() =>
      expect(screen.getByText(/Spring Boot ecosystem updates/i)).toBeInTheDocument(),
    );
    expect(screen.getByText(/summary text/i)).toBeInTheDocument();
  });

  it("redirects to list on 404", async () => {
    setup("/app/radars/404");
    await waitFor(() => expect(screen.getByText("list-page")).toBeInTheDocument());
  });

  it("renders action proposals panel when proposals exist", async () => {
    setup();
    await waitFor(() => expect(screen.getByText(/CVE-2024-1234/)).toBeInTheDocument());
    expect(screen.getByRole("button", { name: /approve/i })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Add a radar handler for the GENERATING case to MSW**

In `frontend/src/test/mswHandlers.ts`, the current `/api/radars/:id` handler returns READY. Add a special id (43) for GENERATING so later tests can exercise live-streaming. Update the existing `http.get("/api/radars/:id", ...)` handler body to:

```ts
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
      // ... (existing body for the READY case)
```

(Preserve the original READY response for any other id, including 42 and 100.)

- [ ] **Step 3: Run test to verify it fails**

```bash
cd frontend && npm run test -- RadarDetailPage
```

Expected: FAIL.

- [ ] **Step 4: Implement**

Matches `docs/superpowers/design-assets/2026-04-22-plan8-frontend/Dev-Radar-Product.html` `RadarDetailScreen`. Grid layout `minmax(0, 720px) 300px` with 48px gap. Overline + synthetic h1 "This week in your stack" + metadata row (streaming mode shows `PulseDot + "Generating themes…" + "N of M"`; READY shows `N themes · Xs · Ykk tokens`). Persisted themes render as `<ThemeCard>`; pending themes during stream render as `<ThemeSkeleton>`.

Create `frontend/src/pages/RadarDetailPage.tsx`:

```tsx
import { useEffect, useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { PulseDot } from "../components/PulseDot";
import { ThemeCard } from "../components/ThemeCard";
import { ThemeSkeleton } from "../components/ThemeSkeleton";
import { ProposalCard } from "../components/ProposalCard";
import { useGetRadarQuery, radarApi } from "../api/radarApi";
import { useListProposalsByRadarQuery } from "../api/actionApi";
import { useGetMyInterestsQuery } from "../api/interestApi";
import { useRadarStream } from "../radar/useRadarStream";
import { generationFinished, generationStarted } from "../radar/radarGenerationSlice";
import type { AppDispatch, RootState } from "../store";
import type { RadarTheme } from "../api/types";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function formatPeriod(startIso: string, endIso: string): string {
  return `Week of ${formatDate(startIso)} – ${formatDate(endIso)}`;
}

/** Best-effort expected theme count for the progress counter while streaming.
 *  The backend doesn't tell us how many themes the orchestrator will produce,
 *  so we estimate from the user's interest count (min 2, max 5). This only
 *  controls how many skeleton placeholders render. */
function estimateExpectedThemes(interestCount: number): number {
  return Math.max(2, Math.min(5, Math.ceil(interestCount / 2)));
}

export function RadarDetailPage() {
  const params = useParams<{ id: string }>();
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const radarId = Number(params.id);

  const { data: radar, error } = useGetRadarQuery(radarId, { skip: !radarId });
  const { data: proposals = [] } = useListProposalsByRadarQuery(radarId, { skip: !radarId });
  const { data: myInterests } = useGetMyInterestsQuery();

  const isGenerating = radar?.status === "GENERATING";
  const stream = useRadarStream(radarId, isGenerating);
  const streaming = isGenerating && stream.status !== "complete" && stream.status !== "failed";

  const sidebarGeneratingId = useSelector(
    (s: RootState) => s.radarGeneration.currentGeneratingRadarId,
  );

  // Track generating radar in the sidebar indicator
  useEffect(() => {
    if (isGenerating && sidebarGeneratingId !== radarId) {
      dispatch(generationStarted({ radarId, startedAt: new Date().toISOString() }));
    }
    return () => { dispatch(generationFinished()); };
  }, [dispatch, radarId, isGenerating, sidebarGeneratingId]);

  // When the stream finishes, refetch the radar for full item metadata
  useEffect(() => {
    if (stream.status === "complete" || stream.status === "failed") {
      dispatch(radarApi.util.invalidateTags([{ type: "Radar", id: radarId }]));
    }
  }, [stream.status, dispatch, radarId]);

  // Redirect on 404 / 403
  useEffect(() => {
    if (error && "status" in error && (error.status === 404 || error.status === 403)) {
      navigate("/app/radars", { replace: true });
    }
  }, [error, navigate]);

  // Merge persisted themes + streamed themes (streamed ones have empty items
  // until the final refetch fills in full metadata)
  const themes: RadarTheme[] = useMemo(() => {
    const persisted = radar?.themes ?? [];
    if (!isGenerating) return persisted;
    const persistedIds = new Set(persisted.map((t) => t.id));
    const streamedOnly: RadarTheme[] = stream.themes
      .filter((t) => !persistedIds.has(t.themeId))
      .map((t) => ({
        id: t.themeId,
        title: t.title,
        summary: t.summary,
        displayOrder: t.displayOrder,
        items: [],
      }));
    return [...persisted, ...streamedOnly].sort((a, b) => a.displayOrder - b.displayOrder);
  }, [radar, stream.themes, isGenerating]);

  if (!radar) {
    return (
      <Box sx={{ maxWidth: 720 }}>
        <Typography variant="body2" color="text.secondary">Loading radar…</Typography>
      </Box>
    );
  }

  const expectedThemes = estimateExpectedThemes(myInterests?.length ?? 3);
  const pendingCount = streaming ? Math.max(0, expectedThemes - themes.length) : 0;

  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: { xs: "1fr", lg: "minmax(0, 720px) 300px" },
        gap: { xs: 5, lg: 6 },
        alignItems: "flex-start",
      }}
    >
      {/* Read column */}
      <Box sx={{ minWidth: 0 }}>
        <Box sx={{ mb: 5 }}>
          <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 1 }}>
            {formatPeriod(radar.periodStart, radar.periodEnd)}
          </Typography>
          <Typography
            component="h1"
            sx={{
              m: 0,
              fontSize: "2rem",
              lineHeight: "40px",
              fontWeight: 500,
              letterSpacing: "-0.01em",
              color: "text.primary",
            }}
          >
            This week in your stack
          </Typography>
          <Box
            sx={{
              mt: 1.5,
              fontSize: 13,
              lineHeight: "20px",
              color: "text.secondary",
              display: "flex",
              alignItems: "center",
              gap: 1.25,
              flexWrap: "wrap",
              fontVariantNumeric: "tabular-nums",
            }}
          >
            {streaming ? (
              <>
                <PulseDot />
                <span>Generating themes…</span>
                <span>·</span>
                <span>{themes.length} of {expectedThemes}</span>
              </>
            ) : (
              <>
                <span>{themes.length} themes</span>
                <span>·</span>
                <span>{((radar.generationMs ?? 0) / 1000).toFixed(1)}s</span>
                <span>·</span>
                <span>{((radar.tokenCount ?? 0) / 1000).toFixed(1)}k tokens</span>
              </>
            )}
          </Box>

          {stream.status === "failed" && (
            <Typography variant="body2" color="error" sx={{ mt: 2 }}>
              Generation failed: {stream.error ?? "unknown error"}
            </Typography>
          )}
        </Box>

        {themes.map((t) => (
          <ThemeCard key={t.id} theme={t} />
        ))}
        {streaming &&
          Array.from({ length: pendingCount }).map((_, i) => (
            <ThemeSkeleton key={`sk-${i}`} />
          ))}

        {!streaming && themes.length === 0 && (
          <Box
            sx={{
              padding: "80px 24px",
              textAlign: "center",
              border: "1px dashed",
              borderColor: "divider",
              borderRadius: 3,
              color: "text.secondary",
              fontSize: 14,
            }}
          >
            No themes generated.
          </Box>
        )}
      </Box>

      {/* Proposals column */}
      {proposals.length > 0 && (
        <Box component="aside">
          <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 2 }}>
            Action proposals
          </Typography>
          {proposals.map((p) => (
            <ProposalCard key={p.id} proposal={p} />
          ))}
        </Box>
      )}
    </Box>
  );
}
```

- [ ] **Step 5: Run test**

```bash
cd frontend && npm run test -- RadarDetailPage
```

Expected: PASS (3/3).

- [ ] **Step 6: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/pages/RadarDetailPage.tsx frontend/src/pages/RadarDetailPage.test.tsx frontend/src/test/mswHandlers.ts
git commit -m "feat(frontend): add RadarDetailPage with live SSE streaming + proposals panel"
```

---

## Task 16: Frontend — update `AppShell` with real sidebar links + generating indicator

**Files:**
- Modify: `frontend/src/pages/AppShell.tsx`

- [ ] **Step 1: Replace `AppShell.tsx`**

```tsx
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useSelector } from "react-redux";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { keyframes } from "@mui/system";
import { useAuth } from "../auth/useAuth";
import type { RootState } from "../store";

const SIDEBAR_WIDTH = 240;

const pulse = keyframes`
  0% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.85); }
  100% { opacity: 1; transform: scale(1); }
`;

type NavItem =
  | { key: string; label: string; to: string; disabled?: false }
  | { key: string; label: string; disabled: true };

const NAV_ITEMS: NavItem[] = [
  { key: "radars", label: "Radars", to: "/app/radars" },
  { key: "interests", label: "Interests", to: "/app/interests" },
  { key: "settings", label: "Settings", disabled: true },
];

export function AppShell() {
  const { user, logout } = useAuth();
  const generatingRadarId = useSelector((s: RootState) => s.radarGeneration.currentGeneratingRadarId);
  const location = useLocation();

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default", display: "flex" }}>
      <Box
        component="aside"
        sx={{
          width: SIDEBAR_WIDTH,
          flexShrink: 0,
          borderRight: 1,
          borderColor: "divider",
          bgcolor: "background.default",
          p: "32px 24px",
          display: "flex",
          flexDirection: "column",
        }}
      >
        <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 5 }}>
          Dev Radar
        </Typography>

        <Box component="nav" sx={{ display: "flex", flexDirection: "column", gap: "2px" }}>
          {NAV_ITEMS.map((item) => {
            if (item.disabled) {
              return (
                <Box
                  key={item.key}
                  sx={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    px: "10px", py: "8px",
                    borderRadius: "6px",
                    color: "text.secondary",
                    opacity: 0.6,
                    cursor: "not-allowed",
                    fontSize: "0.9375rem",
                    lineHeight: "24px",
                  }}
                >
                  <span>{item.label}</span>
                  <Box
                    component="span"
                    sx={{
                      fontSize: "0.6875rem", lineHeight: "16px",
                      letterSpacing: "0.06em", textTransform: "uppercase",
                      color: "text.secondary", opacity: 0.8,
                    }}
                  >
                    soon
                  </Box>
                </Box>
              );
            }
            const active = location.pathname.startsWith(item.to);
            const showGeneratingDot = item.key === "radars" && generatingRadarId !== null;
            return (
              <Box
                key={item.key}
                component={NavLink}
                to={item.to}
                sx={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  px: "10px", py: "8px",
                  borderRadius: "6px",
                  textDecoration: "none",
                  fontSize: "0.9375rem",
                  lineHeight: "24px",
                  fontWeight: active ? 500 : 400,
                  color: active ? "text.primary" : "text.secondary",
                  bgcolor: active ? "rgba(45,42,38,0.04)" : "transparent",
                  "&:hover": { color: "text.primary", bgcolor: "rgba(45,42,38,0.04)" },
                }}
              >
                <span>{item.label}</span>
                {showGeneratingDot && (
                  <Box
                    sx={{
                      width: 8, height: 8, borderRadius: "50%",
                      bgcolor: "text.primary",
                      animation: `${pulse} 1.4s ease-in-out infinite`,
                    }}
                  />
                )}
              </Box>
            );
          })}
        </Box>

        <Box sx={{ flex: 1 }} />

        <Box sx={{ pt: 2.5, borderTop: 1, borderColor: "divider" }}>
          <Typography sx={{ fontSize: "0.875rem", lineHeight: "20px", fontWeight: 500, color: "text.primary" }}>
            {user?.displayName}
          </Typography>
          <Typography
            sx={{
              fontSize: "0.8125rem", lineHeight: "20px",
              color: "text.secondary",
              mb: 1.5,
              overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
            }}
          >
            {user?.email}
          </Typography>
          <Box
            component="button"
            onClick={logout}
            sx={{
              background: "transparent", border: "none", padding: 0,
              fontFamily: "inherit",
              fontSize: "0.8125rem", lineHeight: "20px",
              color: "text.secondary",
              cursor: "pointer",
              textDecoration: "underline", textUnderlineOffset: "3px",
              "&:hover": { color: "text.primary" },
            }}
          >
            Sign out
          </Box>
        </Box>
      </Box>

      <Box
        component="main"
        sx={{
          flex: 1,
          p: { xs: 4, md: "80px 48px" },
          display: "flex",
          justifyContent: "flex-start",
        }}
      >
        <Outlet />
      </Box>
    </Box>
  );
}
```

Key change: `AppShell` now renders `<Outlet />` in the main content area instead of a hardcoded welcome. The routes render inside it.

- [ ] **Step 2: Update `AppShell.test.tsx` to match the Outlet-based shell**

The existing test from Plan 7 expected a "Welcome, Alice." heading inside the shell itself. That heading is gone — pages render their own content. Replace `frontend/src/pages/AppShell.test.tsx` entirely with:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { loginSucceeded } from "../auth/authSlice";
import { generationStarted } from "../radar/radarGenerationSlice";
import { AppShell } from "./AppShell";

function renderShell(authed: boolean, generating: boolean, initialPath = "/app/radars") {
  const store = makeStore();
  if (authed) {
    store.dispatch(
      loginSucceeded({
        accessToken: "t",
        user: { id: 1, email: "alice@test.com", displayName: "Alice" },
      }),
    );
  }
  if (generating) {
    store.dispatch(generationStarted({ radarId: 100, startedAt: "2026-04-22T10:00:00Z" }));
  }
  render(
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="/app/radars" element={<div data-testid="child">child-content</div>} />
              <Route path="/app/interests" element={<div data-testid="child">interests-content</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
}

describe("AppShell", () => {
  it("renders sidebar with Radars/Interests links and user block", () => {
    renderShell(true, false);
    expect(screen.getByRole("link", { name: /radars/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /interests/i })).toBeInTheDocument();
    expect(screen.getByText(/alice/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign out/i })).toBeInTheDocument();
    expect(screen.getByTestId("child")).toHaveTextContent("child-content");
  });

  it("shows Settings as disabled with 'soon' tag", () => {
    renderShell(true, false);
    expect(screen.getByText(/settings/i)).toBeInTheDocument();
    expect(screen.getByText(/^soon$/i)).toBeInTheDocument();
  });

  it("highlights Interests when on /app/interests", () => {
    renderShell(true, false, "/app/interests");
    expect(screen.getByTestId("child")).toHaveTextContent("interests-content");
  });
});
```

- [ ] **Step 3: Run shell test**

```bash
cd frontend && npm run test -- AppShell
```

Expected: PASS (3/3).

- [ ] **Step 4: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/pages/AppShell.tsx frontend/src/pages/AppShell.test.tsx
git commit -m "feat(frontend): AppShell uses Outlet and adds Interests link + generating indicator"
```

---

## Task 17: Frontend — wire new routes in App.tsx

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: Replace `App.tsx`**

```tsx
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { Provider } from "react-redux";
import { ThemeProvider } from "@mui/material/styles";
import CssBaseline from "@mui/material/CssBaseline";
import { store } from "./store";
import { theme } from "./theme";
import { Landing } from "./pages/Landing";
import { Login } from "./pages/Login";
import { Register } from "./pages/Register";
import { AppShell } from "./pages/AppShell";
import { InterestPickerPage } from "./pages/InterestPickerPage";
import { RadarListPage } from "./pages/RadarListPage";
import { RadarDetailPage } from "./pages/RadarDetailPage";
import { GitHubCallback } from "./pages/GitHubCallback";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { ErrorBoundary } from "./ErrorBoundary";

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/auth/github/complete" element={<GitHubCallback />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/app" element={<AppShell />}>
          <Route index element={<Navigate to="radars" replace />} />
          <Route path="radars" element={<RadarListPage />} />
          <Route path="radars/:id" element={<RadarDetailPage />} />
          <Route path="interests" element={<InterestPickerPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <Provider store={store}>
        <ThemeProvider theme={theme}>
          <CssBaseline />
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </ThemeProvider>
      </Provider>
    </ErrorBoundary>
  );
}
```

- [ ] **Step 2: Update existing `App.test.tsx`**

The previous "login flow lands at /app" test asserted on "Welcome, Test User." text that lived inside the shell. Replace `frontend/src/App.test.tsx` entirely with:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { Provider } from "react-redux";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "./store";
import { theme } from "./theme";
import { AppRoutes } from "./App";
import { tokenStorage } from "./auth/tokenStorage";

function renderAt(path: string) {
  const store = makeStore();
  render(
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[path]}>
          <AppRoutes />
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
  return { user: userEvent.setup(), store };
}

describe("App routing", () => {
  it("shows landing at /", () => {
    renderAt("/");
    expect(screen.getByText(/weekly brief/i)).toBeInTheDocument();
  });

  it("redirects /app to /login when not authenticated", () => {
    localStorage.clear();
    tokenStorage.clear();
    renderAt("/app");
    expect(screen.getByRole("button", { name: /sign in/i })).toBeInTheDocument();
  });

  it("login flow lands at /app/radars with the Radars heading", async () => {
    localStorage.clear();
    tokenStorage.clear();
    const { user } = renderAt("/login");
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/password/i), "ok");
    await user.click(screen.getByRole("button", { name: /sign in/i }));
    await waitFor(() =>
      expect(screen.getByRole("heading", { name: /^radars$/i, level: 1 })).toBeInTheDocument(),
    );
  });
});
```

- [ ] **Step 3: Run full test suite + typecheck + lint + build**

```bash
cd frontend && npm run test && npm run typecheck && npm run lint && npm run build
```

Expected: all pass. Test count should rise from 42 (end of Plan 7) to ~65 across all new Plan 8 suites.

- [ ] **Step 4: Commit**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst
git add frontend/src/App.tsx frontend/src/App.test.tsx
git commit -m "feat(frontend): wire /app/radars, /app/radars/:id, and /app/interests routes"
```

---

## Task 18: End-to-end smoke test against the real backend

**Files:** no files changed — manual verification.

Both servers need to run. Playwright drives the flow; subagents can't open browsers, so this task is for the controller (the human or the top-level agent) to execute.

- [ ] **Step 1: Start the backend**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst/backend && docker compose up -d
DB_HOST_PORT=3307 REDIS_HOST_PORT=6379 \
  ANTHROPIC_API_KEY="$ANTHROPIC_API_KEY" \
  GOOGLE_AI_API_KEY="$GOOGLE_AI_API_KEY" \
  JWT_SECRET=dev-smoke-test-jwt-secret-must-be-at-least-256-bits-long-for-hs256-algorithm-ok \
  GITHUB_TOKEN_ENCRYPTION_KEY=UEjGRJjRBYGZQRdsB7Cln1mLG0qlxPEAU+Vq/Sx0iYE= \
  mvn -f /Users/purandhar/Work/Projects/AI_analyst/backend/pom.xml spring-boot:run
```

Wait for "Started DevRadarApplication".

- [ ] **Step 2: Start the frontend**

```bash
cd /Users/purandhar/Work/Projects/AI_analyst/frontend && npm run dev
```

Open `http://localhost:5173`.

- [ ] **Step 3: Register a new user and log in**

Use a fresh email like `plan8-smoke+1@test.com`. Land on `/app/radars`.

Expected: sidebar shows display name, **Radars** and **Interests** links; Settings reads "Settings SOON"; the Generate button is disabled because no interests yet; first-time banner "Pick a few interests to generate your first radar" visible.

- [ ] **Step 4: Pick interests**

Click "Pick interests →". On `/app/interests`, select a handful of tags — `java`, `spring_boot`, `security`, `docker`. Selected tag chips show filled Ink. "N selected" updates live. Click Save. Success (no error alert).

- [ ] **Step 5: Generate a radar**

Back to `/app/radars`. Generate button is now enabled. Click it. URL changes to `/app/radars/:newId`. A "Generating…" caption with a pulsing dot appears. Sidebar "Radars" link also shows a pulsing dot.

- [ ] **Step 6: Watch themes stream in**

Within ~20s, 2–3 theme cards fade in one by one. Each shows a title (sans h2) and a summary (serif 17px). Citation pills `[1] [2] [3]` appear under each summary.

If the generator proposes a CVE fix, a proposals panel appears on the right with a card showing CVE ID, package, and version bump. Click Approve. A small modal opens with prefilled fix version. Click "Open PR". The card updates in place to show "PR opened →" linking to a GitHub URL.

Expected: no uncaught console errors. SSE connection appears in DevTools Network → EventStream. After `radar.complete`, the generating indicator disappears and the final "Generated in Xs · N tokens" caption shows.

- [ ] **Step 7: Reload mid-stream (optional polish check)**

Start a second radar and immediately reload the page. The already-persisted themes render instantly; the stream reattaches and fills in any remaining. No blank screen.

- [ ] **Step 8: Sign out**

Click "Sign out" in the sidebar. Lands on `/login`. Tokens cleared from `localStorage`.

- [ ] **Step 9: Stop the servers**

```bash
kill $(lsof -ti:8080) $(lsof -ti:5173)
```

- [ ] **Step 10: No commit for this task.**

If any defect was found during the smoke test, open a small fix commit (same pattern as Plan 7's "fix(frontend): smoke-test polish" commit). Do NOT skip fixing visible defects just to close the task.

---

## Self-Review Checklist

Before declaring Plan 8 complete, verify:

1. **Spec §4 routes + sidebar** → Tasks 16 + 17. Sidebar has Radars + Interests + Settings(soon). `/app` redirects to `/app/radars`. Routes `/app/radars`, `/app/radars/:id`, `/app/interests` exist and render.
2. **Spec §5 Interest Picker** → Task 10. Search + categorized tags + save + selected count.
3. **Spec §6 Radar List** → Task 12. List rows, Generate button, first-time banner when no interests, empty state when no radars.
4. **Spec §7 Radar Detail** → Task 15. Two-column layout, themes with serif summary + citations, proposals panel on right, live generating indicator.
5. **Spec §8 SSE** → Task 1 (backend query-param auth) + Task 8 (hook). Chosen Option A (query-param) per spec.
6. **Spec §9 radarGenerationSlice** → Task 6 + Task 16 (sidebar indicator).
7. **Spec §12 error handling** → Tasks 12 (create radar failure alert), 15 (404 → redirect, stream failure caption), 14 (proposal failure Alert + Retry).
8. **All 42 Plan 7 tests still pass** → Task 17 final `npm run test` shows them green.
9. **Backend IT covering query-param auth passes** → Task 1.
10. **Hero screen visual checklist** (from design-decision block at the top of this plan):
    - Serif summary: Task 13, `ThemeCard` uses `serifStack` at 17/28. ✓
    - Numbered citation pills: Task 13. ✓
    - Pulsing generating dot: Task 15 + Task 11. ✓
    - 300ms fade-in on new themes: Task 15 `fadeIn` keyframe. ✓
    - Proposals panel right-side: Task 15. ✓

## Execution Notes

- **Task order is strict up to Task 17.** Tasks 1–7 are independent and could theoretically run in parallel, but each subagent touches the store or types — serial is simpler and cheaper.
- **Model selection:** Tasks 1 and 15 are the heaviest and deserve the strongest available model. Everything else is mechanical TDD and can use a cheaper model.
- **Anti-pattern to avoid:** do NOT merge RTK Query slices into one file. Keep `interestApi.ts` / `radarApi.ts` / `actionApi.ts` separate — splitting by resource is the established pattern in this codebase (look at the backend's resource split) and each tests independently.
- **If a subagent adds an unused import warning, fix it before committing** — Plan 7 surfaced one such cleanup mid-execution. Lint runs with `--max-warnings 0`, so these will block the `npm run lint` step in Task 17.
