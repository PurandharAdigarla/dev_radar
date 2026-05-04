# Dev AI Radar — Target Architecture Blueprint (Google ADK)

## Overview

This architecture uses **Google ADK (Agent Development Kit) for Java** as the AI orchestration layer. Instead of a hand-rolled Gemini REST client, ADK provides:
- **ParallelAgent** — 10 topic-specific research agents run concurrently
- **Built-in GoogleSearchTool** — native web grounding, no manual API wiring
- **Output schema validation** — guaranteed structured JSON per agent
- **Session state** — agents share context via `outputKey` + template substitution

---

## 1. Data Model

No changes from previous design — the data model is independent of AI framework choice.

```sql
users                 -- keep as-is
refresh_tokens        -- keep as-is
oauth_states          -- keep as-is

user_topics
  id              BIGINT PK AUTO_INCREMENT
  user_id         BIGINT NOT NULL FK -> users(id)
  topic           VARCHAR(80) NOT NULL
  display_order   INT NOT NULL
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  UNIQUE(user_id, topic)
  INDEX(user_id)

radars
  id                   BIGINT PK AUTO_INCREMENT
  user_id              BIGINT NOT NULL FK -> users(id)
  period_start         DATE
  period_end           DATE
  status               VARCHAR(20) NOT NULL  -- GENERATING | READY | FAILED
  generated_at         TIMESTAMP
  generation_ms        INT
  token_count          INT
  input_token_count    INT
  output_token_count   INT
  error_code           VARCHAR(50)
  error_message        TEXT
  share_token          VARCHAR(64) UNIQUE
  is_public            BOOLEAN DEFAULT FALSE
  created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  INDEX(user_id, generated_at DESC)

radar_themes
  id              BIGINT PK AUTO_INCREMENT
  radar_id        BIGINT NOT NULL FK -> radars(id) ON DELETE CASCADE
  topic           VARCHAR(80)
  title           VARCHAR(500) NOT NULL
  summary         TEXT
  display_order   INT NOT NULL
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  INDEX(radar_id)

radar_theme_sources
  id              BIGINT PK AUTO_INCREMENT
  theme_id        BIGINT NOT NULL FK -> radar_themes(id) ON DELETE CASCADE
  url             VARCHAR(2000) NOT NULL
  title           VARCHAR(500)
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  INDEX(theme_id)
```

---

## 2. Generation Strategy — ADK ParallelAgent

**Decision: 1 LlmAgent per topic, running in parallel via ParallelAgent, followed by a synthesis agent.**

### Why this is better than single-call:

| Aspect | Single call (old) | ParallelAgent (new) |
|--------|-------------------|---------------------|
| Depth per topic | Shallow — model splits attention across 10 | Deep — dedicated search per topic |
| Google Search | One set of results for all topics | Dedicated search per topic |
| Failure isolation | 1 bad topic = entire radar fails | 1 bad topic = 9 still succeed |
| Token efficiency | Wasted on model "deciding" what to search | Each agent has focused instructions |
| Latency | Sequential (1 call but slow) | Parallel (10 calls, wall-clock same as 1) |

### API budget impact:

- 10 research agents + 1 synthesis agent = **11 Gemini calls per radar**
- At 100 users/day: 1100 calls (73% of 1500/day free tier)
- At 50 users/day: 550 calls (37% — comfortable)
- Retry budget: 400 calls/day reserved

For current scale (few users), this is well within budget. If user count grows past ~120, fall back to batching topics (5+5 or 3+3+4 groupings) to reduce calls.

---

## 3. Agent Architecture

### Agent Graph

```
SequentialAgent("radar-pipeline")
  ├── ParallelAgent("topic-research")
  │     ├── LlmAgent("research-frontend-development")    → outputKey: "result_0"
  │     ├── LlmAgent("research-devops")                  → outputKey: "result_1"
  │     ├── LlmAgent("research-rust")                    → outputKey: "result_2"
  │     └── ... (up to 10 topic agents)
  │
  └── LlmAgent("synthesizer")
        reads: {result_0}, {result_1}, ... from session state
        outputs: final structured radar JSON
        → outputKey: "radar_output"
```

### Research Agent (per topic)

Each topic gets a dedicated `LlmAgent` with:
- **GoogleSearchTool** for real-time web research
- **Focused instruction** specific to that topic
- **Output schema** enforcing structured JSON
- **outputKey** to store results in session state

