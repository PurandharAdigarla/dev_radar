package com.devradar.ai;

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
        identify 3-5 themes that genuinely matter to this user. Use the provided tools to refine your candidate
        set. When you are done investigating, output a single JSON object with NO PROSE around it:

        {"themes": [
          {"title": "...", "summary": "...", "item_ids": [<source_item ids cited>]},
          ...
        ]}

        Each theme should:
        - Have a title under 100 chars.
        - Have a summary 1-3 sentences citing why it matters to this user specifically.
        - Reference 1-5 source_item ids from your search results.
        Do not invent ids — only cite ids you've seen in tool results.
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

    public RadarOrchestrationResult generate(List<String> userInterests, List<Long> candidateItemIds) {
        String userMsg = """
            User interests: %s
            Candidate item ids (from last 7 days, pre-filtered to user's tags): %s

            Use the tools to look up titles, score relevance, fetch full details, and produce the final themes JSON.
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

            if (resp.toolCalls().isEmpty() || "end_turn".equals(resp.stopReason())) break;

            List<AiToolResult> results = new ArrayList<>();
            for (AiToolCall call : resp.toolCalls()) {
                String out = tools.dispatch(call.name(), call.inputJson());
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

    /** Find the first {...} JSON object in the text and return that substring. */
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
