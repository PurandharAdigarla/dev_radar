# Dev Radar — Corporate Review: Features to Pull Clients

**Date:** April 27, 2026
**Reviewed by:** CEO Strategy, CTO Engineering, Product Manager, Growth & Sales agents

---

## Executive Summary

Dev Radar sits in a unique gap between content aggregation (daily.dev, Feedly) and security tooling (Snyk, Dependabot). The core moat is the pipeline: **scan repos → detect relevant changes → explain with AI context → auto-fix**. No competitor unifies all four. The path to revenue is evolving from a "content tool" to an "intelligence platform."

**Positioning line:** *"daily.dev shows you what's popular. Dev Radar shows you what matters to YOUR stack."*

---

## Top 15 Features — Cross-Agent Consensus

Ranked by how many agents flagged it and overall impact × effort score.

### Tier 1: Ship in Weeks 1-4 (Critical Path)

| # | Feature | What | Why it pulls clients | Effort |
|---|---------|------|---------------------|--------|
| 1 | **Auto-generate first radar on signup** | After onboarding step 2, immediately trigger radar generation and redirect to the streaming view — don't dump users on an empty list | PM found the current flow requires 6-7 actions before any value. The "aha moment" is too far from signup. Fixing this alone could double activation rate | 1 day |
| 2 | **Auto-scheduled weekly radar** | Cron job generates each user's radar on their chosen day/time, so it's ready when they check in | All 4 agents flagged this. Currently users must manually click "Generate" every week — most forget. This is the #1 retention lever | 3 days |
| 3 | **Public shareable radar pages** | One-click share a read-only radar to Twitter/LinkedIn/Slack with CTA "Get your own — free" | CEO + Growth agents both identified this as the primary viral loop. Developers share interesting content. Every share = qualified lead | 2 days |
| 4 | **Official blog RSS seed data** | Seed 20+ authoritative feeds (spring.io, react.dev, go.dev, rust-lang.org) mapped to interest tags | CTO flagged: radar themes are weak without authoritative ARTICLE sources. Currently only HN + GitHub + GHSA. Cheap config change | 1 day |
| 5 | **"New since your last radar" dashboard badge** | Show count of new CVEs, releases, and trending items since last visit | PM identified empty dashboard as a retention killer. This gives users a reason to come back | 2 days |

### Tier 2: Ship in Weeks 5-8 (Conversion Engine)

| # | Feature | What | Why it pulls clients | Effort |
|---|---------|------|---------------------|--------|
| 6 | **Stack Drift Alerts** | Compare user's dependency versions against ecosystem trends. "73% of React projects migrated to Vite. You're still on Webpack 4" | CEO agent: "transforms Dev Radar from nice-to-read into need-to-have." Gate behind PRO | 5 days |
| 7 | **Dependabot Alerts API integration** | Replace substring CVE matching with real GitHub Dependabot alerts (transitive deps, CVSS scores) | CTO found current vuln detection is brittle string matching. This makes security credible | 3 days |
| 8 | **EPSS Score integration** | Add exploit probability scores to CVE cards via FIRST.org API. "CRITICAL CVSS + 90% EPSS = emergency" | CTO: CVSS tells severity, EPSS tells likelihood. This is what security teams actually act on | 2 days |
| 9 | **CVE auto-fix PR expansion** | Extend beyond pom.xml to package.json, build.gradle, requirements.txt | PM found this is a strong PRO conversion trigger. Currently narrow scope limits perceived value | 8 days |
| 10 | **Free-tier "your stack had 3 CVEs this week" email** | Stripped-down weekly notification for free users — just counts, not content. Creates urgency to log in | PM + Growth: free users currently have ZERO re-engagement. This is the pull mechanic | 2 days |

### Tier 3: Ship in Weeks 9-16 (Enterprise Features)

| # | Feature | What | Why it pulls clients | Effort |
|---|---------|------|---------------------|--------|
| 11 | **Team Radar + Shared Intelligence** | Aggregate across all team members' repos into one view. Prioritized by blast radius | CEO: "Engineering managers don't want 8 individual radars. They want one view." Core TEAM value prop | 8 days |
| 12 | **Security Posture Dashboard** | Org-wide CVE count, unpatched %, MTTR trend, priority queue | CEO + CTO: "your exposure decreased 15% this month" is a slide engineering directors put in front of VPs | 10 days |
| 13 | **GitHub Action for CI** | `devradar/check-deps` — posts PR comments with CVE info, blocks merge on CRITICAL | CTO: "CI integration is the #1 driver of enterprise seat expansion." Makes Dev Radar a gate, not a dashboard | 8 days |
| 14 | **Technology Adoption Radar (ThoughtWorks-style)** | Auto-generate Adopt/Trial/Assess/Hold radar from actual repo usage across the team | CEO: "Every engineering org wants a tech radar. Most never build one. Auto-generating it is a massive time-saver for CTOs" | 10 days |
| 15 | **SBOM Generation (CycloneDX/SPDX)** | Export dependency data as compliance-ready SBOM | CTO: US Executive Order 14028 requires SBOMs. You already have the data; just formatting | 3 days |

---

## Pricing Restructure (CEO + PM Consensus)

Current gating on **radar count** is wrong — nobody generates 50 radars/month. Gate on **depth of intelligence** instead.