```java
LlmAgent researchAgent = LlmAgent.builder()
    .name("research-" + sanitize(topic))
    .model("gemini-2.5-flash")
    .instruction(buildResearchInstruction(topic, since, previousThemes))
    .tools(GoogleSearchTool.INSTANCE)
    .outputSchema(themeOutputSchema())
    .outputKey("result_" + index)
    .build();
```

**Research instruction per topic:**
```
You are a tech AI research analyst. Research how AI is transforming "{topic}".

Time period: Search for developments from {since} to {today}.

Focus exclusively on:
- New AI tools, products, or features that impact {topic}
- AI-powered alternatives or enhancements to existing tools in {topic}
- Significant announcements, launches, or updates from AI companies relevant to {topic}

DO NOT repeat these previously reported findings:
{previousThemesForTopic}

Use Google Search extensively. Be specific — cite real products, real announcements, real URLs.
If nothing genuinely new happened in this period, say so honestly.

Output a JSON object with your findings.
```

**Output schema per research agent:**
```json
{
  "type": "OBJECT",
  "properties": {
    "topic": { "type": "STRING" },
    "title": { "type": "STRING", "description": "Specific headline, max 120 chars" },
    "summary": { "type": "STRING", "description": "2-4 sentences: what, why it matters, what to do" },
    "sources": {
      "type": "ARRAY",
      "items": {
        "type": "OBJECT",
        "properties": {
          "url": { "type": "STRING" },
          "title": { "type": "STRING" }
        }
      }
    },
    "has_news": { "type": "BOOLEAN", "description": "false if nothing new found" }
  },
  "required": ["topic", "title", "summary", "sources", "has_news"]
}
```

### Synthesizer Agent

Runs after all research agents complete. Reads their outputs from session state and produces the final radar.

```java
LlmAgent synthesizer = LlmAgent.builder()
    .name("synthesizer")
    .model("gemini-2.5-flash")
    .instruction(buildSynthesizerInstruction(topics))
    .outputSchema(radarOutputSchema())
    .outputKey("radar_output")
    .build();
```

**Synthesizer instruction:**
```
You are a radar editor. Below are research findings for each topic.
Your job is to:
1. Rank themes by importance/impact
2. Clean up titles for consistency (max 120 chars, specific and actionable)
3. Ensure summaries are practical — tell the user what matters and what to do
4. Remove any themes where has_news=false (nothing new found)
5. Assign display_order (0-indexed, most important first)

Research findings:
- Topic 0: {result_0}
- Topic 1: {result_1}
...

Output the final radar JSON.
```

**Radar output schema:**
```json
{
  "type": "OBJECT",
  "properties": {
    "themes": {
      "type": "ARRAY",
      "items": {
        "type": "OBJECT",
        "properties": {
          "topic": { "type": "STRING" },
          "title": { "type": "STRING" },
          "summary": { "type": "STRING" },
          "sources": { "type": "ARRAY", "items": { ... } },
          "display_order": { "type": "INTEGER" }
        }
      }
    }
  },
  "required": ["themes"]
}
```

---

## 4. Token Budget Analysis (ADK approach)

| Component | Tokens per agent | × Agents | Total |
|---|---|---|---|
| Research agent system prompt | ~300 | × 10 | 3,000 |
| Research agent previous context (per-topic) | ~500 | × 10 | 5,000 |
| Research agent output | ~200 | × 10 | 2,000 |
| Synthesizer input (all results) | ~2,500 | × 1 | 2,500 |
| Synthesizer output | ~1,500 | × 1 | 1,500 |
| **Total per radar** | | | **~14,000** |

vs. old single-call: ~7,350 tokens. ADK uses ~2× tokens but delivers significantly deeper results per topic because each agent does dedicated Google Search.

At 100 users/day: 1.4M tokens — still within Gemini's rate limits (1M tokens/minute, so 100 concurrent users would need ~14 seconds total).

---

## 5. Component Design (ADK-based)

### Package Structure

