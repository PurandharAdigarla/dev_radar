# Dev Radar — UI Redesign Brief

## Product Overview

Dev Radar is a SaaS tool that generates weekly AI-powered technology radars for developers. Users pick topics (e.g., "Spring Boot", "React", "MySQL"), and the system uses Google's Gemini AI with web search to produce curated, actionable reports of what happened in those domains — releases, CVEs, AI tools, ecosystem shifts. Radars are generated in real-time via Server-Sent Events (SSE), showing live search activity as agents research each topic.

**Stack:** React + MUI + Redux Toolkit (frontend), Java/Spring Boot (backend), MySQL (database), Gemini 2.5 Flash (AI).

**Live demo:** https://devradar-414645578716.us-central1.run.app

---

## User Flow

```
Landing → Register → Pick Topics → Generate Radar → View Radar (streaming) → Explore themes/repos → Share
                                       ↓
                              Dashboard (returning users)
```

---

## Pages & Screens to Design

### 1. Landing Page (`/`)
**Purpose:** Convert visitors to sign-ups.
**Content:**
- Hero: headline, subheadline, CTA buttons (Sign Up / Sign In)
- Sample radar preview (live demo data with themes and sources)
- Feature highlights (AI-powered research, real-time streaming, GitHub repo discovery)
- Footer with links

**Data shown:** Sample RadarDetail with 3-4 themes, each with title + summary + source links.

---

### 2. Login (`/login`)
**Purpose:** Email/password authentication.
**Content:**
- Email field, Password field
- "Sign in" button
- Error alert (invalid credentials)
- Link to Register page
- Wrapped in centered AuthCard (max 400px)

---

### 3. Register (`/register`)
**Purpose:** Account creation.
**Content:**
- Email, Display name, Password fields
- "Create account" button
- Error alerts (email taken, generic error)
- Link to Login page
- Same AuthCard layout as Login

---

### 4. App Shell (sidebar/topbar layout)
**Purpose:** Main navigation wrapper for all authenticated pages.
**Desktop (>=900px):** 240px left sidebar with:
- "DEV RADAR" logo/title
- Nav items: Dashboard, Radars, Topics, Settings, Notifications
- Active state: bold + subtle background
- Pulsing dot on "Radars" when generation is in progress
- Bottom: user name, email, "Sign out" link, "Public dashboard" link

**Mobile (<900px):** Top bar with:
- Logo, horizontal nav items, user/sign-out

**Main content area** renders the active page via `<Outlet />`.

---

### 5. Dashboard (`/app/dashboard`)
**Purpose:** Home screen for returning users.
**Content:**
- **Onboarding checklist** (new users only): Pick topics, Generate first radar, Explore radar — with checkmarks and progress
- **New items alert:** "[N] new items since your last radar" with Generate Radar CTA (shown only if items available)
- **4-card grid:**
  - Your Topics: chip list of selected topics, or empty state with "Add Topics" CTA
  - Radar Status: latest radar date + "View radars" link, or empty state with "Generate" CTA
  - Your Stats: 3 numbers — Radars count, Themes count, Engagements count
  - Quick Actions: 3 buttons — Generate Radar, Manage Topics, Settings

**Data:** `UserStats { radarCount, themeCount, engagementCount, latestRadarDate, newItemsSinceLastRadar }`, `Topic[]`

---

### 6. Radar List (`/app/radars`)
**Purpose:** View all generated radars, create new ones.
**Content:**
- Page header with "Generate new radar" button (disabled if no topics, with alert message)
- List of radar rows, each showing:
  - Date generated (e.g., "May 4, 2026") or "Generating..."
  - Status badge: READY (green pill), GENERATING (with pulse dot), FAILED (red pill)
  - Stats: "[N] themes · [X]k tokens · [Y]s"
  - Clickable → navigates to radar detail
- Empty state if no radars

**Data:** `PageResponse<RadarSummary>` — paginated list, sorted by generatedAt desc.

---

