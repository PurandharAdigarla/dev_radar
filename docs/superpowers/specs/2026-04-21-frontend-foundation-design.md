# Dev Radar — Frontend Foundation + Auth Design

**Date:** 2026-04-21
**Status:** Approved for implementation (Plan 7)
**Scope:** Sub-project 1 (MVP) — frontend foundation. Subsequent plans will add product screens (Plan 8) and dashboard/settings (Plan 9).

## 1. Goal

Ship the React frontend foundation: project scaffold, routing, theme tokens, Redux store, API client, auth flows (login/register/me), protected routes, and the app shell. End state after Plan 7: an unauthenticated user can register, log in, and land on a signed-in empty app shell showing their display name.

Plans 8 and 9 build on this foundation — no feature screens ship with Plan 7.

## 2. Scope split rationale

The original frontend scope (7+ screens including live SSE radars, action proposal review, observability dashboard, MCP key management) is 35–40 tasks. That's too big for one plan. We split into three:

| Plan | Covers |
|---|---|
| **7 (this spec)** | Foundation, theme, auth, app shell |
| **8** | Interest picker, radar list, radar detail with live SSE, action proposal approval |
| **9** | Observability dashboard, API key (MCP) settings |

Each plan ships something demoable on its own. Plan 7 alone gets "empty authenticated shell." Plan 8 delivers the core product. Plan 9 closes the portfolio loop.

## 3. Tech stack

| Concern | Choice | Rationale |
|---|---|---|
| Build tool | **Vite 5** | Fast dev server, modern bundling, native TS |
| Language | **TypeScript** (strict) | Per global CLAUDE.md — no `any` |
| UI framework | **React 19** | Current stable |
| State | **Redux Toolkit + RTK Query** | Per project CLAUDE.md; RTK Query for API caching |
| Routing | **React Router v7** | Current stable, declarative |
| Component library | **MUI v6** with custom theme | Per project CLAUDE.md |
| Testing | **Vitest + React Testing Library** | Vite-native, replaces Jest |
| Linting | **ESLint + Prettier** | Standard |
| Package manager | **npm** | Default |
| SSE | Native `EventSource` wrapped in `useSSE` hook | Added in Plan 8 — not this plan |

**Directory:** `frontend/` at the repo root, alongside `backend/`.

## 4. Minimalist Claude-inspired aesthetic

### 4.1 Design tokens

| Token | Value | Purpose |
|---|---|---|
| `color.background` | `#faf9f7` | Warm off-white page background |
| `color.surface` | `#ffffff` | Cards, dialogs, elevated surfaces |
| `color.text.primary` | `#2d2a26` | Main text |
| `color.text.secondary` | `#6b655e` | Labels, captions, metadata |
| `color.accent` | `#c15f3c` | Primary buttons, active states, focus rings |
| `color.accent.hover` | `#a84e2f` | Hover state on primary actions |
| `color.divider` | `#e8e4df` | Subtle borders |
| `color.error` | `#b3261e` | Validation errors |
| `color.success` | `#2d7a3e` | Success feedback |
| `font.sans` | `Inter, system-ui, sans-serif` | UI chrome |
| `font.serif` | `"Source Serif Pro", "Tiempos Text", Georgia, serif` | Long-form radar summaries (Plan 8) |
| `font.mono` | `"JetBrains Mono", ui-monospace, monospace` | API keys, code snippets |
| `radius.sm` | `4px` | Small chips |
| `radius.md` | `8px` | Buttons, inputs, cards |
| `radius.lg` | `12px` | Dialogs |
| `shadow.subtle` | `0 1px 2px rgba(45,42,38,0.04), 0 1px 1px rgba(45,42,38,0.03)` | Single elevation level — no stacked shadows |
| `space.*` | 4, 8, 12, 16, 24, 32, 48, 64, 96 | Whitespace ladder |
| `maxWidth.content` | `720px` | Reading-width for long-form |
| `maxWidth.app` | `960px` | App shell |

### 4.2 Typography scale

| Variant | Size / Line-height | Weight | Usage |
|---|---|---|---|
| `h1` | 32 / 40 | 500 | Page titles |
| `h2` | 24 / 32 | 500 | Section headers |
| `h3` | 18 / 26 | 500 | Sub-section headers |
| `body` | 15 / 24 | 400 | Default prose |
| `caption` | 13 / 20 | 400 | Metadata, labels |
| `overline` | 11 / 16 | 500, letter-spacing 0.08em, uppercase | Section eyebrows |

