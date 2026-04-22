# Dev Radar — Frontend Core Product (Plan 8) Design

**Date:** 2026-04-22
**Status:** Approved for implementation (Plan 8)
**Scope:** Sub-project 1 (MVP) — the three core product screens that make the app actually useful. Plan 9 handles observability + MCP key management.

## 1. Goal

Ship the three screens that turn Plan 7's empty shell into the demoable product:

1. **Interest picker** — pick the tags your weekly radar should focus on.
2. **Radar list** — your past radars, plus "Generate new radar".
3. **Radar detail with live SSE** — the editorial reading experience, themes streaming in as the AI generates them, with CVE action proposals embedded as a right-side panel.

End state: a first-time user logs in, gets nudged to pick interests, generates their first radar, watches themes stream in live, reviews a CVE PR proposal, clicks Approve — PR URL shows up in the UI.

## 2. Scope split context

Plan 7 delivered: auth (email + GitHub) + theme + shell.
Plan 8 (this spec): interests + radar list + radar detail w/ SSE + action proposals embedded in detail.
Plan 9 (next): observability dashboard + MCP API key settings.

## 3. Tech stack additions

Building on Plan 7's stack. New only:

- **RTK Query slices** — `interestApi`, `radarApi`, `actionApi`.
- **Custom SSE hook** — `useRadarStream(radarId)` — wraps browser-native streaming. Implementation detail in §8.
- **Redux slice for pending generation** — `radarGenerationSlice` tracks which radar is currently streaming so the shell can show a "generating…" badge in the sidebar.

No new libraries. No router upgrades.

## 4. Routes + sidebar

**Routes:**
```
/app                       → redirect to /app/radars
/app/radars                → RadarListPage
/app/radars/:id            → RadarDetailPage (with SSE)
/app/interests             → InterestPickerPage
/app/settings              → "soon" placeholder (Plan 9)
/auth/github/complete      → GitHubCallback (from Plan 7)
/                          → Landing
/login                     → Login
/register                  → Register
```

**Sidebar (authenticated shell) — updated from Plan 7:**
```
Radars       → /app/radars     (active when on /app/radars*)
Interests    → /app/interests  (active when on /app/interests)
Settings     → disabled "soon" (Plan 9)
```

Change from Plan 7: replace disabled "Proposals" with enabled "Interests". Proposals no longer have their own route — they live inside a radar detail as a side panel. This matches how the product actually works: a proposal without its originating radar has no context.

**First-time nudge:** If `useAuth().user` is present but `getMyInterests()` returns an empty list, inject a dismissable banner at the top of `/app/radars`:

> **Pick a few interests to generate your first radar.**
> [Pick interests →]

Banner dismisses on navigation to `/app/interests`. It does NOT force a redirect — users can still see their (empty) radar list.

## 5. Interest Picker

### 5.1 Layout

Single column, max-width 720px, same page-layout rhythm as Landing. Sections:

1. **Header** — "Interests" h1 + one-line description in `text.secondary`.
2. **Search input** — filters the visible tag list by `displayName` or `slug`, case-insensitive.
3. **Selected count + Save button** — sticky at the top-right of the content area, or inline under the search. "12 selected · [Save]". Save is disabled when no changes vs server state.
4. **Category sections** — interest tags grouped by category. Each section: category name as `h2`, then a wrap-flow of tag chips.

### 5.2 Tag chip

A toggleable pill. Selected = filled Ink with white text. Unselected = outlined divider with Ink text. Click toggles. No confirmation, no undo in Plan 8 — save or navigate-away-with-unsaved-changes guard (see §5.4).

### 5.3 Data flow

- On mount: `useListTagsQuery()` + `useGetMyInterestsQuery()` — both hit RTK Query, cached for the session.
- Local state: `Set<slug>` representing the current-picked interests.
- Initial local state = server value (reset when server re-fetches).
- `Save` button → `useSetMyInterestsMutation({ slugs: [...] })` with the local set.
- On success: RTK Query invalidates `getMyInterests` + shows a transient success toast.

### 5.4 Unsaved changes guard

If the local set differs from server state, a `beforeunload` listener warns on navigation. Within the SPA, intercept React Router navigation with `useBlocker` (v7) to show a confirm dialog.

## 6. Radar List

### 6.1 Layout

Single column, max-width 720px. Sections:

1. **Header** — "Radars" h1 + "Your past weekly briefs" in `text.secondary`.
2. **Generate button** — primary pill "Generate new radar" on the right of the header. Disabled + tooltip "Pick at least one interest first" if user has no interests.
3. **(Optional) First-time nudge banner** — if interests list empty, see §4.
4. **Radar list** — stacked editorial rows, one per past radar.

### 6.2 Radar row shape

Each row is a button-styled link that navigates to `/app/radars/:id`:

```
┌──────────────────────────────────────────────────────────────┐
│  Apr 20 · 3 themes · 4.2k tokens · 12s               READY   │
│  Week of Apr 13 – Apr 20                                      │
└──────────────────────────────────────────────────────────────┘
```

- Top line: metadata (date, theme count, token count, generation ms) in `text.secondary` caption.
- Bottom line: period (e.g. "Week of Apr 13 – Apr 20") in `body1`.
- Right: status chip — `READY` (Ink filled), `GENERATING` (Ink outlined + spinning dot), `FAILED` (error filled).
- Hover: subtle `rgba(45,42,38,0.04)` background. No card shadow.
- Row spacing: 16px vertical gap, 1px divider between rows.

### 6.3 Empty state

If `list()` returns zero radars AND user has interests:

> **No radars yet.**
> Generate your first brief — it takes about 30 seconds.
> [Generate new radar]

Centered in the content column. Muted.

### 6.4 Data flow

- `useListRadarsQuery({page:0, size:20})` on mount.
- `useCreateRadarMutation()` on button click → navigates to `/app/radars/:newId` immediately on success so the user watches the stream.
- No pagination controls in Plan 8 — 20 latest radars is plenty for demo. Pagination deferred.

## 7. Radar Detail (THE key screen)

This is the editorial reading experience. Most design polish budget goes here.

### 7.1 Layout

Two columns within the main content area:

```
┌─────────────────────────────────────────┬─────────────────────┐
│  Radar detail (read column, 720px)      │  Proposals (280px)  │
│                                         │                     │
│   Apr 20 · 3 themes · …                 │   Action proposals  │
│   ────────────                          │   ──────────────    │
│                                         │                     │
│   # Spring Boot ecosystem updates       │   [proposal card 1] │
│   (serif body, ~17px / 28)              │   [proposal card 2] │
│   … citations: [1] [2] [3]              │                     │
│                                         │                     │
│   # Security advisories affecting Java  │                     │
│   …                                     │                     │
│                                         │                     │
└─────────────────────────────────────────┴─────────────────────┘
```

Proposals column is optional — hidden when no proposals exist. On mobile (<900px), proposals collapse under the themes, not beside.

### 7.2 Theme card (editorial)

Each theme is a self-contained reading block:

- **Title** — h2, 24 / 32 / 500, `-0.01em`.
- **Summary** — serif (`Source Serif Pro`), 17 / 28, `text.primary`. Rendered as markdown-ish paragraphs. Line length capped at ~720px.
- **Citations** — horizontal list of small pills under the summary. Each pill: `[1] spring-boot-3.5-release` with a URL on hover. Clicking opens the source URL in a new tab.
- **Between themes** — 48px vertical gap + no divider. Whitespace is the separator.

### 7.3 Live streaming (SSE)

When the radar status is `GENERATING`:

1. Page renders header + a "Generating…" caption below it with a pulsing dot.
2. `useRadarStream(radarId)` opens the stream.
3. Server events:
   - `radar.started` — sets caption to "Generating…"
   - `radar.theme.complete` — appends a new theme card to the list (with a subtle fade-in animation, 300ms ease-out).
   - `action.proposed` — appends a card to the proposals panel.
   - `radar.complete` — caption swaps to "Generated in {elapsed}s · {tokens} tokens", stream closes.
   - `radar.failed` — caption becomes an error Alert with retry button.
4. When `GENERATING` radar is loaded AFTER themes are already persisted (user refreshed mid-stream), the detail API returns any completed themes; the SSE stream fills in the rest.

### 7.4 Action proposal card (right panel)

Per proposal:

```
┌──────────────────────┐
│  CVE-2024-1234       │
│  jackson-databind    │
│  2.16.1 → 2.17.0     │
│  ───                  │
│  [Approve]  [Dismiss]│
└──────────────────────┘
```

