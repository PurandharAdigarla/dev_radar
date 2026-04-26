# Dev Radar — Repositioning Plan

**Date:** 2026-04-26
**Status:** Approved by CTO, Architect, VP Product, Head of Revenue

## The Pivot

**From:** "A weekly AI brief for what you care about" (AI newsletter)
**To:** "Know what changed in your stack before it breaks your build" (developer intelligence platform)

**USP:** Newsletters inform. Dev Radar acts on your actual codebase.

---

## Positioning & Pitch

**Headline:** "Your codebase has 3 vulnerable dependencies. We found them."

**Subheadline:** Dev Radar scans your repos, matches CVEs against your actual dependency tree, and opens fix PRs — all in one weekly radar.

**vs daily.dev/TLDR:** "They inform. We act. Dev Radar doesn't just tell you about a CVE — it opens the PR."
**vs Dependabot:** "Dependabot bumps versions. Dev Radar explains WHY you should upgrade, what the community says, then opens the PR."
**vs Snyk:** "Snyk costs $52/seat and needs a security team. Dev Radar gives individual developers the same CVE coverage for $9/mo, plus tech intelligence Snyk doesn't touch."

---

## Pricing

| | Free | Pro ($9/mo) | Team ($14/seat/mo, min 3) |
|---|---|---|---|
| Radars/week | 1 | Unlimited | Unlimited |
| Sources | HN + GH Trending | + GHSA + RSS + Releases + DEP_RELEASE | All |
| Dependency scanning | 3 repos, 1 scan/week | 20 repos, daily | 50 repos/seat, 4x/day |
| Auto-PR | View only | Full execute | Execute + team audit log |
| MCP server | No | Yes | Yes |
| AI model | Gemini Flash | Sonnet 4.6 | Sonnet 4.6 |

**Unit economics:** AI COGS ~$0.02-0.06/user/month. Gross margin 93-95%.

---

## Technical Implementation Plan

### Workstream 1: Security & Stability (Week 1)

| Task | Files | Complexity |
|---|---|---|
| Rate limiting (Bucket4j + Redis) | Create `RateLimitConfig.java`, `RateLimitFilter.java`. Modify `SecurityConfig.java`, `application.yml` | M |
| Fix `/api/internal/**` auth | Create `TriggerSecretFilter.java`. Modify `SecurityConfig.java` — remove `permitAll()`, add filter | S |
| Fix `/mcp/**` auth | Modify `SecurityConfig.java` — change to `.authenticated()`, existing `ApiKeyAuthenticationFilter` handles it | S |
| AI budget guardrails | Modify `MultiProviderAiClient.java` — check daily cost before provider calls. Add `estimatedCostUsd()` to `DailyMetricsCounter` | S |

### Workstream 2: Push Delivery (Week 2)

| Task | Files | Complexity |
|---|---|---|
| Notification preferences table | Create migration `021-notification-preferences.xml`, entity `NotificationPreference.java`, repository | S |
| Email renderer (Thymeleaf) | Create `EmailRenderer.java`, `radar-email.html` template | M |
| Slack Block Kit renderer | Create `SlackRenderer.java` | S |
| SendGrid integration | Create `EmailSender.java` interface, `SendGridEmailSender.java`. Add `sendgrid-java` to pom.xml | M |
| Slack webhook sender | Create `SlackWebhookSender.java` | S |
| Weekly auto-gen + delivery job | Create `WeeklyDigestJob.java`, `DigestDeliveryService.java`. Modify `RadarApplicationService` — add `createForUser(userId)` | M |
| Notification API | Create `NotificationResource.java` — GET/PUT prefs, POST test-email, POST test-slack | S |
| Settings UI | Create `NotificationsPage.tsx`, `notificationApi.ts`. Modify `AppShell` nav, `App.tsx` routes | M |

### Workstream 3: Feedback Loop (Week 3-4)