Prose within radar summaries (Plan 8) uses `font.serif` at `17 / 28` for reading comfort.

### 4.3 Chrome philosophy

- **No heavy top bar.** Sidebar is the primary navigation, subtle and text-only.
- **No stacked shadows.** One subtle elevation only; rely on dividers and whitespace for separation.
- **No dense toolbars.** Actions are primary buttons in context, not toolbars.
- **Typography over chrome.** Headers carry visual weight via size + weight, not color blocks.
- **Whitespace-forward.** Default spacing between blocks is 32px; content columns max out at 720px so lines stay readable.

### 4.4 MUI theme override

Create a single `theme.ts` that overrides MUI's palette, typography, shape, shadows, and component defaults. All components in the app consume this theme via `ThemeProvider`. No component inlines hex colors or pixel values — everything via the theme.

## 5. Architecture

### 5.1 Directory structure

```
frontend/
├── package.json
├── tsconfig.json
├── tsconfig.node.json
├── vite.config.ts
├── index.html
├── .eslintrc.cjs
├── .prettierrc
├── src/
│   ├── main.tsx                          # entry point — mounts <App />
│   ├── App.tsx                           # router + ThemeProvider + Redux Provider
│   ├── theme.ts                          # MUI theme (tokens above)
│   ├── api/
│   │   ├── apiClient.ts                  # RTK Query base + JWT injection
│   │   ├── authApi.ts                    # RTK Query slice: register, login, me, refresh
│   │   └── types.ts                      # shared API types
│   ├── auth/
│   │   ├── authSlice.ts                  # Redux slice: accessToken, user
│   │   ├── useAuth.ts                    # hook wrapping slice + RTK Query
│   │   ├── ProtectedRoute.tsx            # redirects unauthenticated → /login
│   │   └── tokenStorage.ts               # localStorage read/write with guard
│   ├── components/
│   │   ├── Button.tsx                    # themed MUI button wrapper
│   │   ├── TextField.tsx                 # themed MUI input wrapper
│   │   ├── Card.tsx                      # themed MUI card wrapper
│   │   ├── Alert.tsx                     # themed MUI alert wrapper
│   │   └── PageLayout.tsx                # max-width centered content column
│   ├── pages/
│   │   ├── Landing.tsx                   # unauthenticated home
│   │   ├── Login.tsx
│   │   ├── Register.tsx
│   │   └── AppShell.tsx                  # authenticated shell with sidebar
│   ├── store/
│   │   └── index.ts                      # configureStore + RTK Query middleware
│   └── test/
│       └── setup.ts                      # Vitest setup (RTL, matchers)
├── public/
└── README.md
```

Deliberately small. Each file has one responsibility. No premature abstractions.

### 5.2 Routing

```
/                  Landing (unauthenticated)
/login             Login
/register          Register
/app               AppShell (protected) — redirects here after login
  /app/*           Future product screens (Plans 8, 9)
```

Protected routes use `<ProtectedRoute>` wrapper that checks `authSlice.accessToken` and redirects to `/login` if absent.

### 5.3 State shape

```ts
// Redux root state
{
  auth: {
    accessToken: string | null,
    user: { id: number, email: string, displayName: string } | null,
    status: 'idle' | 'loading' | 'error',
    error: string | null
  },
  api: { /* RTK Query cache — managed */ }
}
```

RTK Query endpoints (all under one `authApi` slice for Plan 7):
- `register({ email, password, displayName })` → `{ userId }`
- `login({ email, password })` → `{ accessToken, refreshToken, user }`
- `me()` → `{ id, email, displayName }`
- `refresh({ refreshToken })` → `{ accessToken, refreshToken }`
- `logout()` → `void` (client-side token drop)

### 5.4 Token handling

