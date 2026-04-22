# Plan 10 — Radar Quality Overhaul

**Status:** Draft
**Date:** 2026-04-22
**Depends on:** Plans 1-9 (full MVP complete)

---

## 1. Problem

The radar output is generic and unhelpful. Themes like "Java Ecosystem & Frameworks" with summaries like "This theme highlights recent updates and popular projects" deliver zero value. Citations link to irrelevant repos (e.g., a DSA bootcamp under "Java Ecosystem") with no inline context.

**Root cause:** The AI is information-starved. The full pipeline — ingestion, tools, prompt, display — loses information at every layer.

| Layer | What's broken |
|-------|--------------|
| **HN Client** | Fetches title + URL only; ignores points, comment count, story_text |
| **GitHub Trending** | Stores repo path as title ("owner/repo"); ignores description `<p>` tag |
| **GHSA Client** | Stores advisory summary as title; ignores severity, affected package, fix version |
| **SourceItem entity** | No `description` column — content has nowhere to go |
| **SearchItemsTool** | Returns 5 fields (id, external_id, title, url, posted_at) — no description |
| **ScoreRelevanceTool** | Makes a separate AI call to score by title only — wasteful and inaccurate |
| **GetItemDetailTool** | Dumps raw JSON blob — AI must parse opaque payload |
| **System prompt** | Accepts vague category labels as themes |
| **RadarItemDTO** | 4 fields (id, title, url, author) — no description, no source name |
| **CitationPill** | Number pill with hover tooltip — no inline context |

---

## 2. Solution: Full-Stack Content Enrichment

Fix every layer top-to-bottom so richer data flows through the entire pipeline.

---

## 3. Database Layer

### 3.1 Migration 014

Add `description` column to `source_items`:

```sql
ALTER TABLE source_items ADD COLUMN description TEXT;
```

TEXT type, nullable. Holds a clean, human-readable summary extracted by each source client.

### 3.2 SourceItem Entity

Add field:
```java
@Column(columnDefinition = "TEXT")
private String description;
```

With getter/setter.

### 3.3 FetchedItem Record

Add `description` parameter between `title` and `author`:

```java
public record FetchedItem(
    String externalId,
    String url,
    String title,
    String description,  // NEW
    String author,
    Instant postedAt,
    String rawPayload,
    List<String> topics
) {}
```

All existing callers must be updated to pass description.

---

## 4. Ingestion Client Changes

### 4.1 HackerNewsClient

The Algolia API response contains fields we're ignoring.

**Changes:**
- Extract `points` and `num_comments` from the JSON hit
- For text posts (Ask HN, Show HN): extract `story_text` if present, truncate to 500 chars
- For link posts: build description from metadata: "{points} points, {num_comments} comments on Hacker News"
- Pass description to FetchedItem constructor

**Example output:**
- Link post: `description = "438 points, 217 comments on Hacker News"`
- Text post: `description = "Ask HN: Has anyone migrated from Spring Boot 2.x to 3.x? What were the biggest pain points? [truncated]"`

### 4.2 GitHubTrendingClient

The HTML contains a description `<p>` element we're ignoring. The title is currently the repo path.

**Changes:**
- Extract description from `<p>` element within each `article.Box-row` (class varies: look for the first `<p>` child with non-empty text)
- Extract star count from the stars link element
- Set title to the repo name portion only (e.g., "DSA-Bootcamp-Java") not the full path
- Build description: "{repo_description}. {stars} stars on GitHub" or just "{stars} stars on GitHub" if no description
- Keep full path as externalId for dedup

**Example output:**
- title: `"DSA-Bootcamp-Java"`
- description: `"Code samples, assignments, and notes for the Java DSA + interview prep bootcamp. 22.5k stars on GitHub"`

### 4.3 GHSAClient

The advisory JSON contains rich fields we're ignoring.

**Changes:**
- Extract `severity` (critical/high/medium/low)
- Extract first entry from `vulnerabilities` array: `package.name`, `package.ecosystem`, `vulnerable_version_range`, `patched_versions`
- Extract `cve_id` if present
- Build structured description: "{severity} severity. Affects {package} ({ecosystem}) {version_range}. Fix: upgrade to {patched_version}. {cve_id}"