| Task | Files | Complexity |
|---|---|---|
| Engagement events table | Create migration `022-engagement-tracking.xml`, entity `EngagementEvent.java`, repository | S |
| Engagement API | Create `EngagementResource.java` — POST event, GET summary | S |
| Engagement profile service | Create `EngagementProfileService.java` — queries events, builds `UserEngagementProfile` | M |
| Feed into orchestrator | Modify `RadarOrchestrator.java` — append engagement context to system prompt. Modify `RadarApplicationService` — inject profile | M |
| Theme interaction UI | Modify `ThemeCard.tsx` — add thumbs up/down, share. Create `engagementApi.ts`. Modify `SourceCard.tsx` — track clicks | M |
| Personalization eval | Modify `EvalService.java` — add PERSONALIZATION category. Modify `LlmJudge.java` — add `scorePersonalization()` | S |

### Workstream 4: B2B Teams (Week 5-6)

| Task | Files | Complexity |
|---|---|---|
| Teams schema | Create migration `023-teams-schema.xml`. Create entities: `Team.java`, `TeamMember.java`, `TeamMemberId.java`, `TeamRadar.java` | M |
| Team radar generation | Create `TeamRadarService.java` — merge member interests, generate. Modify `RadarApplicationService` — add `createForTeam()` | M |
| Team API | Create `TeamResource.java` — CRUD team, members, radars, `GET /dependencies`, `GET /vulnerabilities` | L |
| Team auth | Modify `JwtTokenProvider.java` — add `teamIds` claim. Create `TeamAuthorizationService.java` | S |
| Plan enforcement | Create `PlanEnforcer.java` — check team plan limits on radar gen, member add | S |
| Team frontend | Create `TeamDashboardPage.tsx`, `TeamSettingsPage.tsx`, `teamApi.ts`. Modify `AppShell`, `App.tsx` | L |

### Workstream 5: Repositioned Experience (Week 5-6)

| Task | Files | Complexity |
|---|---|---|
| Sample radar (anonymous) | Create `SampleRadarResource.java`, `SampleRadarRefreshJob.java`. Modify `SecurityConfig` | S |
| GitHub onboarding scan | Create `UserDependencyResource.java` — `GET /me/dependencies`, `POST /me/dependencies/scan`. Create onboarding page in frontend | M |
| Auto-suggest interests | Create `GET /api/users/me/suggested-interests` endpoint. Modify `InterestPickerPage.tsx` — pre-populate from scan results | M |
| Shareable radars | Create migration `024-shareable-radars.xml`. Modify `Radar.java`, `RadarResource.java` — share token. Create `SharedRadarPage.tsx` | M |
| Landing page redesign | Modify `Landing.tsx` — repositioned copy, sample radar embed, "How it works" section, live stats | M |
| Dashboard home | Create `DashboardPage.tsx` — dependency health, radar status, stats, activity. Create `GET /me/dependency-summary`, `GET /me/stats` endpoints | M |
| CVE theme styling | Modify `ThemeCard.tsx` — red border + shield icon for GHSA themes, inline proposal card | M |

### New Database Migrations

```
021-notification-preferences.xml
022-engagement-tracking.xml
023-teams-schema.xml
024-shareable-radars.xml
```

---

## Go-to-Market Plan

### Month 1-3: First 1,000 Users

| Channel | Tactic | Expected Signups |
|---|---|---|
| Show HN | "Dev Radar — AI that scans your repos and opens PRs when CVEs hit your dependencies." Post Tuesday 11am ET with 30s screen recording of auto-PR flow | 300 |
| Dev Twitter/Bluesky | 5 posts/week: radar screenshots with real findings, Monday CVE roundup threads | 200/mo |
| Reddit | r/java (PomParser angle), r/webdev (package.json), r/devops (CVE remediation), r/programming (architecture) | 150 |
| Discord/Slack | Theo's T3, Fireship, Spring community, Java Champions — #showcase posts | 100 |
| SEO/Content | "Every CVE affecting Spring Boot in April 2026," "Gemini vs Claude benchmarks" from observability data | 50/mo compound |