- Access token stored in `localStorage` under key `devradar.accessToken`.
- Refresh token stored in `localStorage` under key `devradar.refreshToken`.
- `apiClient.ts` reads access token on every request, injects `Authorization: Bearer <token>`.
- On 401 response, `apiClient` auto-triggers `refresh` mutation. If refresh succeeds, retry original request. If refresh fails, dispatch `authSlice.logout()` and redirect to `/login`.
- `tokenStorage.ts` wraps localStorage calls with typed getters/setters and handles `QuotaExceededError` / SSR guards (even though we don't SSR — defensive).

### 5.5 App shell layout

After login, user lands at `/app`. The shell:

```
┌─ sidebar (240px) ────────────────┬─ main content (max 720px, centered) ─┐
│                                   │                                       │
│   Dev Radar                       │   Welcome, {displayName}.             │
│                                   │                                       │
│   ──────────────                  │   (empty — Plans 8/9 add content)    │
│   Radars          (disabled)      │                                       │
│   Proposals       (disabled)      │                                       │
│   Settings        (disabled)      │                                       │
│                                   │                                       │
│   ──────────────                  │                                       │
│                                   │                                       │
│   {displayName}                   │                                       │
│   Sign out                        │                                       │
│                                   │                                       │
└───────────────────────────────────┴───────────────────────────────────────┘
```

Sidebar items are disabled placeholders in Plan 7 — they'll wire up in Plans 8/9. The sign-out link works and clears tokens.

Sidebar width fixed at 240px. Main content max-width 720px, centered within remaining space. Background `color.background`; sidebar same background separated by `color.divider` right-border. No drop shadow.

## 6. Error Handling

| Failure | Behavior |
|---|---|
| Invalid credentials on login | Inline `<Alert severity="error">` under form, message from server 401 body |
| Registration validation (weak password, dup email) | Inline `<Alert>` with server field errors mapped to specific inputs |
| Network error | Toast alert at bottom: "Can't reach server — check your connection." |
| 401 after login (stale token) | Silent refresh; if refresh fails → logout + redirect to `/login` |
| 500 | Toast: "Something went wrong. Try again." + error logged to console |
| Uncaught React error | Error boundary at `<App>` level renders "Something broke. Refresh?" — not pretty, but contained |

No fancy error-reporting service in Plan 7; just console + inline UI.

## 7. Testing Strategy

- **Unit (Vitest):** `tokenStorage`, reducers in `authSlice`, `apiClient` fetch-baseQuery with mock fetch.
- **Component (RTL + Vitest):** `Login`, `Register`, `ProtectedRoute`, `AppShell`. Assertions on ARIA labels, not classnames.
- **No E2E in Plan 7.** Playwright comes in Plan 8 or later when we have screens worth smoke-testing.

MSW (Mock Service Worker) for mocking backend in component tests — cleaner than fetch mocks.

## 8. Build + Dev

| Command | Action |
|---|---|
| `npm install` | Install deps |
| `npm run dev` | Vite dev server on port 5173 |
| `npm run build` | Production bundle to `dist/` |
| `npm run test` | Vitest run |
| `npm run test:watch` | Vitest watch mode |
| `npm run lint` | ESLint |
| `npm run typecheck` | `tsc --noEmit` |

Backend runs on `8080` (existing). Dev server proxies `/api` and `/mcp` paths to `http://localhost:8080` via `vite.config.ts`.

## 9. Out of Scope (deferred / rejected)

- **Feature screens** (interest picker, radar list/detail, proposals, dashboard, MCP keys) — Plans 8 and 9.
- **SSR / Next.js** — client-side SPA is fine; SSR adds deployment weight without demo benefit.
- **i18n** — English only.
- **Dark mode** — Claude-inspired light theme is the brand; dark mode is Plan 10+ polish.
- **Accessibility audit** — keyboard nav and ARIA labels yes, full WCAG AA audit later.
- **Analytics / telemetry** — out of scope until something is worth measuring.
- **PWA / offline** — full online app.
- **shadcn/ui or Tailwind** — considered and rejected; CLAUDE.md specifies MUI and consistency matters.
- **Real fonts hosted** — use system fallbacks + optional Google Fonts load; no custom font licensing work.

## 10. Future-Proofing Notes

- The `api/` directory is structured per-slice (currently just `authApi.ts`). Plan 8 will add `radarApi.ts`, `actionApi.ts`, `interestApi.ts`. Plan 9 adds `observabilityApi.ts`, `apiKeyApi.ts`. Each is a separate RTK Query slice injected into the same store.
- `components/` starts with 5 primitives. Feature-specific components live in `pages/` co-located with the page that uses them. Only promote to `components/` when a second page needs it.
- The sidebar is a component in `AppShell.tsx`. When Plans 8/9 add items, they'll pass them in as config — the shell itself doesn't grow.
