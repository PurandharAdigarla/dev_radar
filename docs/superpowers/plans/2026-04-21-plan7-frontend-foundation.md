# Dev Radar — Plan 7: Frontend Foundation + Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold the React frontend at `frontend/` with Vite + TypeScript + Redux Toolkit + MUI (Claude-inspired minimalist theme), implement auth flows (login / register / me / refresh), protected routes, and an app shell so an authenticated user lands at `/app` with their display name.

**Architecture:** Client-side SPA in a new `frontend/` directory. Vite dev server proxies `/api` + `/mcp` to the Spring backend on port 8080. RTK Query owns server cache; Redux slice owns access token + user object; localStorage persists tokens across reloads. MUI v6 is themed once via `src/theme.ts` — no component inlines hex colors or pixel values. Every UI primitive (`Button`, `TextField`, `Card`, `Alert`, `PageLayout`) is a thin wrapper over MUI consuming the theme.

**Tech Stack:** Vite 5, React 19, TypeScript 5.5+ (strict), Redux Toolkit 2.x + RTK Query, React Router v7, MUI v6, Vitest + React Testing Library + MSW, ESLint + Prettier.

**Spec reference:** `docs/superpowers/specs/2026-04-21-frontend-foundation-design.md`.

**Design reference:** Visual design was iterated on claude.ai/design and exported. The exported React JSX, chat transcript, and interactive HTML are preserved at `docs/superpowers/design-assets/2026-04-21-plan7-frontend/` — **open `screens.jsx` as the canonical source of truth for markup, spacing, and micro-interactions**. The tokens and component code in Tasks 3, 9, 10, and 11 are derived directly from it.

**Design decisions locked by the export:**
- **Monochrome** — no terracotta. `Ink` (`#2d2a26`) is the button accent per the user's final instruction ("keep the website monochrome and minimalist").
- **Pill buttons** — `border-radius: 999px`, padding `12px 20px`. NOT the default 8px radius.
- **Landing hero: 48px** with `letter-spacing: -0.02em`; body paragraph bumped to 17px.
- `letter-spacing: -0.01em` on h2 (auth) and AppShell h1.
- Inputs: `border-radius: 8px`, padding `10px 14px`, focus → `0 0 0 2px` Ink ring + border color change to Ink.
- Alert: inline warning icon + tinted error background (`rgba(179,38,30,0.06)`) + `rgba(179,38,30,0.2)` border. Register email-taken variant includes an inline "Sign in instead?" link.
- Sidebar disabled items: reduced opacity (0.6) + `cursor: not-allowed` + small uppercase **"soon"** tag on the right.
- Sidebar user block at bottom with display name, email (ellipsis), and an underlined "Sign out" text button (not a filled button).

---

## File Structure

```
frontend/                                     (NEW — sibling to backend/)
├── package.json
├── tsconfig.json
├── tsconfig.node.json
├── vite.config.ts
├── index.html
├── .eslintrc.cjs
├── .prettierrc
├── .gitignore
├── README.md
├── public/
│   └── favicon.svg
├── src/
│   ├── main.tsx                             # entry point
│   ├── App.tsx                              # ThemeProvider + Redux Provider + router
│   ├── theme.ts                             # MUI theme with Claude-inspired tokens
│   ├── vite-env.d.ts
│   ├── api/
│   │   ├── apiClient.ts                     # RTK Query baseQuery with JWT injection + 401 refresh
│   │   ├── authApi.ts                       # register / login / me / refresh endpoints
│   │   └── types.ts                         # shared API types
│   ├── auth/
│   │   ├── authSlice.ts                     # Redux slice: token, user, status, error
│   │   ├── useAuth.ts                       # hook exposing login/logout/register/user
│   │   ├── ProtectedRoute.tsx               # redirect guard
│   │   └── tokenStorage.ts                  # localStorage wrappers
│   ├── components/
│   │   ├── Button.tsx
│   │   ├── TextField.tsx
│   │   ├── Card.tsx
│   │   ├── Alert.tsx
│   │   └── PageLayout.tsx
│   ├── pages/
│   │   ├── Landing.tsx
│   │   ├── Login.tsx
│   │   ├── Register.tsx
│   │   └── AppShell.tsx
│   ├── store/
│   │   └── index.ts                         # configureStore + RTK Query middleware
│   └── test/
│       ├── setup.ts                         # Vitest + RTL + MSW setup
│       └── mswHandlers.ts                   # mock backend handlers for tests
└── dist/                                    # build output (gitignored)
```

Directory is small by design; every file has one responsibility.

---

## Task 1: Scaffold Vite + TypeScript Project

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/vite-env.d.ts`
- Create: `frontend/.gitignore`
- Create: `frontend/public/favicon.svg`

- [ ] **Step 1: Verify the repo root directory exists and `frontend/` does not**

Run: `ls -la /Users/purandhar/Work/Projects/AI_analyst | grep -E "^d"`
Expected: shows `backend/` and other dirs, no `frontend/` yet.

- [ ] **Step 2: Create `frontend/package.json`**

```json
{
  "name": "devradar-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc --noEmit && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest",
    "lint": "eslint . --ext ts,tsx --max-warnings 0",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "@emotion/react": "^11.13.0",
    "@emotion/styled": "^11.13.0",
    "@mui/material": "^6.1.0",
    "@reduxjs/toolkit": "^2.2.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-redux": "^9.1.0",
    "react-router-dom": "^7.0.0"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.5.0",
    "@testing-library/react": "^16.0.0",
    "@testing-library/user-event": "^14.5.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "@typescript-eslint/eslint-plugin": "^8.8.0",
    "@typescript-eslint/parser": "^8.8.0",
    "@vitejs/plugin-react": "^4.3.0",
    "eslint": "^8.57.0",
    "eslint-plugin-react-hooks": "^4.6.0",
    "eslint-plugin-react-refresh": "^0.4.0",
    "jsdom": "^25.0.0",
    "msw": "^2.4.0",
    "prettier": "^3.3.0",
    "typescript": "^5.5.0",
    "vite": "^5.4.0",
    "vitest": "^2.1.0"
  }
}
```

- [ ] **Step 3: Create `frontend/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 4: Create `frontend/tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 5: Create `frontend/vite.config.ts`**

```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: true },
      "/mcp": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
  },
});
```

- [ ] **Step 6: Create `frontend/index.html`**

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link
      href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Source+Serif+Pro:wght@400;600&family=JetBrains+Mono:wght@400;500&display=swap"
      rel="stylesheet"
    />
    <title>Dev Radar</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 7: Create `frontend/public/favicon.svg`**

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
  <rect width="32" height="32" rx="6" fill="#faf9f7"/>
  <circle cx="16" cy="16" r="5" fill="#c15f3c"/>
</svg>
```

- [ ] **Step 8: Create `frontend/src/main.tsx`**