- Compact card, outlined, monochrome.
- Title: CVE id in `overline`.
- Body: package name + version bump.
- Buttons: primary "Approve" (opens a small confirm modal with `fixVersion` pre-filled but editable), ghost "Dismiss".

### 7.5 Proposal flow

- **Approve**: modal prompts for `fixVersion` (pre-filled from proposal payload). Submit → `useApproveProposalMutation` → backend opens GitHub PR → panel card updates to show `EXECUTED` status + linked PR URL.
- **Dismiss**: immediate `useDismissProposalMutation`, card status flips to `DISMISSED` (greyed out, no actions).
- **Failed**: card shows `FAILED` + `failureReason` + "Retry" button.

### 7.6 Data flow

- Mount → `useGetRadarQuery(id)` (returns persisted themes + metadata) + `useListProposalsQuery(id)`.
- If `radar.status === GENERATING` → open SSE hook.
- SSE events dispatched into local React state (NOT Redux) scoped to this page — simpler and matches the ephemeral nature of streaming.
- On stream close / complete → `radarApi.util.invalidateTags` to trigger a fresh `getRadar` fetch that reconciles anything missed.

## 8. SSE implementation

### 8.1 The `EventSource` problem

Browser-native `EventSource` cannot set the `Authorization: Bearer <jwt>` header. Our backend `/api/radars/:id/stream` requires JWT auth. Three ways to resolve:

| Option | Approach | Trade-off |
|---|---|---|
| A | Accept `?token=<jwt>` query param on the SSE endpoint | Tiny backend change. JWT appears in server logs. Mitigation: rotate JWT fast. |
| B | Issue short-lived stream token from JWT'd endpoint; SSE uses that | More moving parts, but keeps the long-lived JWT out of URLs. |
| C | `fetch` with `ReadableStream` + manual SSE parsing | Pure frontend, keeps header auth. More code. |

**Decision: Option A.** Simplest, aligns with how most SPAs handle SSE + Bearer tokens, and the JWT is already in the URL (as a query param) only for the duration of the stream. TLS protects the URL in transit. We'll note this in `AuthService` tests so future migrations know why.

### 8.2 Backend changes required

In `RadarSseResource` or its security config, accept a `?token=<jwt>` query param as an alternative to the `Authorization` header for **this one endpoint only**. Two implementation options — the subagent can pick cleanest:

- Path A: new `StreamTokenAuthFilter` that applies only to `/api/radars/*/stream`, reads `?token=`, decodes via existing `JwtTokenProvider`, sets `SecurityContext` same way `JwtAuthenticationFilter` does. Register BEFORE `JwtAuthenticationFilter` so the header path still works for REST.
- Path B: modify `JwtAuthenticationFilter` to also check `?token=` when path matches `/api/radars/*/stream`.

Either way, a new backend IT proves the query-param path works and no other endpoint accepts `?token=`.

### 8.3 Frontend hook

```ts
// frontend/src/radar/useRadarStream.ts
export function useRadarStream(radarId: Long, enabled: boolean): {
  themes: Theme[];
  proposals: ActionProposal[];
  status: "idle" | "open" | "complete" | "failed";
  error: string | null;
  completionMs: number | null;
}
```

Internally:
- `new EventSource("/api/radars/" + radarId + "/stream?token=" + encodeURIComponent(token))`.
- `addEventListener("radar.theme.complete", ...)`, etc.
- Closes on `complete` / `failed` / unmount.
- Exposes state via useState; no Redux.

## 9. Redux `radarGenerationSlice`

Tiny slice tracking which radar (if any) is currently streaming for the current user:

```ts
{ currentGeneratingRadarId: number | null, startedAt: Instant | null }
```

When the user has a generating radar, the sidebar shows a subtle "Generating…" indicator next to the Radars link — so users don't lose their place if they click away. Click the link → goes back to the streaming radar's detail page.

This slice persists across route transitions but not across hard reloads. Trade-off accepted.

## 10. File structure

