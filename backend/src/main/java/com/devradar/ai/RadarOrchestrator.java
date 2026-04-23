package com.devradar.ai;

import com.devradar.ai.tools.ToolContext;
import com.devradar.ai.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RadarOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(RadarOrchestrator.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
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

        CITATION PRIORITY:
        - When items from GH_RELEASES or GH_STARS exist for a technology, ALWAYS prefer them over
          GH_TRENDING items for the same project. Release items link to specific changelogs;
          trending items only link to repo homepages with no context about what changed.
        - ARTICLE items are blog posts and official documentation from authoritative sources.
          They provide valuable context for WHY a change matters. When building a theme about a
          release or trending project, prefer to include an ARTICLE citation alongside the release
          item to give the user both the changelog and expert analysis.
        - Build themes around releases and version updates when available. Trending repos should
          only be cited when no release item exists for that project.

        Use the provided tools to search items by tag, fetch item details, and investigate.
        When you encounter a CVE-related item, call checkRepoForVulnerability to see if
        the user's repos are affected.

        Output a single JSON object with NO PROSE around it:
        {"themes": [
          {"title": "...", "summary": "...", "item_ids": [<source_item ids cited>]},
          ...
        ]}

        Each theme should:
        - Have a specific, concrete title under 120 chars.
        - Have a summary of 2-4 sentences citing why it matters to THIS user and what action to take.
        - Reference 1-5 source_item ids from your search results.
        - Do not invent ids — only cite ids you've seen in tool results.
        """;

    private final AiClient ai;
    private final ToolRegistry tools;
    private final String model;
    private final int maxIterations;
    private final int maxTokens;

    public RadarOrchestrator(AiClient ai, ToolRegistry tools,
                             @Value("${anthropic.orchestrator-model}") String model,
                             @Value("${anthropic.max-tool-iterations}") int maxIterations,
                             @Value("${anthropic.max-tokens-per-call}") int maxTokens) {
        this.ai = ai; this.tools = tools; this.model = model;
        this.maxIterations = maxIterations; this.maxTokens = maxTokens;
    }

    public RadarOrchestrationResult generate(List<String> userInterests, List<Long> candidateItemIds, ToolContext ctx) {
        String userMsg = """
            User interests: %s
            Candidate item ids (from last 7 days, pre-filtered to user's tags): %s

            Use the tools to look up titles, fetch full details, check the user's repos for vulnerabilities, and produce the final themes JSON.
            """.formatted(userInterests, candidateItemIds);

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.userText(userMsg));

        int totalIn = 0, totalOut = 0;
        String lastText = "";

        for (int iter = 0; iter < maxIterations; iter++) {
            AiResponse resp = ai.generate(model, SYSTEM_PROMPT, messages, tools.definitions(), maxTokens);
            totalIn += resp.inputTokens();
            totalOut += resp.outputTokens();
            if (resp.text() != null && !resp.text().isBlank()) lastText = resp.text();
            LOG.info("orchestrator iter={} stopReason={} toolCalls={} textLen={}", iter, resp.stopReason(), resp.toolCalls().size(), lastText.length());

            if (resp.toolCalls().isEmpty() || "end_turn".equals(resp.stopReason())) break;

            messages.add(new AiMessage("assistant", resp.text(), resp.toolCalls(), List.of()));

            List<AiToolResult> results = new ArrayList<>();
            for (AiToolCall call : resp.toolCalls()) {
                String out = tools.dispatch(call.name(), call.inputJson(), ctx);
                boolean isError = out != null && out.contains("\"error\"");
                results.add(new AiToolResult(call.id(), out, isError));
                LOG.debug("tool dispatched name={} resultLen={}", call.name(), out == null ? 0 : out.length());
            }
            messages.add(AiMessage.userToolResults(results));
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
            LOG.warn("failed to parse final radar JSON; returning empty themes. err={}", e.toString());
        }

        return new RadarOrchestrationResult(themes, totalIn, totalOut);
    }

    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) return "{}";
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return text.substring(start, i + 1); }
        }
        return "{}";
    }

    public record RadarOrchestrationResult(List<RadarOrchestrationTheme> themes, int totalInputTokens, int totalOutputTokens) {}
    public record RadarOrchestrationTheme(String title, String summary, List<Long> itemIds, int displayOrder) {}
}