### 7. Radar Detail (`/app/radars/:id`) — THE CORE SCREEN
**Purpose:** View a single radar with all themes, sources, and repo recommendations.

#### States:

**A. Generating (streaming):**
- Header: "Generating..." with pulsing dot, progress counter ("3 of ~5 themes")
- **Activity Feed** showing real-time agent progress:
  - Groups by agent (e.g., "research_0", "repos_1")
  - Each group shows: search queries tried, search results found (title + domain)
  - Latest group expanded, previous collapsed with result count badge
  - Two sections: "Researching topics" and "Discovering repos"
- Theme cards appear as they complete (fade-in animation)
- Skeleton placeholders for pending themes

**B. Ready:**
- Header: date, "Your Dev AI Radar" title, stats ("4 themes · 14.0k tokens · 50.2s")
- "Share this radar" button → copies share URL to clipboard
- **Theme cards** in display order, each containing:
  - Theme title (h2)
  - Summary text (serif font, 2-5 sentences)
  - Security themes highlighted: red left border + shield icon + tinted background (if title/summary matches CVE/vulnerability/security keywords)
  - Source list: clickable links with source badge (HN, GitHub, Release, GHSA, Article, Dependency), title, optional description, optional EPSS score badge for CVEs
  - Engagement buttons: thumbs up, thumbs down, share (with server-persisted state)
- **Repo section** ("Repos worth checking out"): 2-column responsive grid of repo cards, each showing:
  - Repo name (clickable → GitHub)
  - Category badge (MCP Server, Agent Skill, Agent Framework, Dev Tool, Prompt Library)
  - Description
  - "Why notable" explanation
  - Topic tag

**C. Failed:**
- Error alert with backend error message
- "This radar failed to generate" fallback

**Data:** `RadarDetail { themes: RadarTheme[], repos: RepoRecommendation[], generationMs, tokenCount, errorCode, errorMessage }`

**SSE events during streaming:** `AgentProgressEvent { agent, phase, searchQueries, searchResults }`, `ThemeCompleteEvent`, `RadarCompleteEvent`, `RadarFailedEvent`

---

### 8. Topics (`/app/topics`)
**Purpose:** Configure which technology topics to track.
**Content:**
- Text input + "Add" button
- Chip list of current topics (with delete X)
- Suggestion chips (predefined: "MCP Servers", "Claude Code Skills", "Agentic Development", "LLM Frameworks", etc.)
- Counter: "5/15 topics"
- Save button

**Validation:** Max 15, no duplicates, non-empty strings.

---

### 9. Settings (`/app/settings`)
**Purpose:** Account overview.
**Content:**
- Account section: display name, email (read-only)
- Topics section: count + link
- API Keys section: link to `/app/settings/api-keys`

---

### 10. API Keys (`/app/settings/api-keys`)
**Purpose:** Manage API keys for programmatic access.
**Content:**
- "Create key" button
- List of keys: name, prefix (monospace), scope badge (READ/WRITE), created date, last used date, Revoke button
- Create dialog: name input, scope selector (READ/WRITE), shows full key ONCE after creation (must copy)
- Revoke confirmation dialog
- Empty state

**Data:** `ApiKeySummary[]`, `ApiKeyCreateResponse { key (full, shown once) }`

---

### 11. Notifications (`/app/notifications`)
**Purpose:** Configure email digest.
**Content:**
- Email Digest toggle (on/off)
- If on: email address input, day-of-week dropdown, hour (UTC) dropdown
- Save button
- "Send test email" button (disabled if digest off)
- Success/error feedback

**Data:** `NotificationPreference { emailEnabled, emailAddress, digestDayOfWeek, digestHourUtc }`

---

### 12. Shared Radar (`/radar/shared/:shareToken`)
**Purpose:** Public view of a shared radar (no auth required).
**Content:** Same layout as Radar Detail (Ready state) but without engagement buttons or share button. Read-only view.

---