| | FREE | PRO ($9/mo) | TEAM ($15/seat/mo, min 5) | ENTERPRISE (custom) |
|---|------|-------------|--------------------------|---------------------|
| Radars | 1/week | Unlimited | Unlimited | Unlimited |
| Auto-schedule | No (manual only) | Yes | Yes | Yes |
| Sources | 3 built-in | + 10 custom RSS | + 20 custom | Unlimited |
| Interests | 10 | Unlimited | Unlimited | Unlimited |
| History | Last 4 weeks | Full + search | Full + search | Full + export |
| Stack Drift Alerts | No | Yes | Yes | Yes |
| CVE Fix PRs | No | Yes | Yes | Yes + SLA |
| Team Radar | No | No | Yes | Yes |
| Security Dashboard | No | No | Yes | Yes + MTTR |
| Tech Adoption Radar | No | No | No | Yes |
| API/Webhooks | Read-only | Full | Full | Full + SLA |
| SSO/SAML | No | No | No | Yes |

**Key insight:** Gate the *workflow* features (auto-schedule, fix PRs, alerts). Keep the *awareness* free (radar viewing, CVE notifications). Let everyone see the problems; charge for the tools to fix them.

---

## Growth Playbook (Solo Dev Edition)

### Viral Loops
1. **Shareable radar pages** — `devradar.dev/u/purandhar` with "Get your own" CTA
2. **GitHub README badge** — dynamic SVG: "0 critical CVEs this week — tracked by Dev Radar"
3. **CVE advisory pages** — `devradar.dev/cve/CVE-2026-XXXX` with "scan your repos" CTA (fear-driven sharing)
4. **Invite-to-extend trial** — 14 days base + 7 per teammate invited (max 45 days, Dropbox playbook)

### Programmatic SEO (Zero Manual Effort)
Auto-publish weekly per-stack radar pages from existing AI output:
- `devradar.dev/radar/java/2026-w17`
- `devradar.dev/radar/react/2026-w17`

Targets long-tail queries like "new java libraries april 2026" or "react security vulnerabilities this week."

### First 10 Enterprise Teams
1. LinkedIn outreach to DevSecOps leads (10/day): "Free security scan of your GitHub org"
2. Free Team tier for open-source projects (maintainers = influencers)
3. Lightning talks at Java/Spring/React meetups with live demo
4. Public "security audit" lead magnet: paste GitHub org URL → free scan → capture email → follow up

### Blog Posts to Write
| Title | Target |
|-------|--------|
| "How We Use Agentic AI to Generate Tech Radars" | HN front page bait |
| "The 10 Most Impactful Java CVEs of 2026 (So Far)" | Security-conscious devs |
| "I Built an MCP Server That Feeds Security Alerts Into Your IDE" | IDE power users |
| "What 10,000 GitHub Repos Tell Us About Stack Trends" | Broad developer audience |

---

## Critical Activation Fix (PM Deep Dive)

The PM agent traced the actual code and found a **critical drop-off bug**: the onboarding stepper's step 3 ("Generate Radar") does NOT trigger radar generation — it just redirects to the empty radar list page after a 1.5s delay. New users land on an empty page with "Generate your first radar" as another manual action. This is the single biggest leak in the funnel.

**Fix:** After interests are applied in onboarding, immediately call `createRadar()` via the API, redirect to `/app/radars/{id}` so the user sees the SSE streaming skeleton — the most impressive part of the product — as their first authenticated experience.

---

## The Moat (CTO Assessment)

> "The thing that's genuinely unique is the combination of: (1) dependency scanning from the user's actual repos, (2) agentic AI that can call tools to check those repos against incoming advisories, (3) auto-PR execution to fix what it finds. That pipeline — scan, detect, explain, fix — doesn't exist as a unified product. Snyk scans and detects but doesn't explain context. Dependabot fixes but doesn't contextualize. Newsletter products explain but don't scan your code."

**Highest-leverage investment:** Make the scan→detect→explain→fix pipeline deeper: transitive deps, lockfile parsing, EPSS scoring, changelog analysis, and code-aware impact assessment. Everything else (Slack bot, GraphQL, SSO) is distribution and packaging. The pipeline is the product.

---

## 90-Day Execution Plan

| Week | Focus | Deliverables |
|------|-------|-------------|
| 1-2 | **Activation** | Auto-generate first radar on onboarding, sample radar on landing page, dashboard checklist replacing empty states |
| 3-4 | **Distribution** | Public radar pages, GitHub README badge, Plausible analytics, first 2 blog posts, GitHub Marketplace listing |
| 5-6 | **Retention** | Auto-scheduled weekly radars, free-tier CVE email, "new since last radar" badge, RSS blog seed data |
| 7-8 | **Security depth** | Dependabot Alerts API, EPSS scores, transitive dep parsing, SBOM export |
| 9-10 | **Conversion** | Stripe integration for PRO, stack drift alerts (PRO-gated), fix PR expansion, custom RSS sources (PRO-gated) |
| 11-12 | **Enterprise** | Team radar dashboard, security posture view, GitHub Action for CI, first 3 paying teams |

**Revenue target:** 3 teams × 5 seats × $15 = **$225 MRR by day 90**. Modest, but proves willingness to pay. That proof unlocks everything else.

---

## Metrics That Matter

| Metric | Target (90 days) |
|--------|-----------------|
| Signups/week | 50 by month 3 |
| Activation rate (signup → first radar) | >40% |
| Weekly Active Users | 30% of total signups |
| Radar-to-share rate | >5% |
| Team creation rate | >10% of activated users |
| Mean Time to Remediate (CVE) | Trackable baseline established |
