# Dev Radar — Core Product Screens Design Brief (for Claude Design)

> **How to use:** open [claude.ai/design](https://claude.ai/design), paste or upload this file. Iterate visually until the three screens feel right. Export as **standalone HTML** and drop the file back in our chat.

## Context — what already exists

This is the second iteration of Dev Radar's frontend. A previous Claude Design pass defined the tokens + 4 auth/shell screens. **Those tokens are locked and must be reused here exactly.** Only deviate if you have a strong reason — and flag it.

**Product:** Weekly AI-generated brief for developers. User picks interest tags → backend ingests Hacker News + GitHub Trending + CVEs → Claude synthesizes themes → user reads the brief, sees CVE PR proposals, can one-click open a migration PR.

**Audience:** working developers (Java / backend leaning). They reward density + calm, punish noise.

**The brief this covers:** three core product screens — **Interest picker**, **Radar list**, **Radar detail with live AI-streaming themes and CVE PR proposals**.

## Locked tokens (from prior iteration — do not change)

| Token | Value |
|---|---|
| background | `#faf9f7` (warm off-white) |
| surface | `#ffffff` |
| text.primary | `#2d2a26` (Ink) |
| text.secondary | `#6b655e` |
| accent (buttons) | `#2d2a26` (Ink) — monochrome |
| divider | `#e8e4df` |
| error | `#b3261e` + `rgba(179,38,30,0.06)` bg + `rgba(179,38,30,0.2)` border |
| success | `#2d7a3e` |

**Fonts**
- UI: **Inter** (400/500/600)
- **Long-form summaries** (new in this brief): **Source Serif Pro** 17/28 — the reading experience is the editorial hero here
- Code / CVE IDs: **JetBrains Mono**

**Shape**
- Buttons: **pill** (`border-radius: 999`), `padding: 12px 20px`
- Inputs: `border-radius: 8`, `padding: 10px 14px`, focus → 2px Ink ring
- Alert: 8px radius, tinted bg, thin border, inline 16×16 warning icon
- **One** elevation: `0 1px 2px rgba(45,42,38,0.04), 0 1px 1px rgba(45,42,38,0.03)` — no stacked shadows anywhere

**Typography scale**
- h1 (Landing hero): 48px / 1.15 / 500 / `-0.02em` (responsive clamp 32→48)
- h2: 24 / 32 / 500 / `-0.01em`
- Page h1 (not hero): 32 / 40 / 500 / `-0.01em`
- body1: 15 / 24 / 400
- caption: 13 / 20 / 400
- overline: 11 / 16 / 500, `0.08em` letter-spacing, uppercase

**Chrome philosophy** — no heavy shadows, no dense toolbars, typography carries the weight, generous whitespace, single Ink accent.

## Screens to design — three of them

All three are rendered inside the existing app shell (240px left sidebar + centered main content area, 720px read column). The sidebar / top chrome is already defined — you don't need to redesign it. Focus on the **main content area** of each screen.

---

### Screen 1 — Interest Picker (`/app/interests`)

**Purpose:** user selects a handful of technology interest tags that their weekly radar will focus on.

**Data shape:** flat list of interest tags, each with `slug`, `displayName`, `category` (one of `language` / `framework` / `database` / `devops` / `security` / `other`). Users have a subset of these selected. Typical: 80-120 total tags, 5-15 selected.

**What the screen needs**

1. Page header: **h1 "Interests"** + one-line secondary "Pick topics your weekly radar should cover." Scale `32 / 40 / 500 / -0.01em`.
2. **Search input** — filters the visible tag list by display name or slug, case-insensitive. Uses the shared `TextField` primitive (static label above, 8px radius, Ink focus ring).
3. **Selected count + Save button** — shows e.g. "12 selected" next to a primary pill "Save". Disabled when no changes. Probably inline under the search, not sticky — keep it simple.
4. **Category sections** — tags grouped by category, each section:
   - Category header (editorial, `h2` or `overline` — you decide)
   - Wrap-flow of tag chips

**Key design decisions we want Claude Design to make**

- **Tag chip shape** — there are two natural options:
  - (a) Pill checkbox: selected = filled Ink with white text, unselected = outlined divider with Ink text. Symmetric to buttons.
  - (b) "Tag + checkmark" — always outlined, but selected gets a small checkmark icon inserted. More subtle.
  - Pick one. A matters more for dense wrap-flow (instantly see selection state); B has a calmer unselected state.
- **Category headers** — `h2` at 24px feels heavy if a user has many categories. `overline` at 11px uppercase feels right for an index but might be too quiet. Experiment.
- **Search input prominence** — should it be large (hero-like) or small (quiet, since most users will scroll, not search)?
- **"Pick at least 3 to generate your first radar"** — should this be a persistent nudge, a tooltip, or absent? I lean absent unless you have a strong take.
- **Save button placement** — inline under search, or sticky at the bottom? First-time experience vs return-visit editing — these are different UX needs.

Needs to work on 360px–1280px. On narrow, category sections stack naturally; tag wrap reflows.

---

### Screen 2 — Radar List (`/app/radars`)

**Purpose:** user sees their past weekly radars and kicks off a new one.

**Data shape:** list of radars. Each radar: id, status (`READY` / `GENERATING` / `FAILED`), period (start + end timestamps, one week apart), generatedAt, themeCount, tokenCount, generationMs.

**What the screen needs**

1. Page header: **h1 "Radars"** + one-line secondary "Your weekly briefs."
2. **Generate button** — primary pill "Generate new radar", right-aligned with the header. Disabled + tooltip "Pick at least one interest first" when user has no interests yet.
3. **Optional first-time nudge** — if user has no interests, a dismissable banner BELOW the header: *"Pick a few interests to generate your first radar. [Pick interests →]"* The banner must feel different from an error alert — it's invitational, not corrective.
4. **Radar list** — vertical stack of editorial rows, one per radar. Navigable (row clickable).
5. **Empty state** — user has interests but no radars yet: a muted centered block with a CTA button.

**Key design decisions**

- **Radar row shape** — I suggested this in the spec, but you should pick:

  ```
  Apr 20 · 3 themes · 4.2k tokens · 12s            READY
  Week of Apr 13 – Apr 20
  ```

  Is it a card (outlined, subtle bg on hover), an editorial list row (no border, just divider + hover tint), or a table row? **Lean editorial — the brand is reading-forward, not dashboard-y.** But prove me right or wrong.
- **Status chip** — `READY` / `GENERATING` / `FAILED`. Pill with Ink fill for READY, Ink outline with animated pulse for GENERATING, error-tinted for FAILED. How big? Where on the row? Or is the chip overkill — can we just signal state via typography weight / opacity?
- **Metadata density** — 4 pieces in the top line (date, theme count, token count, ms) reads okay at caption size. Too much? Drop one?
- **First-time banner** — how subtle / loud? Compare against something corrective (error alert) and something cheerful (toast). We want invitational.
- **Empty state** — full-bleed centered block, or a small aside under the header?

---

### Screen 3 — Radar Detail with Live SSE (`/app/radars/:id`) — **THE key portfolio screen**

**Purpose:** the reading experience. Themes stream in live from the AI (~20s to generate ~3 themes). CVE action proposals appear as they're produced. User reads + reviews + approves a PR.

**This is the hero screen of the product.** Most of the design polish budget goes here.

**Data shape:**
- Radar metadata: period, status, theme count, generation ms, token count.
- Themes (array): each has title, summary (3-5 sentences of markdown-ish prose), and 2-5 citations. Each citation is a source item: title + url + sometimes author.
- Action proposals (array, 0-3): each is a CVE-fix PR proposal. Status is `PROPOSED` / `EXECUTED` (with prUrl) / `DISMISSED` / `FAILED` (with failureReason).

**Layout**

Two columns inside the main content area:

- **Read column**: 720px, left-aligned, the editorial body. Themes stacked vertically, separated by whitespace.
- **Proposals column**: 280-320px, right side. Collapses under the read column on narrow viewports (<900px).

**What the read column contains, top to bottom**

1. **Radar header**
   - `overline`: "Week of Apr 13 – Apr 20"
   - h1 or h2: a synthetic title like "3 themes · 12s · 4,200 tokens" — or just the date. You pick what reads best.
   - Pulsing "Generating…" caption while `status === GENERATING`; swaps to final metadata when complete.
2. **Themes** — stacked, one per theme. Each theme:
   - Title (h2, sans, 24/32)
   - Summary in **Source Serif Pro 17/28** — this is the key typographic moment. Pretend you're reading a New York Times article.
   - Citations — a line of numbered pills `[1] [2] [3]` below the summary. Hovering shows the source title. Clicking opens the URL.
3. **Between themes** — 48px vertical gap. No dividers. Whitespace is the separator.

**What the proposals column contains**

- Header: `overline` "Action proposals".
- One card per proposal.
- Empty if no proposals — panel hides entirely.

**Proposal card shape** (compact, outlined, monochrome):

```
┌────────────────────────────┐
│  CVE-2024-1234             │  ← overline, mono font for CVE id
│  jackson-databind           │  ← package, caption
│  2.16.1  →  2.17.0          │  ← version bump, slightly larger
│  ─────                      │
│  [Approve]  Dismiss         │  ← primary pill + ghost text
└────────────────────────────┘
```

States:
- **PROPOSED** — buttons live
- **EXECUTED** — replaces buttons with "PR opened →" linking to the GitHub PR URL. Green success tint.
- **DISMISSED** — card dims (opacity 0.5), buttons gone.
- **FAILED** — shows failureReason in a small inline Alert inside the card + "Retry" button.

**Approve flow** — clicking "Approve" opens a confirm modal:

- Modal title: "Open migration PR"
- Prefilled field: "Upgrade to version" with the proposed fix version (editable)
- Body: "This will push a branch to your GitHub repo and open a PR. You can review it before merging."
- Buttons: primary "Open PR", ghost "Cancel"
- On submit: modal shows inline loading state ~2s, then closes when the card status updates.

**Key design decisions for Screen 3**

- **Generating indicator** — we need to convey "AI is working." A pulsing dot feels right, but placement matters. Options: inline in the header caption, as a small chip next to the h1, or as a subtle progress bar under the header. I lean inline caption. **Also: should themes "shimmer in" with a skeleton block before content appears, or just appear instantly when the event fires?** I lean instant-appear with a fade-in animation; skeletons would feel gimmicky here.
- **Serif-vs-sans for summaries** — the spec commits to serif. **Show me what it looks like.** If serif feels wrong (too literary, not techy), we can revisit.
- **Citation pill style** — numbered like `[1]` (academic feel) or named like `spring-io-blog` (self-describing but longer)?
- **Proposals column** — a calm outlined card, or should it feel more like a right-drawer (flush with the edge, subtle bg tint)? Or floating above the content like an annotation?
- **Approve modal** — full-screen overlay feels heavy for a 2-field confirm. A small centered dialog is more appropriate.
- **Success state** — when a proposal executes successfully, what's the moment of satisfaction? Green checkmark + "PR opened" link? A toast?
- **Failure** — if PR creation fails, the failureReason could be long. How do we fit it in a narrow card?
- **Mobile** — proposals panel collapses under the read column. Should it also collapse to a "(1) proposal" summary line that expands on tap? Or always show full cards stacked?

**Animations — a note on restraint.** The brand isn't cute. Fades in 200-300ms ease-out are fine. Bouncing / spring / parallax are not. Pulsing is fine *only* for the generating indicator.

---

## What I need back from Claude Design

- **Standalone HTML** of all three screens. Prefer one file per screen plus a fourth "design-canvas" view showing them side-by-side.
- **For the Radar Detail screen**, show both states: actively streaming (caption says "Generating…", maybe 1-2 themes visible, third slot empty or shimmering) AND complete (all themes visible, no indicator, proposals populated).
- **For the Interest Picker**, show both empty and after a few selections.
- **For the Radar List**, show three states: empty (new user), one radar present, and five+ radars present.
- **A list of token/layout decisions you made** — specifically:
  - Tag chip style chosen
  - Category header level (h2 vs overline)
  - Radar row shape (card / editorial row / table)
  - Citation pill style (numbered vs named)
  - Generating indicator placement
  - Proposals panel style (card / drawer / annotation)
  - Any new tokens introduced (please flag so we can add to the theme)
- **Anything you tried and rejected** — that's useful signal.

## Out of scope (don't design these — they're Plan 9)

- Observability dashboard (cost / latency / cache / eval charts)
- MCP API key management

Paste the HTML export back in chat and I'll regenerate the relevant components to match exactly.