```
frontend/src/
├── api/
│   ├── interestApi.ts          (new)
│   ├── radarApi.ts             (new)
│   ├── actionApi.ts            (new)
│   └── types.ts                (modify: + Radar, Theme, Interest, Proposal DTOs)
├── radar/
│   ├── useRadarStream.ts       (new: SSE hook)
│   ├── useRadarStream.test.ts  (new: with mock EventSource)
│   ├── radarGenerationSlice.ts (new: tiny currentRadarId state)
│   └── radarGenerationSlice.test.ts (new)
├── pages/
│   ├── RadarListPage.tsx       (new)
│   ├── RadarListPage.test.tsx  (new)
│   ├── RadarDetailPage.tsx     (new)
│   ├── RadarDetailPage.test.tsx (new)
│   ├── InterestPickerPage.tsx  (new)
│   ├── InterestPickerPage.test.tsx (new)
│   └── AppShell.tsx            (modify: wire real "Interests" link, generating indicator)
├── components/
│   ├── RadarRow.tsx            (new: editorial list row)
│   ├── ThemeCard.tsx           (new: serif summary + citations)
│   ├── CitationPill.tsx        (new)
│   ├── TagChip.tsx             (new: toggleable pill)
│   ├── ProposalCard.tsx        (new)
│   ├── ProposalApproveModal.tsx (new)
│   └── StatusChip.tsx          (new: READY / GENERATING / FAILED)
├── store/
│   └── index.ts                (modify: + all three new APIs + generation slice)
├── App.tsx                     (modify: + new routes)
└── test/
    ├── mswHandlers.ts          (modify: + radar/interest/action handlers)
    └── eventSourceMock.ts      (new)
```

## 11. Backend changes (small)

- Allow `?token=` query param for `/api/radars/{id}/stream` (§8.2).
- New IT confirming query-param auth works and REST endpoints still reject `?token=`.

## 12. Error handling

| Failure | Behavior |
|---|---|
| SSE stream drops mid-generation | Hook auto-retries once after 2s. On second fail: caption becomes "Connection lost. Refresh to see the latest." (no auto-reconnect loop — simple). |
| `getRadar(id)` returns 404 | Redirect to `/app/radars` with a transient error toast "Radar not found." |
| `getRadar(id)` returns 403 (not your radar) | Same as 404 — don't leak existence. |
| `createRadar` fails | Stay on list, show inline Alert "Couldn't start a new radar — try again." |
| `approveProposal` fails | Keep modal open, show Alert with `failureReason` from server. User can retry or dismiss. |
| `setMyInterests` fails | Keep local state, show error toast, no server write happened. |
| User has no interests + clicks "Generate new radar" (shouldn't be possible — button disabled) | Server-side rejection; show the same "pick interests first" banner. |

## 13. Testing strategy

- **Unit (Vitest + MSW):** each RTK Query slice.
- **Hook (Vitest + mocked EventSource):** `useRadarStream` — verifies event handling, reconnect, completion state.
- **Component (RTL + MSW):** every page + `ProposalApproveModal`.
- **E2E smoke (Playwright, manual):** login → pick interests → generate → watch themes stream → approve proposal → see PR URL. Run with the real backend (GitHub OAuth optional for this test).
- **Backend IT:** new test for `?token=` auth on the SSE endpoint.

## 14. Out of scope (deferred / rejected)

- **Rendering markdown inside theme summaries** — Plan 8 treats summary as pre-wrapped plain paragraphs. Rich markdown (inline code, lists, links) deferred to a polish pass. Not a demo blocker.
- **Pagination of radar list** — 20 rows hard cap.
- **Deletion of radars** — not in the spec; users just accumulate.
- **Sharing a radar publicly** — Sub-project 2.
- **Editing themes / regenerating a single theme** — out of scope.
- **Real-time updates of the radar list** — list refetches only on mount + after a create mutation.
- **Service worker / offline mode** — nope.

## 15. Visual design — what goes to Claude Design

The design brief I'll write next covers:

- **Radar list row** — card vs editorial row, metadata density, status chip placement + color.
- **Radar detail layout** — two-column shape, read column width, proposals panel style (outlined card vs flush right-drawer).
- **Theme card** — serif typography scale for summaries, citation pill style (numbered `[1]` style vs full slug).
- **Generating indicator** — pulsing dot placement, caption wording, whether to show a skeleton theme placeholder during streaming.
- **Interest picker** — tag chip style (selected = filled vs border only?), category headers (overline vs h2?), search input prominence.
- **Proposal card + approve modal** — confirm dialog density, whether the PR URL inline-opens or redirects.
- **First-time banner** — dismiss behavior, visual distinction from alerts.

Out of Claude Design's hands (engineer call):
- Routes, state management, SSE mechanics, backend changes.
