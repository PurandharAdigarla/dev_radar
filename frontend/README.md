# Dev Radar Frontend

React + TypeScript SPA for Dev Radar.

## Dev

```bash
npm install
npm run dev      # http://localhost:5173
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
- MUI v6 with custom monochrome theme (see `docs/superpowers/design-assets/2026-04-21-plan7-frontend/`)
- Vitest + React Testing Library + MSW
