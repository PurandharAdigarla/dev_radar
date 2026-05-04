package com.devradar.ai;

import com.devradar.ai.tools.ToolContext;
import com.devradar.ai.tools.ToolRegistry;
import com.devradar.service.EngagementProfileService;
import com.devradar.service.EngagementProfileService.UserEngagementProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RadarOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(RadarOrchestrator.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are a senior tech journalist writing for a developer community (like dev.to or InfoQ).
        Given a user's interest tags and a pool of recently ingested items, identify 5-7 themes
        that matter to this user THIS WEEK and write each as a detailed, well-structured article.

        QUALITY RULES:
        - Every theme title must reference a SPECIFIC technology, event, release, or vulnerability.
          BAD: "Java Ecosystem & Frameworks"
          GOOD: "Spring Boot 3.5 drops native GraalVM support for WebFlux"
          GOOD: "CVE-2026-12345: RCE in Spring Framework < 6.1.5"
        - Do not create themes that could apply to any random week. Each theme must be tied to
          something that happened in the last 7 days.

        ARTICLE WRITING RULES — THIS IS CRITICAL:
        Each theme summary must be a DETAILED ARTICLE of 300-500 words, structured like a
        professional dev community post. Use this structure:

        1. OPENING PARAGRAPH: Start with the news hook — what happened, who announced it, when.
           Give the reader immediate context.

        2. TECHNICAL DETAILS: Go deep. Explain the technical specifics — what changed in the API,
           what the new architecture looks like, how the vulnerability works, what the benchmark
           numbers show. Include version numbers, package names, configuration changes. Developers
           read this to LEARN, not just to be aware.

        3. WHY IT MATTERS: Explain the broader significance. How does this fit into the ecosystem?
           What problem does it solve? What was the community's reaction? Reference discussions,
           blog posts, or expert opinions you found via web search.

        4. WHAT TO DO: End with concrete, actionable guidance for the reader. Should they upgrade?
           Migrate? Watch and wait? Include specific steps when possible (e.g. "bump the version
           in your pom.xml" or "check your CSP headers").

        Use paragraph breaks (newlines) between sections for readability. Write in a direct,
        technically precise style — no marketing fluff. Use concrete numbers, version strings,
        and technical terms. The reader is an experienced developer.

        - If an item is a GitHub trending repo, explain its architecture, key features, how it
          compares to alternatives, and why it gained traction this week.
        - If an item is a security advisory, include severity score, attack vector, affected
          versions, fix version, and whether exploits exist in the wild.

        CITATION PRIORITY:
        - DEP_RELEASE items (new versions of packages the user depends on) are HIGHEST priority.
        - GH_RELEASES items preferred over GH_TRENDING for the same project.
        - ARTICLE items provide expert analysis — include alongside release items.
        - GH_TRENDING only when no release item exists for that project.

        RESEARCH DEPTH:
        Use the provided tools to search items by tag and fetch item details.
        You also have access to Google Search — use it extensively to research each theme deeply.
        For EVERY theme, search for:
        - Official announcements and changelogs
        - Community reactions and discussions (HN, Reddit, Twitter)
        - Expert blog posts and analysis
        - Benchmark comparisons or migration guides if relevant
        Weave this research into your article to give the reader a complete, authoritative picture.
        When you encounter a CVE-related item, call checkRepoForVulnerability.

        Output a single JSON object with NO PROSE around it:
        {"themes": [
          {"title": "headline under 120 chars", "summary": "Full article text 300-500 words with paragraph breaks...", "item_ids": [142, 155]}
        ]}

        CROSS-WEEK CONTINUITY:
        - If previous radar summaries are provided, track evolving stories and reference progression.
        - Do NOT repeat themes verbatim. If a topic evolved, reference what changed since last week.
        - Surface trends across multiple weeks when patterns emerge.
        - Highlight resolutions to previously flagged issues.

        Each theme should:
        - Have a specific, concrete title under 120 chars.
        - Have a detailed article summary of 300-500 words as described above.
        - Reference 1-5 source_item ids from your search results.
        - Do not invent ids — only cite ids you've seen in tool results.
        """;

    private final AiClient ai;
    private final ToolRegistry tools;
    private final EngagementProfileService engagementProfileService;
    private final String model;
    private final int maxIterations;
    private final int maxTokens;
    private final int maxDurationSeconds;
    private final int maxTokensPerRadar;

    public RadarOrchestrator(AiClient ai, ToolRegistry tools,
                             EngagementProfileService engagementProfileService,
                             @Value("${devradar.ai.orchestrator-model}") String model,
                             @Value("${devradar.ai.max-tool-iterations}") int maxIterations,
                             @Value("${devradar.ai.max-tokens-per-call}") int maxTokens,
                             @Value("${devradar.ai.max-duration-seconds:120}") int maxDurationSeconds,
                             @Value("${devradar.ai.max-tokens-per-radar:50000}") int maxTokensPerRadar) {
        this.ai = ai; this.tools = tools;
        this.engagementProfileService = engagementProfileService;
        this.model = model;
        this.maxIterations = maxIterations; this.maxTokens = maxTokens;
        this.maxDurationSeconds = maxDurationSeconds;
        this.maxTokensPerRadar = maxTokensPerRadar;
    }

    public RadarOrchestrationResult generate(List<String> userInterests, List<Long> candidateItemIds, ToolContext ctx) {
        return generate(userInterests, candidateItemIds, ctx, null, List.of());
    }

    public RadarOrchestrationResult generate(List<String> userInterests, List<Long> candidateItemIds, ToolContext ctx, Long userId) {
        return generate(userInterests, candidateItemIds, ctx, userId, List.of());
    }

    public RadarOrchestrationResult generate(List<String> userInterests, List<Long> candidateItemIds,
                                             ToolContext ctx, Long userId, List<PreviousRadarSummary> previousRadars) {
        StringBuilder userMsgBuilder = new StringBuilder();
        userMsgBuilder.append("User interests: ").append(userInterests).append("\n");
        userMsgBuilder.append("Candidate item ids (from last 7 days, pre-filtered to user's tags): ").append(candidateItemIds).append("\n\n");
        userMsgBuilder.append("Use the tools to look up titles, fetch full details, check the user's repos for vulnerabilities, and produce the final themes JSON.\n");

        if (!previousRadars.isEmpty()) {
            userMsgBuilder.append("\n## Previous Radars (most recent first)\n");
            for (var prev : previousRadars) {
                userMsgBuilder.append("\n### Week of ").append(prev.periodLabel()).append("\n");
                for (var theme : prev.themes()) {
                    userMsgBuilder.append("- **").append(theme.title()).append("**: ").append(theme.summary()).append("\n");
                }
            }
        }

        String userMsg = userMsgBuilder.toString();
        String systemPrompt = buildSystemPrompt(userId);

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.userText(userMsg));

        int totalIn = 0, totalOut = 0;
        String lastText = "";
        List<AiResponse.GroundingSource> allGroundingSources = new ArrayList<>();
        Instant start = Instant.now();

        for (int iter = 0; iter < maxIterations; iter++) {
            if (Duration.between(start, Instant.now()).toSeconds() > maxDurationSeconds) {
                LOG.warn("orchestrator wall-clock timeout after {}s, returning partial result with {} iterations",
                        maxDurationSeconds, iter);
                break;
            }
            if (totalIn + totalOut > maxTokensPerRadar) {
                LOG.warn("orchestrator per-radar token budget exceeded: {} tokens (limit {}), stopping at iter={}",
                        totalIn + totalOut, maxTokensPerRadar, iter);
                break;
            }
            AiResponse resp = ai.generate(model, systemPrompt, messages, tools.definitions(), maxTokens, true);
            allGroundingSources.addAll(resp.groundingSources());
            totalIn += resp.inputTokens();
            totalOut += resp.outputTokens();
            if (resp.text() != null && !resp.text().isBlank()) lastText = resp.text();
            LOG.info("orchestrator iter={} stopReason={} toolCalls={} textLen={}", iter, resp.stopReason(), resp.toolCalls().size(), lastText.length());

            if (resp.toolCalls().isEmpty() && "end_turn".equals(resp.stopReason())) break;

            if (!resp.toolCalls().isEmpty()) {
                messages.add(new AiMessage("assistant", resp.text(), resp.toolCalls(), List.of()));

                List<AiToolResult> results;
                if (resp.toolCalls().size() == 1) {
                    AiToolCall call = resp.toolCalls().get(0);
                    String out = tools.dispatch(call.name(), call.inputJson(), ctx);
                    results = List.of(new AiToolResult(call.id(), out, isToolError(out)));
                    LOG.debug("tool dispatched name={} resultLen={}", call.name(), out == null ? 0 : out.length());
                } else {
                    results = resp.toolCalls().parallelStream()
                        .map(call -> {
                            String out = tools.dispatch(call.name(), call.inputJson(), ctx);
                            LOG.debug("tool dispatched name={} resultLen={}", call.name(), out == null ? 0 : out.length());
                            return new AiToolResult(call.id(), out, isToolError(out));
                        })
                        .toList();
                }
                messages.add(AiMessage.userToolResults(results));
            }
        }

        // If the model never produced text output, nudge it for the JSON
        if (lastText.isBlank() && Duration.between(start, Instant.now()).toSeconds() < maxDurationSeconds) {
            LOG.info("orchestrator: model produced no text, sending nudge for final JSON");
            messages.add(AiMessage.userText(
                "Now produce the final JSON output with your themes. Output ONLY the JSON object, no prose: {\"themes\": [...]}"));
            AiResponse nudge = ai.generate(model, systemPrompt, messages, List.of(), maxTokens);
            totalIn += nudge.inputTokens();
            totalOut += nudge.outputTokens();
            if (nudge.text() != null && !nudge.text().isBlank()) lastText = nudge.text();
            LOG.info("orchestrator nudge: textLen={}", lastText.length());
        }

        List<RadarOrchestrationTheme> themes = new ArrayList<>();
        try {
            JsonNode root = JSON.readTree(extractJsonObject(lastText));
            JsonNode arr = root.get("themes");
            if (arr != null && arr.isArray()) {
                int order = 0;
                for (JsonNode t : arr) {
                    String title = t.path("title").asText();
                    String summary = t.path("summary").asText();
                    List<Long> ids = new ArrayList<>();
                    for (JsonNode i : t.path("item_ids")) ids.add(i.asLong());
                    themes.add(new RadarOrchestrationTheme(title, summary, ids, order++));
                }
            }
        } catch (Exception e) {
            LOG.warn("failed to parse final radar JSON; returning empty themes. lastText='{}' err={}", lastText.length() > 200 ? lastText.substring(0, 200) : lastText, e.toString());
        }

        return new RadarOrchestrationResult(themes, totalIn, totalOut, allGroundingSources);
    }

    private String buildSystemPrompt(Long userId) {
        if (userId == null) return SYSTEM_PROMPT;
        try {
            UserEngagementProfile profile = engagementProfileService.buildProfile(userId);
            if (profile.totalInteractions() == 0) return SYSTEM_PROMPT;
            StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);
            sb.append("\n\n## User Preferences\n");
            if (!profile.thumbsUpThemes().isEmpty()) {
                sb.append("The user has indicated they like: ").append(profile.thumbsUpThemes()).append(". ");
            }
            if (!profile.thumbsDownThemes().isEmpty()) {
                sb.append("They dislike: ").append(profile.thumbsDownThemes()).append(". ");
            }
            sb.append("Prioritize similar topics and deprioritize disliked ones.");
            return sb.toString();
        } catch (Exception e) {
            LOG.warn("failed to build engagement profile for userId={}: {}", userId, e.getMessage());
            return SYSTEM_PROMPT;
        }
    }

    private static boolean isToolError(String output) {
        if (output == null || output.isBlank()) return false;
        try {
            JsonNode node = JSON.readTree(output);
            return node.isObject() && node.has("error") && node.size() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) return "{}";
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return text.substring(start, i + 1); }
        }
        return "{}";
    }

    public record RadarOrchestrationResult(List<RadarOrchestrationTheme> themes, int totalInputTokens,
                                             int totalOutputTokens, List<AiResponse.GroundingSource> webSources) {}
    public record RadarOrchestrationTheme(String title, String summary, List<Long> itemIds, int displayOrder) {}
    public record PreviousRadarSummary(String periodLabel, List<PreviousTheme> themes) {}
    public record PreviousTheme(String title, String summary) {}
}