```
com.devradar
  domain/
    User, Radar, RadarStatus, RadarTheme, RadarThemeSource, UserTopic
  repository/
    UserRepository, RadarRepository, RadarThemeRepository,
    RadarThemeSourceRepository, UserTopicRepository
  agent/                             -- NEW: ADK agent definitions
    RadarAgentFactory.java           -- builds the agent graph per request
    TopicResearchInstruction.java    -- instruction builder for research agents
    SynthesizerInstruction.java      -- instruction builder for synthesizer
    RadarOutputParser.java           -- extracts themes from session state
    AgentConfig.java                 -- Spring @Configuration for ADK beans
  radar/
    RadarService.java                -- lifecycle: createPending, markReady, markFailed
    RadarGenerationService.java      -- @Async: runs ADK pipeline, persists results
    RadarApplicationService.java     -- daily limit + trigger
    RadarQueryService.java           -- read-side: list, detail, public
  topic/
    UserTopicService.java            -- topic CRUD + AI validation
    TopicValidationAgent.java        -- ADK agent for topic validation
  web/rest/
    TopicController.java
    RadarController.java
    PublicRadarController.java
    AuthController.java              -- keep existing
  config/
    AsyncConfig.java, SecurityConfig.java
  security/
    JwtAuthenticationFilter, JwtTokenProvider,
    RateLimitFilter, RateLimitService
  observability/
    DailyMetricsCounter.java
```

### Key Classes

**`RadarAgentFactory`** — builds the agent graph dynamically based on user's topics:

```java
@Component
public class RadarAgentFactory {

    public SequentialAgent buildRadarPipeline(List<String> topics, LocalDate since,
                                              List<PreviousRadar> previousRadars) {
        List<LlmAgent> researchers = new ArrayList<>();
        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            List<String> prevThemes = extractPreviousThemesFor(topic, previousRadars);

            researchers.add(LlmAgent.builder()
                .name("research-" + i)
                .model("gemini-2.5-flash")
                .instruction(TopicResearchInstruction.build(topic, since, prevThemes))
                .tools(GoogleSearchTool.INSTANCE)
                .outputSchema(themeSchema())
                .outputKey("result_" + i)
                .build());
        }

        ParallelAgent research = ParallelAgent.builder()
            .name("topic-research")
            .subAgents(researchers)
            .build();

        LlmAgent synthesizer = LlmAgent.builder()
            .name("synthesizer")
            .model("gemini-2.5-flash")
            .instruction(SynthesizerInstruction.build(topics))
            .outputSchema(radarSchema())
            .outputKey("radar_output")
            .build();

        return SequentialAgent.builder()
            .name("radar-pipeline")
            .subAgents(research, synthesizer)
            .build();
    }
}
```

**`RadarGenerationService`** — runs the pipeline asynchronously:

```java
@Service
public class RadarGenerationService {

    private final RadarAgentFactory agentFactory;
    private final RadarService radarService;
    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeSourceRepository sourceRepo;

    @Async("radarGenerationExecutor")
    public void runGeneration(Long radarId, Long userId, List<String> topics) {
        long t0 = System.currentTimeMillis();
        try {
            List<PreviousRadar> previous = loadPreviousRadars(userId);
            LocalDate since = findLastRadarDate(userId);

            SequentialAgent pipeline = agentFactory.buildRadarPipeline(topics, since, previous);
            InMemoryRunner runner = new InMemoryRunner(pipeline);
            Session session = runner.sessionService()
                .createSession("devradar", String.valueOf(userId))
                .blockingGet();

            Content trigger = Content.fromParts(Part.fromText("Generate radar"));
            runner.runAsync(String.valueOf(userId), session.id(), trigger,
                    RunConfig.builder().build())
                .blockingSubscribe();

            // Extract results from session state
            Map<String, Object> state = session.state();
            RadarOutput output = RadarOutputParser.parse(state);

            persistThemes(radarId, output.themes());
            long elapsed = System.currentTimeMillis() - t0;
            int tokens = countTokensFromEvents(session);
            radarService.markReady(radarId, elapsed, tokens, 0, 0);

            purgeOldRadars(userId);
        } catch (Exception e) {
            radarService.markFailed(radarId, "GENERATION_FAILED", e.getMessage());
        }
    }
}
```

**`TopicValidationAgent`** — validates user topics using ADK:

```java
@Component
public class TopicValidationAgent {

    private final LlmAgent validationAgent;
    private final InMemoryRunner runner;

    public TopicValidationAgent() {
        this.validationAgent = LlmAgent.builder()
            .name("topic-validator")
            .model("gemini-2.5-flash")
            .instruction("""
                Validate if each topic is a legitimate tech skill/domain.
                VALID: programming languages, frameworks, dev practices, tool categories.
                INVALID: gibberish, offensive, non-tech, overly vague.
                Normalize casing and expand abbreviations.
                """)
            .outputSchema(validationSchema())
            .outputKey("validation_result")
            .build();
        this.runner = new InMemoryRunner(validationAgent);
    }

    public List<ValidatedTopic> validate(List<String> topics) {
        Session session = runner.sessionService()
            .createSession("validation", "system")
            .blockingGet();

        Content input = Content.fromParts(
            Part.fromText("Validate these topics:\n" + String.join("\n", topics)));

        runner.runAsync("system", session.id(), input, RunConfig.builder().build())
            .blockingSubscribe();

        return parseValidationResult(session.state());
    }
}
```

