# Dev Radar — Frontend Design Brief for Claude Design

> **How to use this file:** open [claude.ai/design](https://claude.ai/design), start a new project, and paste this entire brief (or upload this file). Iterate until you like how the four screens feel, then export as **standalone HTML** and drop the file back here.

---

## Product (what you're designing for)

**Dev Radar** is a weekly, AI-generated brief for developers. It ingests Hacker News, GitHub Trending, and security advisories, then Claude synthesizes a personalized "radar" — a set of themed summaries with citations — tailored to each user's interest tags (Java, React, security, etc.). The agentic layer can also propose and open migration PRs when CVEs affect the user's repos.

**Audience:** working developers and tech PMs. Not general consumers. They reward density + calm, punish noise + dark-patterns.

**Demo pitch in one line:** *"I built a multi-user agentic system that synthesizes dev news with citations, evals, and an MCP surface."*

This is a **portfolio project** — the visual bar is "looks like a thoughtful production product, not a hackathon demo."

---

## Brand & aesthetic direction

**Minimalist, editorial, Claude-adjacent.** Warm neutrals (not cold grey), typography carrying the weight, a single muted accent, generous whitespace, one subtle elevation level — never stacked shadows. Think Anthropic's own marketing pages, the iA Writer aesthetic, New York Times article pages. The opposite of a Material Design dashboard.

### Tokens to start from (treat as defaults — change if it feels off)

| Token | Value | Use |
|---|---|---|
| `background` | `#faf9f7` | page background — warm off-white |
| `surface` | `#ffffff` | cards, dialogs |
| `text.primary` | `#2d2a26` | body text |
| `text.secondary` | `#6b655e` | metadata, captions |
| `accent` | `#c15f3c` | primary buttons, active states (terracotta) |
| `accent.hover` | `#a84e2f` | |
| `divider` | `#e8e4df` | thin borders |
| `error` | `#b3261e` | |
| `success` | `#2d7a3e` | |

**Fonts**
- UI chrome → **Inter** (400 / 500 / 600)
- Long-form prose (radar summaries, shown in later plans) → **Source Serif Pro** or similar old-style serif
- Code / API keys → **JetBrains Mono**

**Shape**
- Corner radius: 8px default, 4px on tiny chips, 12px on dialogs
- **One** elevation: `0 1px 2px rgba(45,42,38,0.04), 0 1px 1px rgba(45,42,38,0.03)`. No stacked shadows anywhere.
- Content columns: 720px reading width, 960px app shell, 400px for auth forms

**Chrome philosophy**
- No heavy top bar.
- Sidebar is the primary nav, text-only, no icon chrome.
- Buttons are flat — no gradients, no glow, no bounce animations.
- Forms are spacious — 24–32px between fields, not 8px.
- Errors are **inline alerts**, not toast-and-gone.

---

## The four screens

Design all four. They share the token set above.

### 1. Landing (unauthenticated home)

- Single centered column, ~640px max-width.
- Small `overline` eyebrow "Dev Radar" in uppercase, letter-spaced.
- Large headline: **"A weekly brief for what you care about."**
- One paragraph of body text (grey `text.secondary`): *"Personalized radars synthesized from Hacker News, GitHub Trending, and security advisories, with citations you can trust."*
- Two buttons side by side: primary "Create account", outlined "Sign in".
- Nothing else above the fold. No nav bar, no logo stripe, no testimonials, no gradient hero.

### 2. Register

- Narrow centered form, ~400px max-width, vertically centered on the page.
- Heading: "Create your account".
- Three inputs stacked (24–32px apart): Email, Display name, Password.
- Primary button: "Create account" — full width.
- Small footer link: "Have an account? Sign in"
- Inline error alert appears above the first input when server rejects (e.g. "Email already registered").
- Label on top, not floating. Helper text below only when relevant.

### 3. Login

- Same centered form shape as Register.
- Heading: "Sign in".
- Two inputs: Email, Password.
- Primary button: "Sign in" (full width).
- Small footer link: "New here? Create an account"
- Same inline error pattern.

### 4. AppShell (authenticated landing)

Two-column layout, no top bar.

**Left sidebar** (240px fixed width, background same as page, thin right border in `divider`):
- Top: small `overline` "Dev Radar".
- Nav group (text links, stacked, 8–12px apart):
  - Radars
  - Proposals
  - Settings
- *Note for designer:* these three are **disabled placeholders in the current milestone** — render in `text.secondary` with reduced opacity or a subtle "coming soon" hint. They become active in the next design iteration.
- Bottom of sidebar: small user block with display name, email in `caption`, and a text "Sign out" button.

**Main content area**:
- Centered, 720px max-width, left-aligned inside.
- Large h1: **"Welcome, {Name}."** (use "Welcome, Alice." as the demo).
- Below, one `body1` paragraph in `text.secondary`: *"Your radars and proposals will appear here soon."*
- Lots of vertical whitespace. The emptiness is deliberate — it's a foundation screen; real content lands in later iterations.

---

## Implementation constraints (so what you design is actually buildable)

The design will be implemented in **React 19 + TypeScript + MUI v6**, themed via a single `theme.ts`. Please keep these in mind:

- **Work within the token set above.** If you introduce a new color, call it out — I'll add it to the theme intentionally, not sprinkle one-offs.
- **Components that exist as MUI primitives:** `Button`, `TextField`, `Card`, `Alert`, `Divider`, `Stack`, `Box`, `Link`, `Typography`. If your design needs something MUI doesn't give us (fancy range slider, custom tab control), flag it — we can build it, but I want to know the cost.
- **Typography scale** (don't introduce sizes outside this):
  - h1: 32 / 40 / 500
  - h2: 24 / 32 / 500
  - h3: 18 / 26 / 500
  - body: 15 / 24 / 400
  - caption: 13 / 20 / 400
  - overline: 11 / 16 / 500, 0.08em letter-spacing, uppercase
- **Accessibility:** text contrast must clear WCAG AA. Inputs have visible labels (no placeholder-as-label). Focus states on buttons + inputs must be obvious — prefer a 2px `accent` ring over a subtle color shift.
- **Desktop-first**, but the layout should gracefully collapse: sidebar becomes a top bar at <900px, forms stay centered.

---

## Out of scope (don't design these — they're later plans)

- Radar list + detail pages (live SSE streaming, themed cards with citations) — Plan 8
- Interest picker (tag search + category filters) — Plan 8
- Action proposal review (CVE migration PRs) — Plan 8
- Observability dashboard (cost / latency / cache / eval charts) — Plan 9
- API key / MCP settings page — Plan 9

If you feel inspired and sketch any of those, great — but they're not required for this pass.

---

## What I need back from you

- **Standalone HTML export** of the four screens (Claude Design → export → HTML). One file per screen is fine, or one file with all four stacked — whichever the export produces.
- **A list of any token changes** you made (e.g. "shifted accent to `#b85632`", "tightened headline line-height to 1.18", "dropped sidebar border, used whitespace instead"). So I can update `theme.ts` surgically.
- **Any layout choices that diverged from the brief** and why — so I can either follow your lead or push back with a reason.

Paste the HTML (or the path to it) back in chat and I'll regenerate the Plan 7 theme + page components to match exactly what you approved.