```tsx
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

- [ ] **Step 9: Create `frontend/src/App.tsx` (minimal placeholder)**

```tsx
export default function App() {
  return <div>Dev Radar — scaffolding</div>;
}
```

- [ ] **Step 10: Create `frontend/src/vite-env.d.ts`**

```ts
/// <reference types="vite/client" />
```

- [ ] **Step 11: Create `frontend/.gitignore`**

```
node_modules
dist
.DS_Store
*.log
.env
.env.local
coverage
```

- [ ] **Step 12: Install deps and verify dev server + build**

```bash
cd frontend && npm install
```

Expected: no errors.

```bash
cd frontend && npm run build
```
Expected: BUILD SUCCESS; `dist/` created with `index.html`.

- [ ] **Step 13: Commit**

```bash
git add frontend/
git commit -m "chore(frontend): scaffold Vite + TypeScript + React project"
```

---

## Task 2: ESLint + Prettier Config

**Files:**
- Create: `frontend/.eslintrc.cjs`
- Create: `frontend/.prettierrc`
- Create: `frontend/README.md`

- [ ] **Step 1: Create `frontend/.eslintrc.cjs`**

```js
module.exports = {
  root: true,
  env: { browser: true, es2022: true },
  extends: [
    "eslint:recommended",
    "plugin:@typescript-eslint/recommended",
    "plugin:react-hooks/recommended",
  ],
  ignorePatterns: ["dist", ".eslintrc.cjs", "node_modules"],
  parser: "@typescript-eslint/parser",
  plugins: ["react-refresh"],
  rules: {
    "react-refresh/only-export-components": ["warn", { allowConstantExport: true }],
    "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
    "@typescript-eslint/no-explicit-any": "error",
  },
};
```

- [ ] **Step 2: Create `frontend/.prettierrc`**

```json
{
  "semi": true,
  "singleQuote": false,
  "trailingComma": "all",
  "printWidth": 100,
  "tabWidth": 2
}
```

- [ ] **Step 3: Create `frontend/README.md`**

```markdown
# Dev Radar Frontend

React + TypeScript SPA for Dev Radar.

## Dev

```bash
npm install
npm run dev     # http://localhost:5173
npm run build
npm run test
npm run lint
npm run typecheck
```

Backend runs on `http://localhost:8080`. Vite dev server proxies `/api` and `/mcp` to it.

## Stack

- Vite 5 + React 19 + TypeScript (strict)
- Redux Toolkit + RTK Query
- React Router v7
- MUI v6 with custom minimalist theme
- Vitest + React Testing Library + MSW
```

- [ ] **Step 4: Verify lint passes**

```bash
cd frontend && npm run lint
```

Expected: 0 errors, 0 warnings (may need to rerun after Task 1 eslint deps installed).

- [ ] **Step 5: Commit**

```bash
git add frontend/.eslintrc.cjs frontend/.prettierrc frontend/README.md
git commit -m "chore(frontend): add ESLint + Prettier config and README"
```

---

## Task 3: MUI Theme (Monochrome Tokens from Claude Design) (TDD)

**Files:**
- Create: `frontend/src/theme.ts`
- Create: `frontend/src/theme.test.ts`

Tokens below match the Claude Design export exactly (see `docs/superpowers/design-assets/2026-04-21-plan7-frontend/screens.jsx`). **Monochrome**: primary accent is Ink (`#2d2a26`), not terracotta. Buttons are pills. Letter-spacing is negative on headings.

- [ ] **Step 1: Write failing test for theme**

Create `frontend/src/theme.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import { theme } from "./theme";

describe("theme", () => {
  it("uses warm neutral background", () => {
    expect(theme.palette.background.default).toBe("#faf9f7");
  });

  it("uses Ink as the monochrome primary accent", () => {
    expect(theme.palette.primary.main).toBe("#2d2a26");
  });

  it("uses Inter as the sans font", () => {
    expect(theme.typography.fontFamily).toContain("Inter");
  });

  it("sets default border radius to 8px", () => {
    expect(theme.shape.borderRadius).toBe(8);
  });

  it("applies negative letter-spacing on h1", () => {
    expect(theme.typography.h1.letterSpacing).toBe("-0.02em");
  });

  it("applies negative letter-spacing on h2", () => {
    expect(theme.typography.h2.letterSpacing).toBe("-0.01em");
  });

  it("provides a single subtle shadow level", () => {
    expect(theme.shadows[1]).toContain("rgba(45,42,38");
  });
});
```

- [ ] **Step 2: Create Vitest setup file (prereq)**

Create `frontend/src/test/setup.ts`:

```ts
import "@testing-library/jest-dom";
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd frontend && npm run test -- theme
```

Expected: FAIL (theme module not found).

- [ ] **Step 4: Create `frontend/src/theme.ts`**

```ts
import { createTheme } from "@mui/material/styles";

const subtleShadow = "0 1px 2px rgba(45,42,38,0.04), 0 1px 1px rgba(45,42,38,0.03)";

const INK = "#2d2a26";
const INK_HOVER = "#000000";
const DIVIDER = "#e8e4df";

export const theme = createTheme({
  palette: {
    mode: "light",
    background: {
      default: "#faf9f7",
      paper: "#ffffff",
    },
    text: {
      primary: INK,
      secondary: "#6b655e",
    },
    primary: {
      main: INK,
      dark: INK_HOVER,
      contrastText: "#ffffff",
    },
    divider: DIVIDER,
    error: { main: "#b3261e" },
    success: { main: "#2d7a3e" },
  },
  typography: {
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif',
    h1: {
      fontSize: "3rem",           // 48px — Landing hero
      lineHeight: 1.15,
      fontWeight: 500,
      letterSpacing: "-0.02em",
    },
    h2: {
      fontSize: "1.5rem",         // 24px — auth headings
      lineHeight: "32px",
      fontWeight: 500,
      letterSpacing: "-0.01em",
    },
    h3: { fontSize: "1.125rem", lineHeight: 1.44, fontWeight: 500 },
    body1: { fontSize: "0.9375rem", lineHeight: 1.6, fontWeight: 400 },     // 15/24
    body2: { fontSize: "0.875rem", lineHeight: "20px", fontWeight: 400 },   // 14/20
    caption: { fontSize: "0.8125rem", lineHeight: "20px", fontWeight: 400 }, // 13/20
    overline: {
      fontSize: "0.6875rem",
      lineHeight: "16px",
      fontWeight: 500,
      letterSpacing: "0.08em",
      textTransform: "uppercase",
    },
    button: {
      textTransform: "none",
      fontWeight: 500,
      fontSize: "0.9375rem",      // 15px
      lineHeight: 1,
      letterSpacing: 0,
    },
  },
  shape: { borderRadius: 8 },
  shadows: [
    "none",
    subtleShadow,
    subtleShadow, subtleShadow, subtleShadow, subtleShadow, subtleShadow,
    subtleShadow, subtleShadow, subtleShadow, subtleShadow, subtleShadow,
    subtleShadow, subtleShadow, subtleShadow, subtleShadow, subtleShadow,
    subtleShadow, subtleShadow, subtleShadow, subtleShadow, subtleShadow,
    subtleShadow, subtleShadow, subtleShadow,
  ],
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true, disableRipple: false },
      styleOverrides: {
        root: {
          borderRadius: 999,                 // pill per Claude Design
          padding: "12px 20px",
          boxShadow: "none",
          "&:focus-visible": {
            boxShadow: `0 0 0 2px ${INK}`,
          },
        },
        contained: {
          "&:hover": { boxShadow: "none", backgroundColor: INK_HOVER },
        },
        outlined: {
          borderColor: DIVIDER,
          color: INK,
          "&:hover": { borderColor: DIVIDER, backgroundColor: "rgba(45,42,38,0.04)" },
        },
        text: {
          padding: "6px 8px",
          borderRadius: 6,
          color: "#6b655e",
          fontSize: "0.875rem",
          "&:hover": { color: INK, backgroundColor: "transparent" },
        },
      },
    },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: { root: { backgroundImage: "none" } },
    },
    MuiDivider: { styleOverrides: { root: { borderColor: DIVIDER } } },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          "& .MuiOutlinedInput-notchedOutline": { borderColor: DIVIDER },
          "&:hover .MuiOutlinedInput-notchedOutline": { borderColor: DIVIDER },
          "&.Mui-focused .MuiOutlinedInput-notchedOutline": { borderColor: INK, borderWidth: 1 },
          "&.Mui-focused": { boxShadow: `0 0 0 2px ${INK}` },
        },
        input: { padding: "10px 14px", fontSize: "0.9375rem", lineHeight: "24px" },
      },
    },
    MuiInputLabel: {
      styleOverrides: {
        root: {
          position: "static",
          transform: "none",
          fontSize: "0.8125rem",
          lineHeight: "20px",
          fontWeight: 500,
          color: INK,
          marginBottom: 8,
          "&.Mui-focused": { color: INK },
        },
      },
    },
    MuiTextField: { defaultProps: { variant: "outlined" } },
  },
});

export const serifStack = '"Source Serif Pro", "Source Serif 4", Georgia, serif';
export const monoStack = '"JetBrains Mono", ui-monospace, "SF Mono", Menlo, monospace';
```