---

## 6. ADK Spring Configuration

```java
@Configuration
public class AgentConfig {

    @Bean
    public RadarAgentFactory radarAgentFactory() {
        return new RadarAgentFactory();
    }

    @Bean
    public TopicValidationAgent topicValidationAgent() {
        return new TopicValidationAgent();
    }
}
```

**Maven dependency:**
```xml
<dependency>
    <groupId>com.google.adk</groupId>
    <artifactId>google-adk</artifactId>
    <version>1.2.0</version>
</dependency>
```

**Environment variable (already exists):**
```
GOOGLE_AI_API_KEY=<your-key>
```

ADK's Gemini integration picks up the API key from environment or you configure it explicitly via `BaseLlm`.

---

## 7. Sequence: Generate Radar (ADK flow)

```
Frontend → POST /api/radars
  RadarApplicationService:
    1. getTopics(userId) — fail 400 if empty
    2. checkDailyLimit(userId) — fail 409 if already generated
    3. checkInProgress(userId) — fail 409 if GENERATING exists
    4. radarService.createPending(userId) → radar (status=GENERATING)
    5. @Async radarGenerationService.runGeneration(radarId, userId, topics)
  → return 202 { radarId, status: "GENERATING" }

Frontend → polls GET /api/radars/{id}/status every 3s

RadarGenerationService (async thread):
    1. loadPreviousRadars(userId)
    2. findLastRadarDate(userId)
    3. agentFactory.buildRadarPipeline(topics, since, previous)
    4. InMemoryRunner.runAsync() executes:
       a. ParallelAgent kicks off 10 research LlmAgents concurrently
       b. Each agent: instruction → Gemini call with GoogleSearchTool → structured output → session state
       c. All 10 complete (parallel, ~15-30s wall clock)
       d. SequentialAgent advances to synthesizer
       e. Synthesizer reads {result_0}..{result_9} from state, produces final radar JSON
    5. RadarOutputParser extracts themes from session.state().get("radar_output")
    6. persistThemes + persistSources
    7. radarService.markReady(radarId, elapsed, tokens)
    8. purgeOldRadars(userId)

Frontend sees status=READY → fetches GET /api/radars/{id}
```

---

## 8. Error Handling with ADK

**Per-topic failure isolation:**
- If 1 research agent fails (Gemini 429, timeout), the other 9 still produce results
- ParallelAgent merges event streams; individual failures don't kill siblings
- The synthesizer works with whatever results are in session state
- If `result_3` is missing from state, synthesizer simply omits that topic

**Stale generation:**
- Same `@PostConstruct` failStaleGenerations() pattern
- Cloud Run idle timeout covers the ADK execution window

**Quota exhaustion:**
- If 429 hits during parallel execution, individual agents fail gracefully
- Radar marked READY with partial results (7/10 themes = acceptable)
- Only mark FAILED if synthesizer itself fails or 0 themes produced

**Retry within ADK:**
- ADK's `LlmAgent` has `maxSteps` — limits internal retries
- Set `maxSteps(3)` on research agents (allows 2 retries per topic)
- Global timeout: `RadarGenerationService` wraps with `blockingSubscribe(timeout: 180s)`

---

## 9. Async on Cloud Run (unchanged)

- Keep `@Async("radarGenerationExecutor")` pattern
- ADK runs synchronously within the async thread (`.blockingSubscribe()`)
- Cloud Run config: `--min-instances 1 --max-instances 3 --timeout 300`
- Safety net: `failStaleGenerations()` at `@PostConstruct`

---

## 10. API Contract (unchanged)

Same as before — the API layer is independent of whether ADK or raw REST is used internally.

```
GET    /api/topics
PUT    /api/topics
POST   /api/radars              → 202
GET    /api/radars
GET    /api/radars/{id}
GET    /api/radars/{id}/status
PATCH  /api/radars/{id}/share
GET    /api/public/radars/{shareToken}
```

---

## 11. What ADK Replaces