### Month 3-9: 1K to 10K

- **Shareable radar links** — every shared radar is a landing page with "Get yours free" CTA
- **Email digest** — weekly re-engagement, subject: "3 CVEs affect your repos this week"
- **README badge** — "Scanned by Dev Radar" SVG linking to public radar
- **Open-source** — extract `DependencyFileParser` (PomParser, PackageJsonParser, GradleParser) as standalone lib
- **Partnerships** — JetBrains Marketplace plugin, Cursor/Claude MCP directory listing, GitHub Marketplace app
- **Conference talks** — "Building AI agents that act, not just summarize" at SpringOne, QCon

### Month 6+: B2B Expansion

- Individual user becomes team champion after sharing 3+ radars to colleagues at same email domain
- In-app prompt: "Your org has 47 unpatched CVEs across 12 repos"
- Team plan unlocks org-wide dependency dashboard + audit log
- Self-serve up to 10 seats; sales-assisted above

---

## Revenue Milestones

| Month | Signups | Paid Users | MRR | ARR |
|---|---|---|---|---|
| 3 | 1,000 | 50 | $450 | $5,400 |
| 6 | 2,500 | 200 | $2,200 | $26,400 |
| 9 | 5,000 | 500 | $5,500 | $66,000 |
| 12 | 8,000 | 900 | $12,000 | $144,000 |

---

## Product Experience

### Onboarding Flow (90 seconds to value)

1. **Landing page** → "Connect GitHub" (primary CTA, `repo` scope)
2. **Scanning screen** → "Scanning your repositories..." — live progress as DependencyScanJob runs
3. **Auto-populated interests** → Tags pre-selected from detected ecosystems (MAVEN→Java, NPM→JavaScript)
4. **First radar generates** → SSE streaming, themes appear one by one
5. **Aha moment** → CVE theme appears with red border, showing user's actual repo + dependency + "Open PR to fix"

### Radar Detail Page (Redesigned)

- **CVE themes:** Red left-border, shield icon, "Security Advisory" label, inline ProposalCard with "Open PR" button
- **Dependency themes:** Blue left-border, "Dependency Update" label
- **General themes:** Current unadorned style
- **Interactions:** Thumbs up/down on each theme (feeds personalization), Share button
- **Citations:** Show 2, "Show N more" to expand

### Dashboard Home (New)

1. Dependency health card: "3 packages have updates, 1 CVE detected"
2. Radar status: "Your latest radar is ready" or "Generating..."
3. Stats: themes/month, CVEs caught, PRs opened
4. Recent activity feed

---

## Risk Mitigation

| Risk | Mitigation |
|---|---|
| Free alternatives add AI summaries | They won't scan pom.xml. Double down on "acts on your code" positioning |
| GitHub ships "GitHub Intelligence" | We're multi-source (HN, RSS, community). Ship Slack/Linear integrations for stickiness |
| Conversion rate < 2% | Reduce free tier to 1 radar/month, gate GHSA themes behind Pro, add 14-day trial |
| Anthropic fallback burns budget | Daily budget cap in MultiProviderAiClient ($5/day default) |

---

## Critical Path

```
Week 1: Security & Stability (prerequisite for everything)
    ↓
Week 2: Push Delivery (email/Slack digest)
    ↓                          ↓
Week 3-4: Feedback Loop    Week 3-4: Landing Redesign + Onboarding
    ↓                          ↓
Week 5-6: B2B Teams + Shareable Radars + Dashboard
```

Workstreams 2 and 3 are parallelizable. Week 5-6 work depends on Week 3-4 completion.

---

## Summary of New Artifacts

| Type | Count |
|---|---|
| Backend Java classes (new) | ~18 |
| Backend Java classes (modified) | ~10 |
| Liquibase migrations | 4 |
| Frontend pages/components (new) | ~8 |
| Frontend files (modified) | ~6 |
| Maven dependencies (new) | 2 (Bucket4j, SendGrid) |
| Email template | 1 |