- [ ] **Step 5: Run test to verify it passes**

```bash
cd frontend && npm run test -- theme
```

Expected: PASS (5/5).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/theme.ts frontend/src/theme.test.ts frontend/src/test/setup.ts
git commit -m "feat(frontend): add MUI theme with Claude-inspired minimalist tokens"
```

---

## Task 4: Token Storage Helper (TDD)

**Files:**
- Create: `frontend/src/auth/tokenStorage.ts`
- Create: `frontend/src/auth/tokenStorage.test.ts`

- [ ] **Step 1: Write failing test**

Create `frontend/src/auth/tokenStorage.test.ts`:

```ts
import { describe, it, expect, beforeEach } from "vitest";
import { tokenStorage } from "./tokenStorage";

describe("tokenStorage", () => {
  beforeEach(() => localStorage.clear());

  it("returns null when access token is absent", () => {
    expect(tokenStorage.getAccess()).toBeNull();
  });

  it("persists and reads access token", () => {
    tokenStorage.setAccess("abc");
    expect(tokenStorage.getAccess()).toBe("abc");
  });

  it("persists and reads refresh token", () => {
    tokenStorage.setRefresh("xyz");
    expect(tokenStorage.getRefresh()).toBe("xyz");
  });

  it("clears both tokens", () => {
    tokenStorage.setAccess("a");
    tokenStorage.setRefresh("r");
    tokenStorage.clear();
    expect(tokenStorage.getAccess()).toBeNull();
    expect(tokenStorage.getRefresh()).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- tokenStorage
```

Expected: FAIL (module missing).

- [ ] **Step 3: Implement tokenStorage**

Create `frontend/src/auth/tokenStorage.ts`:

```ts
const ACCESS_KEY = "devradar.accessToken";
const REFRESH_KEY = "devradar.refreshToken";

function safeGet(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeSet(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // QuotaExceededError or storage disabled — silently no-op
  }
}

function safeRemove(key: string): void {
  try {
    localStorage.removeItem(key);
  } catch {
    // ignore
  }
}

export const tokenStorage = {
  getAccess: () => safeGet(ACCESS_KEY),
  setAccess: (v: string) => safeSet(ACCESS_KEY, v),
  getRefresh: () => safeGet(REFRESH_KEY),
  setRefresh: (v: string) => safeSet(REFRESH_KEY, v),
  clear: () => {
    safeRemove(ACCESS_KEY);
    safeRemove(REFRESH_KEY);
  },
};
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd frontend && npm run test -- tokenStorage
```

Expected: PASS (4/4).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/auth/tokenStorage.ts frontend/src/auth/tokenStorage.test.ts
git commit -m "feat(frontend): add tokenStorage with safe localStorage wrappers"
```

---

## Task 5: API Types + authSlice (TDD)

**Files:**
- Create: `frontend/src/api/types.ts`
- Create: `frontend/src/auth/authSlice.ts`
- Create: `frontend/src/auth/authSlice.test.ts`

- [ ] **Step 1: Create shared API types**

Create `frontend/src/api/types.ts`:

```ts
export interface User {
  id: number;
  email: string;
  displayName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}
```

- [ ] **Step 2: Write failing test for authSlice**

Create `frontend/src/auth/authSlice.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import { authReducer, loginSucceeded, loggedOut, setUser } from "./authSlice";
import type { User } from "../api/types";

const initial = authReducer(undefined, { type: "@@INIT" });

describe("authSlice", () => {
  it("starts with no token and no user", () => {
    expect(initial.accessToken).toBeNull();
    expect(initial.user).toBeNull();
  });

  it("stores token + user on loginSucceeded", () => {
    const u: User = { id: 1, email: "a@b.com", displayName: "A" };
    const s = authReducer(initial, loginSucceeded({ accessToken: "t", user: u }));
    expect(s.accessToken).toBe("t");
    expect(s.user).toEqual(u);
  });

  it("clears token + user on loggedOut", () => {
    const u: User = { id: 1, email: "a@b.com", displayName: "A" };
    const loggedIn = authReducer(initial, loginSucceeded({ accessToken: "t", user: u }));
    const out = authReducer(loggedIn, loggedOut());
    expect(out.accessToken).toBeNull();
    expect(out.user).toBeNull();
  });

  it("setUser updates the user profile", () => {
    const u: User = { id: 1, email: "a@b.com", displayName: "Alice" };
    const s = authReducer(initial, setUser(u));
    expect(s.user).toEqual(u);
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd frontend && npm run test -- authSlice
```

Expected: FAIL.

- [ ] **Step 4: Implement authSlice**

Create `frontend/src/auth/authSlice.ts`:

```ts
import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import type { User } from "../api/types";
import { tokenStorage } from "./tokenStorage";

export interface AuthState {
  accessToken: string | null;
  user: User | null;
}

const initialState: AuthState = {
  accessToken: tokenStorage.getAccess(),
  user: null,
};

const slice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    loginSucceeded(state, action: PayloadAction<{ accessToken: string; user: User }>) {
      state.accessToken = action.payload.accessToken;
      state.user = action.payload.user;
    },
    setUser(state, action: PayloadAction<User>) {
      state.user = action.payload;
    },
    tokenRefreshed(state, action: PayloadAction<{ accessToken: string }>) {
      state.accessToken = action.payload.accessToken;
    },
    loggedOut(state) {
      state.accessToken = null;
      state.user = null;
    },
  },
});

export const { loginSucceeded, setUser, tokenRefreshed, loggedOut } = slice.actions;
export const authReducer = slice.reducer;
```

- [ ] **Step 5: Run test to verify it passes**

```bash
cd frontend && npm run test -- authSlice
```

Expected: PASS (4/4).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/auth/authSlice.ts frontend/src/auth/authSlice.test.ts
git commit -m "feat(frontend): add shared API types and authSlice"
```

---

## Task 6: RTK Query API Client with JWT Injection + 401 Refresh (TDD)

**Files:**
- Create: `frontend/src/api/apiClient.ts`
- Create: `frontend/src/api/authApi.ts`
- Create: `frontend/src/test/mswHandlers.ts`
- Modify: `frontend/src/test/setup.ts`

- [ ] **Step 1: Add MSW setup to test/setup.ts**

Replace `frontend/src/test/setup.ts` with:

```ts
import "@testing-library/jest-dom";
import { afterAll, afterEach, beforeAll } from "vitest";
import { setupServer } from "msw/node";
import { handlers } from "./mswHandlers";

export const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

- [ ] **Step 2: Create MSW handlers file**

Create `frontend/src/test/mswHandlers.ts`:

```ts
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
```

- [ ] **Step 3: Write failing test for authApi**

Create `frontend/src/api/authApi.test.ts`:

```ts
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

  it("me endpoint injects Authorization header from state", async () => {
    const store = makeStore();
    tokenStorage.setAccess("valid-token");
    // Re-init slice so initialState picks up the token:
    const store2 = makeStore();
    const result = await store2.dispatch(authApi.endpoints.me.initiate());
    expect(result.data?.displayName).toBe("Alice");
  });
});
```

- [ ] **Step 4: Run test to verify it fails**

```bash
cd frontend && npm run test -- authApi
```

Expected: FAIL.

- [ ] **Step 5: Implement apiClient**

Create `frontend/src/api/apiClient.ts`:

```ts
import { fetchBaseQuery, type BaseQueryFn, type FetchArgs, type FetchBaseQueryError } from "@reduxjs/toolkit/query";
import { tokenStorage } from "../auth/tokenStorage";
import { loggedOut, tokenRefreshed } from "../auth/authSlice";
import type { RefreshResponse } from "./types";

const rawBaseQuery = fetchBaseQuery({
  baseUrl: "/",
  prepareHeaders: (headers) => {
    const token = tokenStorage.getAccess();
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return headers;
  },
});

export const baseQueryWithRefresh: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  let result = await rawBaseQuery(args, api, extraOptions);

  if (result.error?.status === 401) {
    const refreshToken = tokenStorage.getRefresh();
    if (!refreshToken) {
      api.dispatch(loggedOut());
      tokenStorage.clear();
      return result;
    }

    const refreshResult = await rawBaseQuery(
      {
        url: "/api/auth/refresh",
        method: "POST",
        body: { refreshToken },
      },
      api,
      extraOptions,
    );

    if (refreshResult.data) {
      const data = refreshResult.data as RefreshResponse;
      tokenStorage.setAccess(data.accessToken);
      tokenStorage.setRefresh(data.refreshToken);
      api.dispatch(tokenRefreshed({ accessToken: data.accessToken }));
      result = await rawBaseQuery(args, api, extraOptions);
    } else {
      api.dispatch(loggedOut());
      tokenStorage.clear();
    }
  }

  return result;
};
```

- [ ] **Step 6: Implement authApi**

Create `frontend/src/api/authApi.ts`:

```ts
import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { LoginRequest, LoginResponse, RegisterRequest, User } from "./types";

export const authApi = createApi({
  reducerPath: "authApi",
  baseQuery: baseQueryWithRefresh,
  endpoints: (b) => ({
    login: b.mutation<LoginResponse, LoginRequest>({
      query: (body) => ({ url: "/api/auth/login", method: "POST", body }),
    }),
    register: b.mutation<{ userId: number }, RegisterRequest>({
      query: (body) => ({ url: "/api/auth/register", method: "POST", body }),
    }),
    me: b.query<User, void>({
      query: () => ({ url: "/api/users/me" }),
    }),
  }),
});

export const { useLoginMutation, useRegisterMutation, useMeQuery } = authApi;
```

- [ ] **Step 7: Run test to verify it passes**

```bash
cd frontend && npm run test -- authApi
```

Expected: PASS (4/4).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/api/apiClient.ts frontend/src/api/authApi.ts frontend/src/api/authApi.test.ts frontend/src/test/setup.ts frontend/src/test/mswHandlers.ts
git commit -m "feat(frontend): add RTK Query apiClient with JWT injection + 401 refresh"
```

---

## Task 7: Redux Store

**Files:**
- Create: `frontend/src/store/index.ts`
- Create: `frontend/src/store/index.test.ts`

- [ ] **Step 1: Write failing test**

Create `frontend/src/store/index.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import { makeStore, type RootState } from "./index";

describe("store", () => {
  it("includes auth slice and authApi reducer", () => {
    const store = makeStore();
    const state = store.getState() as RootState;
    expect(state.auth).toBeDefined();
    expect(state.authApi).toBeDefined();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- store/index
```

Expected: FAIL.

- [ ] **Step 3: Create store**

Create `frontend/src/store/index.ts`:

```ts
import { configureStore } from "@reduxjs/toolkit";
import { authApi } from "../api/authApi";
import { authReducer } from "../auth/authSlice";

export function makeStore() {
  return configureStore({
    reducer: {
      auth: authReducer,
      [authApi.reducerPath]: authApi.reducer,
    },
    middleware: (getDefault) => getDefault().concat(authApi.middleware),
  });
}

export const store = makeStore();

export type AppStore = ReturnType<typeof makeStore>;
export type RootState = ReturnType<AppStore["getState"]>;
export type AppDispatch = AppStore["dispatch"];
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd frontend && npm run test -- store/index
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/store/index.ts frontend/src/store/index.test.ts
git commit -m "feat(frontend): add Redux store combining authSlice and authApi"
```

---

## Task 8: useAuth Hook (TDD)

**Files:**
- Create: `frontend/src/auth/useAuth.ts`
- Create: `frontend/src/auth/useAuth.test.tsx`

- [ ] **Step 1: Write failing test**

Create `frontend/src/auth/useAuth.test.tsx`:

```tsx
import { describe, it, expect, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { makeStore } from "../store";
import { useAuth } from "./useAuth";
import { tokenStorage } from "./tokenStorage";
import type { ReactNode } from "react";

function wrap(store = makeStore()) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <Provider store={store}>{children}</Provider>;
  };
}

describe("useAuth", () => {
  beforeEach(() => {
    localStorage.clear();
    tokenStorage.clear();
  });

  it("starts unauthenticated", () => {
    const { result } = renderHook(() => useAuth(), { wrapper: wrap() });
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
  });

  it("login sets tokens + user and flips isAuthenticated", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper: wrap() });
    await act(async () => {
      await result.current.login("a@b.com", "ok");
    });
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
    expect(result.current.user?.email).toBe("a@b.com");
    expect(tokenStorage.getAccess()).toBe("access-1");
    expect(tokenStorage.getRefresh()).toBe("refresh-1");
  });

  it("logout clears tokens + user", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper: wrap() });
    await act(async () => {
      await result.current.login("a@b.com", "ok");
    });
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
    act(() => result.current.logout());
    expect(result.current.isAuthenticated).toBe(false);
    expect(tokenStorage.getAccess()).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- useAuth
```

Expected: FAIL.

- [ ] **Step 3: Implement useAuth**

Create `frontend/src/auth/useAuth.ts`:

```ts
import { useCallback } from "react";
import { useDispatch, useSelector } from "react-redux";
import type { RootState, AppDispatch } from "../store";
import { loginSucceeded, loggedOut } from "./authSlice";
import { useLoginMutation, useRegisterMutation } from "../api/authApi";
import { tokenStorage } from "./tokenStorage";

export function useAuth() {
  const dispatch = useDispatch<AppDispatch>();
  const { accessToken, user } = useSelector((s: RootState) => s.auth);
  const [loginMut] = useLoginMutation();
  const [registerMut] = useRegisterMutation();

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await loginMut({ email, password }).unwrap();
      tokenStorage.setAccess(res.accessToken);
      tokenStorage.setRefresh(res.refreshToken);
      dispatch(loginSucceeded({ accessToken: res.accessToken, user: res.user }));
      return res;
    },
    [loginMut, dispatch],
  );

  const register = useCallback(
    async (email: string, password: string, displayName: string) => {
      return await registerMut({ email, password, displayName }).unwrap();
    },
    [registerMut],
  );

  const logout = useCallback(() => {
    tokenStorage.clear();
    dispatch(loggedOut());
  }, [dispatch]);

  return {
    isAuthenticated: accessToken !== null,
    user,
    accessToken,
    login,
    register,
    logout,
  };
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd frontend && npm run test -- useAuth
```

Expected: PASS (3/3).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/auth/useAuth.ts frontend/src/auth/useAuth.test.tsx
git commit -m "feat(frontend): add useAuth hook with login/register/logout"
```

---

## Task 9: Component Primitives (Button, TextField, Card, Alert, PageLayout)

**Files:**
- Create: `frontend/src/components/Button.tsx`
- Create: `frontend/src/components/TextField.tsx`
- Create: `frontend/src/components/Card.tsx`
- Create: `frontend/src/components/Alert.tsx`
- Create: `frontend/src/components/PageLayout.tsx`
- Create: `frontend/src/components/components.test.tsx`

- [ ] **Step 1: Write failing test covering all five primitives**

Create `frontend/src/components/components.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { theme } from "../theme";
import { Button } from "./Button";
import { TextField } from "./TextField";
import { Card } from "./Card";
import { Alert } from "./Alert";
import { PageLayout } from "./PageLayout";

function withTheme(ui: React.ReactNode) {
  return <ThemeProvider theme={theme}>{ui}</ThemeProvider>;
}

describe("primitives", () => {
  it("Button renders label and handles click", () => {
    render(withTheme(<Button>Sign in</Button>));
    expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument();
  });

  it("TextField renders with label", () => {
    render(withTheme(<TextField label="Email" />));
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
  });

  it("Card renders children", () => {
    render(withTheme(<Card><span>Hello</span></Card>));
    expect(screen.getByText("Hello")).toBeInTheDocument();
  });

  it("Alert renders message", () => {
    render(withTheme(<Alert severity="error">Nope</Alert>));
    expect(screen.getByText("Nope")).toBeInTheDocument();
  });

  it("PageLayout wraps children in centered container", () => {
    render(withTheme(<PageLayout><div data-testid="kid" /></PageLayout>));
    expect(screen.getByTestId("kid")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm run test -- components
```

Expected: FAIL.

- [ ] **Step 3: Create Button**

Create `frontend/src/components/Button.tsx`:

```tsx
import MuiButton, { type ButtonProps as MuiButtonProps } from "@mui/material/Button";
import { forwardRef } from "react";

export type ButtonProps = MuiButtonProps;

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "contained", color = "primary", ...rest },
  ref,
) {
  return <MuiButton ref={ref} variant={variant} color={color} {...rest} />;
});
```

- [ ] **Step 4: Create TextField**

Create `frontend/src/components/TextField.tsx`:

```tsx
import MuiTextField, { type TextFieldProps as MuiTextFieldProps } from "@mui/material/TextField";
import { forwardRef } from "react";

export type TextFieldProps = MuiTextFieldProps;

export const TextField = forwardRef<HTMLDivElement, TextFieldProps>(function TextField(
  { fullWidth = true, margin = "normal", ...rest },
  ref,
) {
  return <MuiTextField ref={ref} fullWidth={fullWidth} margin={margin} {...rest} />;
});
```

- [ ] **Step 5: Create Card**

Create `frontend/src/components/Card.tsx`:

```tsx
import MuiCard, { type CardProps as MuiCardProps } from "@mui/material/Card";
import MuiCardContent from "@mui/material/CardContent";
import { forwardRef, type ReactNode } from "react";

export interface CardProps extends Omit<MuiCardProps, "children"> {
  children: ReactNode;
  padded?: boolean;
}

export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  { children, padded = true, variant = "outlined", ...rest },
  ref,
) {
  return (
    <MuiCard ref={ref} variant={variant} {...rest}>
      {padded ? <MuiCardContent>{children}</MuiCardContent> : children}
    </MuiCard>
  );
});
```

- [ ] **Step 6: Create Alert**

Per the Claude Design export, the error alert is a custom inline block (tinted error bg + thin red border + warning icon), not MUI's filled/outlined Alert variants. The primitive below matches that shape exactly.

Create `frontend/src/components/Alert.tsx`:

```tsx
import Box from "@mui/material/Box";
import type { ReactNode } from "react";

export type AlertSeverity = "error" | "success";

export interface AlertProps {
  severity?: AlertSeverity;
  children: ReactNode;
  role?: string;
}

const palette = {
  error: {
    color: "#b3261e",
    bg: "rgba(179,38,30,0.06)",
    border: "rgba(179,38,30,0.2)",
  },
  success: {
    color: "#2d7a3e",
    bg: "rgba(45,122,62,0.06)",
    border: "rgba(45,122,62,0.2)",
  },
};

export function Alert({ severity = "error", children, role = "alert" }: AlertProps) {
  const p = palette[severity];
  return (
    <Box
      role={role}
      sx={{
        fontSize: 14,
        lineHeight: "20px",
        color: p.color,
        background: p.bg,
        border: `1px solid ${p.border}`,
        borderRadius: 1,
        padding: "10px 14px",
        display: "flex",
        gap: 1.25,
        alignItems: "flex-start",
      }}
    >
      <Box
        component="svg"
        width="16"
        height="16"
        viewBox="0 0 16 16"
        fill="none"
        sx={{ flexShrink: 0, mt: "2px" }}
        aria-hidden="true"
      >
        <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth="1.4" />
        <path d="M8 4.5v4M8 11v.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      </Box>
      <Box component="span">{children}</Box>
    </Box>
  );
}
```

The `components.test.tsx` assertion `screen.getByText("Nope")` still passes — the message lives inside a `<span>` descendant.

- [ ] **Step 7: Create PageLayout**

Create `frontend/src/components/PageLayout.tsx`:

```tsx
import Box from "@mui/material/Box";
import type { ReactNode } from "react";

export interface PageLayoutProps {
  children: ReactNode;
  maxWidth?: number;
}

export function PageLayout({ children, maxWidth = 720 }: PageLayoutProps) {
  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "background.default",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        py: { xs: 6, md: 10 },
        px: 3,
      }}
    >
      <Box sx={{ width: "100%", maxWidth: `${maxWidth}px` }}>{children}</Box>
    </Box>
  );
}
```

- [ ] **Step 8: Run test to verify it passes**

```bash
cd frontend && npm run test -- components
```

Expected: PASS (5/5).

- [ ] **Step 9: Commit**

```bash
git add frontend/src/components/
git commit -m "feat(frontend): add themed UI primitives (Button, TextField, Card, Alert, PageLayout)"
```

---

## Task 10: Landing, Login, Register Pages (TDD)

**Files:**
- Create: `frontend/src/pages/Landing.tsx`
- Create: `frontend/src/pages/Login.tsx`
- Create: `frontend/src/pages/Register.tsx`
- Create: `frontend/src/pages/Login.test.tsx`
- Create: `frontend/src/pages/Register.test.tsx`

- [ ] **Step 1: Write failing test for Login page**

Create `frontend/src/pages/Login.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { Login } from "./Login";

function setup() {
  const store = makeStore();
  render(
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <MemoryRouter>
          <Login />
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
  return { user: userEvent.setup(), store };
}

describe("Login page", () => {
  it("renders email + password fields", () => {
    setup();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it("shows error alert on invalid credentials", async () => {
    const { user } = setup();
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/password/i), "wrong");
    await user.click(screen.getByRole("button", { name: /sign in/i }));
    await waitFor(() =>
      expect(screen.getByText(/invalid credentials/i)).toBeInTheDocument(),
    );
  });
});
```

- [ ] **Step 2: Write failing test for Register page**

Create `frontend/src/pages/Register.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { Register } from "./Register";

function setup() {
  render(
    <Provider store={makeStore()}>
      <ThemeProvider theme={theme}>
        <MemoryRouter>
          <Register />
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
  return { user: userEvent.setup() };
}

describe("Register page", () => {
  it("renders email, password, display name fields", () => {
    setup();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/display name/i)).toBeInTheDocument();
  });

  it("shows error on duplicate email", async () => {
    const { user } = setup();
    await user.type(screen.getByLabelText(/email/i), "dup@test.com");
    await user.type(screen.getByLabelText(/password/i), "Password1!");
    await user.type(screen.getByLabelText(/display name/i), "Dup");
    await user.click(screen.getByRole("button", { name: /create account/i }));
    await waitFor(() =>
      expect(screen.getByText(/already registered/i)).toBeInTheDocument(),
    );
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
cd frontend && npm run test -- pages
```

Expected: FAIL.

- [ ] **Step 4: Create Landing page**

Matches `docs/superpowers/design-assets/2026-04-21-plan7-frontend/screens.jsx` `LandingScreen` exactly. 640px max-width, left-aligned, 32px overline-to-h1 gap, 24px h1-to-body gap, 40px body-to-buttons gap, pill buttons side by side.

Create `frontend/src/pages/Landing.tsx`:

```tsx
import { Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "../components/Button";

export function Landing() {
  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "background.default",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        px: 4,
        py: 10,
      }}
    >
      <Box sx={{ maxWidth: 640, width: "100%" }}>
        <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 4 }}>
          Dev Radar
        </Typography>
        <Typography variant="h1" component="h1" sx={{ textWrap: "balance" }}>
          A weekly brief for what you care about.
        </Typography>
        <Typography
          variant="body1"
          color="text.secondary"
          sx={{
            fontSize: "1.0625rem",       // 17px — hero paragraph
            lineHeight: 1.6,
            mt: 3,
            mb: 5,
            maxWidth: 560,
            textWrap: "pretty",
          }}
        >
          Personalized radars synthesized from Hacker News, GitHub Trending, and security
          advisories, with citations you can trust.
        </Typography>
        <Box sx={{ display: "flex", gap: 1.5, flexWrap: "wrap" }}>
          <Button component={RouterLink} to="/register">
            Create account
          </Button>
          <Button component={RouterLink} to="/login" variant="outlined">
            Sign in
          </Button>
        </Box>
      </Box>
    </Box>
  );
}
```

- [ ] **Step 5: Create a shared AuthCard wrapper**

Both Login and Register sit inside a centered 400px column with a "Dev Radar" overline above. Extract once.

Create `frontend/src/components/AuthCard.tsx`:

```tsx
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import type { ReactNode } from "react";

export function AuthCard({ children }: { children: ReactNode }) {
  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "background.default",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        px: 4,
        py: 10,
      }}
    >
      <Box sx={{ maxWidth: 400, width: "100%" }}>
        <Box sx={{ display: "flex", justifyContent: "center", mb: 5 }}>
          <Typography variant="overline" color="text.secondary">Dev Radar</Typography>
        </Box>
        {children}
      </Box>
    </Box>
  );
}
```

- [ ] **Step 6: Create Login page**

Create `frontend/src/pages/Login.tsx`:

```tsx
import { useState, type FormEvent } from "react";
import { useNavigate, Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Link from "@mui/material/Link";
import { Button } from "../components/Button";
import { TextField } from "../components/TextField";
import { Alert } from "../components/Alert";
import { AuthCard } from "../components/AuthCard";
import { useAuth } from "../auth/useAuth";

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate("/app");
    } catch (err) {
      const status = (err as { status?: number }).status;
      const serverMsg = (err as { data?: { message?: string } }).data?.message;
      setError(serverMsg ?? (status === 401 ? "Invalid credentials" : "Sign-in failed"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard>
      <Typography variant="h2" sx={{ mb: 1 }}>Sign in</Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        Welcome back.
      </Typography>

      <Box component="form" onSubmit={onSubmit} sx={{ display: "flex", flexDirection: "column", gap: 3.5 }}>
        {error && <Alert severity="error">{error}</Alert>}

        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          InputLabelProps={{ shrink: true }}
          required
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          InputLabelProps={{ shrink: true }}
          required
        />

        <Box sx={{ mt: 0.5 }}>
          <Button type="submit" fullWidth disabled={submitting}>
            {submitting ? "Signing in…" : "Sign in"}
          </Button>
        </Box>
      </Box>

      <Typography
        variant="body2"
        color="text.secondary"
        align="center"
        sx={{ mt: 4 }}
      >
        New here?{" "}
        <Link
          component={RouterLink}
          to="/register"
          sx={{ color: "text.primary", textDecoration: "underline", textUnderlineOffset: "3px" }}
        >
          Create an account
        </Link>
      </Typography>
    </AuthCard>
  );
}
```

- [ ] **Step 7: Create Register page**

Differences from Login: extra Display name field, helper text on two fields, and an inline "Sign in instead?" link inside the email-taken error alert (per Claude Design export).

Create `frontend/src/pages/Register.tsx`:

```tsx
import { useState, type FormEvent } from "react";
import { useNavigate, Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Link from "@mui/material/Link";
import { Button } from "../components/Button";
import { TextField } from "../components/TextField";
import { Alert } from "../components/Alert";
import { AuthCard } from "../components/AuthCard";
import { useAuth } from "../auth/useAuth";

type ErrorKind = "email-taken" | "generic" | null;

export function Register() {
  const { register, login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<ErrorKind>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setErrorMsg(null);
    setSubmitting(true);
    try {
      await register(email, password, displayName);
      await login(email, password);
      navigate("/app");
    } catch (err) {
      const status = (err as { status?: number }).status;
      const serverMsg = (err as { data?: { message?: string } }).data?.message;
      if (status === 409) {
        setError("email-taken");
      } else {
        setError("generic");
        setErrorMsg(serverMsg ?? "Registration failed");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard>
      <Typography variant="h2" sx={{ mb: 1 }}>Create your account</Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        Start getting your weekly radar.
      </Typography>

      <Box component="form" onSubmit={onSubmit} sx={{ display: "flex", flexDirection: "column", gap: 3.5 }}>
        {error === "email-taken" && (
          <Alert severity="error">
            That email is already registered.{" "}
            <Link
              component={RouterLink}
              to="/login"
              sx={{ color: "inherit", textDecoration: "underline", textUnderlineOffset: "3px" }}
            >
              Sign in instead?
            </Link>
          </Alert>
        )}
        {error === "generic" && errorMsg && <Alert severity="error">{errorMsg}</Alert>}

        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          InputLabelProps={{ shrink: true }}
          required
        />
        <TextField
          label="Display name"
          autoComplete="nickname"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          InputLabelProps={{ shrink: true }}
          helperText="Shown on your radar header. You can change this later."
          required
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          InputLabelProps={{ shrink: true }}
          helperText="At least 8 characters."
          required
        />

        <Box sx={{ mt: 0.5 }}>
          <Button type="submit" fullWidth disabled={submitting}>
            {submitting ? "Creating…" : "Create account"}
          </Button>
        </Box>
      </Box>

      <Typography variant="body2" color="text.secondary" align="center" sx={{ mt: 4 }}>
        Have an account?{" "}
        <Link
          component={RouterLink}
          to="/login"
          sx={{ color: "text.primary", textDecoration: "underline", textUnderlineOffset: "3px" }}
        >
          Sign in
        </Link>
      </Typography>
    </AuthCard>
  );
}
```

- [ ] **Step 8: Run tests to verify they pass**

```bash
cd frontend && npm run test -- pages
```

Expected: PASS. Login/Register test assertions still match (`screen.getByLabelText(/email/i)`, `screen.getByText(/already registered/i)`, etc.) — the AuthCard wrapper and shared overline don't affect those queries. If a test fails on label matching, it's because MUI's `InputLabelProps={{ shrink: true }}` keeps the label visible and clickable, which is what we want — update the assertion if needed.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/pages/Landing.tsx frontend/src/pages/Login.tsx frontend/src/pages/Register.tsx frontend/src/components/AuthCard.tsx frontend/src/pages/Login.test.tsx frontend/src/pages/Register.test.tsx
git commit -m "feat(frontend): add Landing, Login, and Register pages"
```

---

## Task 11: ProtectedRoute + AppShell (TDD)

**Files:**
- Create: `frontend/src/auth/ProtectedRoute.tsx`
- Create: `frontend/src/auth/ProtectedRoute.test.tsx`
- Create: `frontend/src/pages/AppShell.tsx`
- Create: `frontend/src/pages/AppShell.test.tsx`

- [ ] **Step 1: Write failing test for ProtectedRoute**

Create `frontend/src/auth/ProtectedRoute.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { makeStore } from "../store";
import { loginSucceeded } from "./authSlice";
import { ProtectedRoute } from "./ProtectedRoute";

function renderAt(path: string, authed: boolean) {
  const store = makeStore();
  if (authed) {
    store.dispatch(
      loginSucceeded({
        accessToken: "t",
        user: { id: 1, email: "a@b.com", displayName: "Alice" },
      }),
    );
  }
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/app" element={<div>protected</div>} />
          </Route>
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

describe("ProtectedRoute", () => {
  it("renders outlet when authenticated", () => {
    renderAt("/app", true);
    expect(screen.getByText("protected")).toBeInTheDocument();
  });

  it("redirects to /login when not authenticated", () => {
    renderAt("/app", false);
    expect(screen.getByText("login page")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Write failing test for AppShell**

Create `frontend/src/pages/AppShell.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { loginSucceeded } from "../auth/authSlice";
import { AppShell } from "./AppShell";

describe("AppShell", () => {
  it("renders welcome with user's display name", () => {
    const store = makeStore();
    store.dispatch(
      loginSucceeded({
        accessToken: "t",
        user: { id: 1, email: "alice@test.com", displayName: "Alice" },
      }),
    );
    render(
      <Provider store={store}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <AppShell />
          </MemoryRouter>
        </ThemeProvider>
      </Provider>,
    );
    expect(screen.getByText(/welcome, alice/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign out/i })).toBeInTheDocument();
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
cd frontend && npm run test -- ProtectedRoute AppShell
```

Expected: FAIL.

- [ ] **Step 4: Create ProtectedRoute**

Create `frontend/src/auth/ProtectedRoute.tsx`:

```tsx
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "./useAuth";

export function ProtectedRoute() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <Outlet />;
}
```

- [ ] **Step 5: Create AppShell**

Matches `docs/superpowers/design-assets/2026-04-21-plan7-frontend/screens.jsx` `AppShellScreen`. Sidebar is 240px with a right border; nav items are `not-allowed` with opacity 0.6 and a small uppercase **"soon"** tag on the right; the user block sits at the bottom of the sidebar with a top border and an underlined text "Sign out" link. Main uses a **32px h1** (not the 48px Landing hero size) because it's an app-shell heading, not a marketing headline.

Create `frontend/src/pages/AppShell.tsx`:

```tsx
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useAuth } from "../auth/useAuth";

const SIDEBAR_WIDTH = 240;
const NAV_ITEMS = [
  { key: "radars", label: "Radars" },
  { key: "proposals", label: "Proposals" },
  { key: "settings", label: "Settings" },
];

export function AppShell() {
  const { user, logout } = useAuth();

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
          {NAV_ITEMS.map((item) => (
            <Box
              key={item.key}
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                px: "10px",
                py: "8px",
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
                  fontSize: "0.6875rem",
                  lineHeight: "16px",
                  letterSpacing: "0.06em",
                  textTransform: "uppercase",
                  color: "text.secondary",
                  opacity: 0.8,
                }}
              >
                soon
              </Box>
            </Box>
          ))}
        </Box>

        <Box sx={{ flex: 1 }} />

        <Box sx={{ pt: 2.5, borderTop: 1, borderColor: "divider" }}>
          <Typography sx={{ fontSize: "0.875rem", lineHeight: "20px", fontWeight: 500, color: "text.primary" }}>
            {user?.displayName}
          </Typography>
          <Typography
            sx={{
              fontSize: "0.8125rem",
              lineHeight: "20px",
              color: "text.secondary",
              mb: 1.5,
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {user?.email}
          </Typography>
          <Box
            component="button"
            onClick={logout}
            sx={{
              background: "transparent",
              border: "none",
              padding: 0,
              fontFamily: "inherit",
              fontSize: "0.8125rem",
              lineHeight: "20px",
              color: "text.secondary",
              cursor: "pointer",
              textDecoration: "underline",
              textUnderlineOffset: "3px",
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
        <Box sx={{ maxWidth: 720, width: "100%" }}>
          <Typography
            component="h1"
            sx={{ fontSize: "2rem", lineHeight: "40px", fontWeight: 500, letterSpacing: "-0.01em" }}
          >
            Welcome, {user?.displayName ?? "there"}.
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mt: 2, maxWidth: 560 }}>
            Your radars and proposals will appear here soon.
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd frontend && npm run test -- ProtectedRoute AppShell
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/auth/ProtectedRoute.tsx frontend/src/auth/ProtectedRoute.test.tsx frontend/src/pages/AppShell.tsx frontend/src/pages/AppShell.test.tsx
git commit -m "feat(frontend): add ProtectedRoute guard and AppShell layout"
```

---

## Task 12: Wire App.tsx — Router + Providers + Error Boundary

**Files:**
- Modify: `frontend/src/App.tsx`
- Create: `frontend/src/ErrorBoundary.tsx`
- Create: `frontend/src/App.test.tsx`

- [ ] **Step 1: Write failing test covering routing end-to-end**

Create `frontend/src/App.test.tsx`:

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

  it("login flow lands at /app", async () => {
    localStorage.clear();
    tokenStorage.clear();
    const { user } = renderAt("/login");
    await user.type(screen.getByLabelText(/email/i), "a@b.com");
    await user.type(screen.getByLabelText(/password/i), "ok");
    await user.click(screen.getByRole("button", { name: /sign in/i }));
    await waitFor(() =>
      expect(screen.getByText(/welcome, test user/i)).toBeInTheDocument(),
    );
  });
});
```

- [ ] **Step 2: Create ErrorBoundary**

Create `frontend/src/ErrorBoundary.tsx`:

```tsx
import { Component, type ReactNode } from "react";

interface State { hasError: boolean }
interface Props { children: ReactNode }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error) {
    console.error("Uncaught error:", error);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 32, fontFamily: "Inter, sans-serif" }}>
          <h1>Something broke.</h1>
          <p>Try refreshing the page.</p>
        </div>
      );
    }
    return this.props.children;
  }
}
```

- [ ] **Step 3: Rewrite App.tsx**

Replace `frontend/src/App.tsx`:

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
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { ErrorBoundary } from "./ErrorBoundary";

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/app" element={<AppShell />} />
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

- [ ] **Step 4: Run test to verify it passes**

```bash
cd frontend && npm run test -- App
```

Expected: PASS.

- [ ] **Step 5: Run full suite + build + typecheck + lint**

```bash
cd frontend && npm run test && npm run typecheck && npm run lint && npm run build
```

Expected: all pass, `dist/` produced.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/ErrorBoundary.tsx
git commit -m "feat(frontend): wire App with router, providers, and error boundary"
```

---

## Task 13: Boot the Backend and Smoke-Test End-to-End

**Files:** (no files changed — this is a manual smoke test)

- [ ] **Step 1: Start the backend**

In one terminal:

```bash
cd backend && mvn spring-boot:run
```

Wait for "Started DevRadarApplication" in the logs.

- [ ] **Step 2: Start the frontend dev server**

In another terminal:

```bash
cd frontend && npm run dev
```

Open `http://localhost:5173` in a browser.

- [ ] **Step 3: Verify landing page**

- Page loads with "A weekly brief for what you care about." headline.
- Background is warm off-white (`#faf9f7`).
- Inter font is rendered (check in dev tools).
- Two buttons visible: "Create account" and "Sign in".

- [ ] **Step 4: Register a new user**

- Click "Create account".
- Fill in email (use a fresh one like `e2e+1@test.com`), display name, and password meeting backend requirements.
- Submit.
- Expect redirect to `/app` and sidebar showing "Dev Radar" with your display name + email.
- Main area shows "Welcome, {displayName}.".

- [ ] **Step 5: Sign out**

- Click "Sign out" in sidebar.
- Expect redirect to `/login`.
- Open devtools → Application → Local Storage → verify `devradar.accessToken` and `devradar.refreshToken` are removed.

- [ ] **Step 6: Sign back in**

- Enter same credentials on `/login`.
- Submit.
- Expect redirect to `/app`.

- [ ] **Step 7: Reload**

- With `/app` open, hit reload.
- Expect to stay authenticated (access token persisted in localStorage, `me` call on reload not required for Plan 7 since shell uses stored user — user is hydrated on login only).

**Known limitation in Plan 7:** on hard reload the `user` Redux state is empty (only `accessToken` is hydrated from localStorage). The shell falls back to "Welcome, there." This is a deliberate simplification; Plan 8 adds a `me` query fired on app mount to rehydrate the user.

- [ ] **Step 8: Document any UX issues observed**

If fonts don't load, colors look off, or any route misbehaves, stop and fix before closing the task. UI correctness is the whole point of a UI plan — don't ship a broken theme.

- [ ] **Step 9: No commit for this task (manual verification only)**

---

## Self-Review Checklist (Run after all 13 tasks)

Before declaring Plan 7 complete, verify:

1. **Spec coverage — every requirement has a task:**
   - Vite + TS + React scaffold ✅ Task 1
   - ESLint + Prettier ✅ Task 2
   - MUI theme with Claude tokens (palette, typography, shape, shadow) ✅ Task 3
   - localStorage token persistence ✅ Task 4
   - authSlice + API types ✅ Task 5
   - RTK Query client with JWT injection + 401 refresh ✅ Task 6
   - Redux store ✅ Task 7
   - useAuth hook ✅ Task 8
   - 5 component primitives ✅ Task 9
   - Landing, Login, Register pages ✅ Task 10
   - ProtectedRoute + AppShell ✅ Task 11
   - App.tsx wiring + ErrorBoundary + CssBaseline ✅ Task 12
   - End-to-end smoke test against real backend ✅ Task 13

2. **No feature screens implemented** — radars, proposals, dashboard, keys should all be absent. Sidebar links are disabled placeholders. This is a foundation plan.

3. **Strict TypeScript — no `any`** — ESLint rule `@typescript-eslint/no-explicit-any: error` enforces this.

4. **Every primitive consumes the theme** — grep `frontend/src` for raw hex values; any match outside `theme.ts` is a bug.

5. **All tests pass** — `npm run test` exits 0.

6. **`npm run build` succeeds** — dist is produced.

7. **Manual smoke: register → land → sign out → sign in works against real backend.**

---

## Execution Notes

- **Framework version risk:** React 19 and MUI v6 are current stable. If `npm install` reveals peer dep conflicts (most likely with `@mui/material` requiring React <19), downgrade React to 18.x in `package.json` and proceed — the code in this plan is compatible with both. Retry install.
- **Subagent dispatch order:** Tasks 1 and 2 can run back-to-back on the same subagent session since they both touch config. Tasks 3–8 are independent and each in TDD form; each should be its own subagent invocation. Tasks 9–12 build UI — run sequentially in order. Task 13 is a manual verification and should be run by the controller, not a subagent (subagents can't open browsers).
- **Anti-pattern to avoid:** do NOT add feature screens (radar list, observability dashboard, etc.) to this plan or to subagent prompts. Those are Plans 8 and 9. Scope creep here delays the first demoable milestone.
- **MSW note:** the MSW service worker must be initialized for `jsdom` environment (done via `msw/node` → `setupServer`). If tests hit real `/api` URLs, it's because MSW didn't start — check `test/setup.ts` wiring.