| Old code | Replaced by |
|----------|-------------|
| `GeminiAiClient.java` (raw REST) | ADK's built-in Gemini integration |
| `AiClient.java` interface | Not needed — ADK abstracts this |
| `AiMessage`, `AiToolCall`, `AiToolResult` | ADK's `Content`, `Part`, `Event` |
| `TopicRadarOrchestrator.java` (prompt + parse) | `RadarAgentFactory` + ADK agents |
| `MultiProviderAiClient.java` | Not needed — single provider via ADK |
| `ToolDefinition`, `ToolRegistry` | ADK's `GoogleSearchTool`, `FunctionTool` |
| Manual JSON extraction (`extractJson()`) | ADK's `outputSchema` validation |
| Manual grounding metadata parsing | ADK handles via `GoogleSearchTool` |

---

## 12. What to Keep (not replaced by ADK)

- All domain entities, repositories, Liquibase migrations
- `RadarService` (lifecycle management)
- `RadarApplicationService` (daily limit, trigger)
- `RadarQueryService` (read-side)
- `UserTopicService` (CRUD, but validation moves to ADK agent)
- All REST controllers
- Security layer (JWT, rate limiting)
- `AsyncConfig`, `SecurityConfig`
- Frontend (React + Redux + MUI)

---

## 13. Migration Plan (updated for ADK)

### Phase 1: Add ADK dependency
- Add `google-adk` to `pom.xml`
- Verify it builds and doesn't conflict with existing dependencies

### Phase 2: Schema migration (changelog 030)
- Add `topic VARCHAR(80)` to `radar_themes`
- Create `radar_theme_sources` table
- Same as before

### Phase 3: Build ADK agents
1. Create `agent/RadarAgentFactory.java`
2. Create `agent/TopicResearchInstruction.java`
3. Create `agent/SynthesizerInstruction.java`
4. Create `agent/RadarOutputParser.java`
5. Create `agent/AgentConfig.java`
6. Create `topic/TopicValidationAgent.java`
7. Update `RadarGenerationService` to use `RadarAgentFactory` + `InMemoryRunner`
8. Update `UserTopicService` to use `TopicValidationAgent`

### Phase 4: Delete old AI layer
- Delete `ai/GeminiAiClient.java`
- Delete `ai/AiClient.java`
- Delete `ai/MultiProviderAiClient.java`
- Delete `ai/AiMessage.java`, `AiResponse.java`, `AiToolCall.java`, `AiToolResult.java`
- Delete `ai/RadarOrchestrator.java`
- Delete `ai/tools/` (entire package)
- Delete `radar/TopicRadarOrchestrator.java`
- Delete `radar/TopicRadarGenerationService.java`

### Phase 5: Delete legacy code (same as before)
- All ingestion, GitHub, teams, badges, engagement, eval code
- Legacy domain entities and repositories

### Phase 6: Schema cleanup (changelog 031)
- Drop legacy tables

### Phase 7: Config cleanup
- Remove `devradar.ai.*` config properties (ADK uses env vars / its own config)
- Keep only: `GOOGLE_AI_API_KEY` env var

---

## 14. File Structure (Target)

```
com.devradar
  agent/
    RadarAgentFactory.java
    TopicResearchInstruction.java
    SynthesizerInstruction.java
    RadarOutputParser.java
    AgentConfig.java
  topic/
    UserTopicService.java
    TopicValidationAgent.java
  radar/
    RadarService.java
    RadarGenerationService.java
    RadarApplicationService.java
    RadarQueryService.java
  domain/
    User.java
    Radar.java
    RadarStatus.java
    RadarTheme.java
    RadarThemeSource.java
    UserTopic.java
  repository/
    UserRepository.java
    RadarRepository.java
    RadarThemeRepository.java
    RadarThemeSourceRepository.java
    UserTopicRepository.java
  web/rest/
    TopicController.java
    RadarController.java
    PublicRadarController.java
    AuthController.java
  config/
    AsyncConfig.java
    SecurityConfig.java
  security/
    JwtAuthenticationFilter.java
    JwtTokenProvider.java
    RateLimitFilter.java
    RateLimitService.java
  observability/
    DailyMetricsCounter.java
```

---

## 15. Advantages of ADK Architecture

1. **10× deeper research** — each topic gets dedicated Google Search, not shared
2. **Failure isolation** — one topic failing doesn't kill the radar
3. **Schema-validated output** — no more manual JSON parsing/extraction hacks
4. **Parallel execution** — 10 agents run concurrently, wall-clock time = 1 agent
5. **Cleaner code** — ~60% less AI orchestration code (no REST client, no message building, no tool dispatch)
6. **Future extensibility** — add custom FunctionTools (e.g., check npm registry, fetch GitHub trending) without changing architecture
7. **Testability** — mock at the agent level, not at the HTTP level