**Example output:**
- title: `"Remote code execution in Spring Framework"` (from summary)
- description: `"CRITICAL severity. Affects org.springframework:spring-core (Maven) < 6.1.5. Fix: upgrade to 6.1.5. CVE-2026-12345"`

---

## 5. IngestionService + TagExtractor

### 5.1 IngestionService

Add `si.setDescription(item.description())` in `ingestOne()`.

### 5.2 TagExtractor

Currently `extract(String title, List<String> topics)`.

Change signature to `extract(String title, String description, List<String> topics)`.

Run keyword matching on both title and description for better tag coverage. A GitHub repo titled "jextract" with description "Extract Java bindings from C headers using Panama FFI" should match the `java` tag even though the title alone doesn't contain "java."

---

## 6. AI Tool Changes

### 6.1 SearchItemsTool

Add `description` to the output JSON for each item:

```json
{
  "id": 42,
  "external_id": "...",
  "title": "DSA-Bootcamp-Java",
  "description": "Code samples and notes for Java DSA bootcamp. 22.5k stars on GitHub",
  "url": "https://github.com/...",
  "posted_at": "2026-04-20T..."
}
```

This gives the AI enough context to judge relevance without needing a separate scoring call.

### 6.2 GetItemDetailTool

- Add `description` to the output JSON
- Remove `raw_payload` from the response — the AI doesn't need opaque JSON when it has structured fields
- Add `source_name` by joining to the `sources` table (value: "hackernews", "github_trending", or "ghsa")

Updated output:
```json
{
  "id": 42,
  "external_id": "...",
  "title": "...",
  "description": "...",
  "url": "...",
  "author": "...",
  "source_name": "github_trending",
  "posted_at": "..."
}
```

### 6.3 Remove ScoreRelevanceTool

Delete `ScoreRelevanceTool.java` entirely.

**Why:** It makes a separate AI API call (using the scoring model) to score items by title only. With descriptions now in SearchItemsTool output, the orchestrator model has enough context to judge relevance itself during its tool loop. This:
- Eliminates one AI API call per radar generation (cost + latency savings)
- Produces better scoring (orchestrator sees full context, not just titles)
- Simplifies the tool registry

Remove from `ToolRegistry` registration. Update orchestrator system prompt to not reference it.

---

## 7. Orchestrator System Prompt Rewrite

The current prompt accepts vague output. The new prompt must enforce quality.

**New system prompt:**

```
You are a tech radar analyst. Given a user's interest tags and a pool of recently ingested items,
identify 3-5 themes that matter to this user THIS WEEK.

QUALITY RULES:
- Every theme title must reference a SPECIFIC technology, event, release, or vulnerability.
  BAD: "Java Ecosystem & Frameworks"
  GOOD: "Spring Boot 3.5 drops native GraalVM support for WebFlux"
  GOOD: "CVE-2026-12345: RCE in Spring Framework < 6.1.5"
- Every summary must explain WHY this matters to the user specifically and WHAT they should do.
- Do not create themes that could apply to any random week. Each theme must be tied to
  something that happened in the last 7 days.
- If an item is a GitHub trending repo, explain what it does and why it's trending,
  not just that it exists.
- If an item is a security advisory, include the severity, affected package, and fix version.

Use the provided tools to search items, fetch details, and investigate. When you encounter a
CVE-related item, call checkRepoForVulnerability to see if the user's repos are affected.

Output a single JSON object with NO PROSE:
{"themes": [
  {"title": "...", "summary": "...", "item_ids": [<source_item ids cited>]},
  ...
]}

Each theme should:
- Have a specific, concrete title under 120 chars.
- Have a summary of 2-4 sentences citing why it matters to THIS user and what action to take.
- Reference 1-5 source_item ids from your search results.
- Do not invent ids — only cite ids you've seen in tool results.
```

---

## 8. Backend DTO Layer

### 8.1 RadarItemDTO

Add `description` and `sourceName`:

```java
public record RadarItemDTO(
    Long id,
    String title,
    String description,
    String url,
    String author,
    String sourceName
) {}
```

### 8.2 RadarApplicationService.get()

When building `RadarItemDTO`, fetch description from SourceItem and resolve sourceName:

```java
itemDtos.add(new RadarItemDTO(
    si.getId(),
    si.getTitle(),
    si.getDescription(),
    si.getUrl(),
    si.getAuthor(),
    resolveSourceName(si.getSourceId())
));
```

`resolveSourceName()` maps source ID to display name using the `sources` table. Cache this mapping since there are only 3 sources.

### 8.3 MCP DTOs

Update `CitationMcpDTO` to include description:

```java
public record CitationMcpDTO(String title, String description, String url) {}
```

---

## 9. Frontend Changes

### 9.1 TypeScript Types

Update `RadarItem` in `types.ts`:

```typescript
export interface RadarItem {
  id: number;
  title: string;
  description: string | null;
  url: string;
  author: string | null;
  sourceName: string;
}
```

### 9.2 Replace CitationPill with SourceCard

Delete `CitationPill.tsx`. Create `SourceCard.tsx` — an inline card showing:

- **Source badge**: Small pill showing "HN" / "GitHub" / "GHSA" with subtle background
- **Title**: The item title, linked to URL
- **Description**: One-line description in secondary text, truncated with ellipsis

Layout: Vertical stack under each theme (not inline pills). Each card is a compact row with source badge, title, and description.

```
┌─────────────────────────────────────────────────────────┐
│ [HN]  Ask HN: Spring Boot 2.x to 3.x migration         │
│       438 points, 217 comments on Hacker News            │
├─────────────────────────────────────────────────────────┤
│ [GHSA] Remote code execution in Spring Framework         │
│        CRITICAL. Affects spring-core < 6.1.5. Fix: 6.1.5│
├─────────────────────────────────────────────────────────┤
│ [GH]  spring-boot-3-migration-guide                      │
│       Step-by-step guide for upgrading. 1.2k stars       │
└─────────────────────────────────────────────────────────┘
```

### 9.3 ThemeCard Update

Replace the citation pills section with a `<SourceCard>` list:

```tsx
{theme.items.length > 0 && (
  <Box sx={{ mt: 3, display: "flex", flexDirection: "column", gap: 0 }}>
    {theme.items.map((item) => (
      <SourceCard key={item.id} item={item} />
    ))}
  </Box>
)}
```

Remove the "SOURCES" overline label and the pills loop.

---

## 10. Files Changed

### New Files
```
backend/src/main/resources/db/changelog/014-add-description-column.xml
frontend/src/components/SourceCard.tsx
```

### Modified Files
```
backend/src/main/java/com/devradar/domain/SourceItem.java
backend/src/main/java/com/devradar/ingest/client/FetchedItem.java
backend/src/main/java/com/devradar/ingest/client/HackerNewsClient.java
backend/src/main/java/com/devradar/ingest/client/GitHubTrendingClient.java
backend/src/main/java/com/devradar/ingest/client/GHSAClient.java
backend/src/main/java/com/devradar/ingest/IngestionService.java
backend/src/main/java/com/devradar/ingest/TagExtractor.java
backend/src/main/java/com/devradar/ai/tools/SearchItemsTool.java
backend/src/main/java/com/devradar/ai/tools/GetItemDetailTool.java
backend/src/main/java/com/devradar/ai/tools/ToolRegistry.java
backend/src/main/java/com/devradar/ai/RadarOrchestrator.java
backend/src/main/java/com/devradar/web/rest/dto/RadarItemDTO.java
backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java
backend/src/main/java/com/devradar/mcp/dto/CitationMcpDTO.java
backend/src/main/resources/db/changelog/db.changelog-master.xml
frontend/src/api/types.ts
frontend/src/components/ThemeCard.tsx
frontend/src/pages/RadarDetailPage.tsx (if CitationPill import needs cleanup)
```

### Deleted Files
```
backend/src/main/java/com/devradar/ai/tools/ScoreRelevanceTool.java
backend/src/test/java/com/devradar/ai/tools/ScoreRelevanceToolTest.java
frontend/src/components/CitationPill.tsx
```

---

## 11. Non-Goals

- No new ingestion sources (fix existing three first)
- No re-ingestion of historical items (new descriptions apply to newly ingested items only)
- No changes to SSE streaming, auth, or radar generation loop structure
- No changes to eval harness (eval can be updated separately)
- No changes to observability dashboard or API keys page