### 13. Public Observability Dashboard (`/observability`)
**Purpose:** Public transparency dashboard showing system health.
**Content:**
- Summary cards: radars generated (24h), total tokens, p50/p95 latency, cache hit rate, eval scores
- Line charts (7-day or 30-day): radars/day, tokens/day, latency trends
- No auth required

**Data:** `ObservabilitySummary`, `MetricsDay[]`

---

## Design Tokens (current)

| Token | Value | Notes |
|-------|-------|-------|
| Primary color | `#2d2a26` (dark ink/brown) | Used for buttons, active text |
| Background | `#faf9f7` (warm off-white) | Page background |
| Paper | `#ffffff` | Card/surface background |
| Text primary | `#2d2a26` | Body text |
| Text secondary | `#6b655e` | Meta text, labels |
| Divider | `#e8e4df` | Borders, separators |
| Error | `#b3261e` | Failed states, CVE highlights |
| Success | `#2d7a3e` | Ready badges |
| Body font | Inter, system-ui | All UI text |
| Serif font | Source Serif Pro | Theme summaries |
| Mono font | JetBrains Mono | Code, API keys, stats |
| Button radius | 999px (pill) | All buttons |
| Card radius | 8px | Inputs, cards |
| Base font | 15px (0.9375rem) | body1 |

---

## Component Inventory

### Atoms
- **Button** — pill-shaped, dark fill primary / outlined secondary
- **TextField** — label above, 8px radius, no MUI notch
- **Alert** — colored bar with icon (error/success)
- **StatusTag** — pill badge: READY (green), GENERATING (pulse dot), FAILED (red)
- **PulseDot** — tiny animated green circle (8px default)
- **Chip** — topic tags, category badges

### Molecules
- **AuthCard** — centered container (max 400px) for login/register forms
- **RadarRow** — clickable list item: date + stats + status badge
- **SourceCard** — link row: source badge + title + description + optional EPSS badge
- **RepoCard** — card: repo name + category badge + description + why notable + topic
- **ThemeCard** — article: h2 title + serif summary + source list + engagement buttons
- **ThemeSkeleton** — loading placeholder mimicking ThemeCard shape
- **ActivityFeed** — collapsible timeline of agent search progress
- **CreateKeyDialog** — modal for API key creation
- **OnboardingChecklist** — step list with checkmarks

### Organisms
- **AppShell** — sidebar (desktop) / topbar (mobile) + main content outlet
- **RepoSection** — "Repos worth checking out" heading + 2-col grid of RepoCards
- **PageHeader** — h1 + optional subtitle + optional right-side action slot

---

## Key User Interactions

1. **Generate radar:** Click button → POST creates radar → navigate to detail page → SSE stream shows live progress → themes appear one by one → completion with stats
2. **Engage with theme:** Thumbs up/down (toggle, persisted to server), share link copy
3. **Manage topics:** Add/remove chips, click suggestions, max 15
4. **Share radar:** Button generates unique share URL, copies to clipboard
5. **Create API key:** Modal form → server returns full key once → must copy immediately

---

## Responsive Breakpoints

| Breakpoint | Layout |
|------------|--------|
| < 900px | Top bar navigation, single column, stacked cards |
| >= 900px | 240px sidebar + main content area |

---

## Notes for Designer

- **Teams feature is hidden** from nav but routes exist — don't design for it now
- **GitHub OAuth login is removed** — email/password only
- **The radar detail page is the hero screen** — this is where users spend 90% of their time. Make it exceptional.
- **Streaming state is critical UX** — the 30-90 second generation wait needs to feel engaging, not frustrating. The activity feed showing "AI is searching..." with real query terms is the key differentiator.
- **Security themes** (CVEs, vulnerabilities) should stand out visually — they're the most actionable content.
- **Source cards** are links to external articles/releases — they need to feel clickable and scannable.
- **Repo recommendations** are GitHub repos — show them as discoverable cards, not a dense list.
- **Dark mode** is not currently implemented but would be a nice addition if the design system supports it.
- **The serif font** (Source Serif Pro) is used only for theme summaries — it creates a reading-focused feel similar to Substack/Medium for the main content.
