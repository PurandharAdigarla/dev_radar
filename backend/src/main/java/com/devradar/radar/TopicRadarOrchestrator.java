package com.devradar.radar;

import com.devradar.ai.AiClient;
import com.devradar.ai.AiMessage;
import com.devradar.ai.AiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class TopicRadarOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(TopicRadarOrchestrator.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are a Dev AI Radar analyst. Your job is to research how AI is transforming
        specific technology domains and deliver actionable, concrete updates.

        For each topic the user has selected, produce ONE theme covering recent AI developments
        that affect that domain. Focus exclusively on:
        - New AI tools, products, or features that impact this domain
        - AI-powered alternatives or enhancements to existing tools in this domain
        - Significant announcements, launches, or updates from AI companies relevant to this domain
        - Emerging patterns in how AI is being applied to this domain

        DO NOT include:
        - General news unrelated to AI's impact on the topic
        - Vague speculation without concrete products/tools/announcements
        - Repeated information from previous radars (provided below if available)

        Output a single JSON object:
        {"themes": [
          {
            "topic": "the user's topic exactly as given",
            "title": "Specific headline about an AI development (max 120 chars)",
            "summary": "2-4 sentences: what happened, what tool/product, why it matters for practitioners in this domain, what to do about it.",
            "sources": [{"url": "source_url", "title": "source_title"}]
          }
        ]}

        Rules:
        - One theme per topic, in the same order as the user's topic list.
        - Every claim must be grounded in your web search results.
        - If there's genuinely nothing new for a topic in the given period, say so honestly
          in the summary rather than fabricating.
        - Summaries should be practical and opinionated — tell the user what matters and why.
        """;

    private final AiClient ai;
    private final String model;
    private final int maxTokens;

    public TopicRadarOrchestrator(AiClient ai,
                                  @Value("${devradar.ai.orchestrator-model}") String model,
                                  @Value("${devradar.ai.max-tokens-per-call:8192}") int maxTokens) {
        this.ai = ai;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public TopicRadarResult generate(List<String> topics, List<PreviousRadar> previousRadars, LocalDate since) {
        StringBuilder userMsg = new StringBuilder();
        userMsg.append("## User's Topics\n");
        for (int i = 0; i < topics.size(); i++) {
            userMsg.append(i + 1).append(". ").append(topics.get(i)).append("\n");
        }

        if (since != null) {
            userMsg.append("\n## Time Period\n");
            userMsg.append("Search for AI developments from **").append(since.format(DateTimeFormatter.ISO_DATE));
            userMsg.append("** to **").append(LocalDate.now().format(DateTimeFormatter.ISO_DATE)).append("** only.\n");
        } else {
            userMsg.append("\n## Time Period\n");
            userMsg.append("This is the user's FIRST radar. Do a deep sweep of recent AI developments ");
            userMsg.append("(last 2-4 weeks) for each topic.\n");
        }

        if (!previousRadars.isEmpty()) {
            userMsg.append("\n## Previous Radars (do NOT repeat this information)\n");
            for (var prev : previousRadars) {
                userMsg.append("\n### ").append(prev.date()).append("\n");
                for (var theme : prev.themes()) {
                    userMsg.append("- [").append(theme.topic()).append("] ").append(theme.title());
                    userMsg.append(": ").append(theme.summary()).append("\n");
                }
            }
        }

        userMsg.append("\nUse Google Search to research each topic deeply. Produce the JSON output.");

        AiResponse resp = ai.generate(model, SYSTEM_PROMPT,
                List.of(AiMessage.userText(userMsg.toString())), List.of(), maxTokens, true);

        List<TopicTheme> themes = new ArrayList<>();
        List<AiResponse.GroundingSource> webSources = new ArrayList<>(resp.groundingSources());

        try {
            String text = resp.text();
            JsonNode root = JSON.readTree(extractJson(text));
            JsonNode arr = root.path("themes");
            if (arr.isArray()) {
                for (JsonNode t : arr) {
                    String topic = t.path("topic").asText();
                    String title = t.path("title").asText();
                    String summary = t.path("summary").asText();
                    List<AiResponse.GroundingSource> themeSources = new ArrayList<>();
                    JsonNode sourcesNode = t.path("sources");
                    if (sourcesNode.isArray()) {
                        for (JsonNode s : sourcesNode) {
                            themeSources.add(new AiResponse.GroundingSource(
                                    s.path("url").asText(), s.path("title").asText("")));
                        }
                    }
                    themes.add(new TopicTheme(topic, title, summary, themeSources));
                }
            }
        } catch (Exception e) {
            LOG.warn("failed to parse topic radar JSON: {}", e.getMessage());
        }

        return new TopicRadarResult(themes, resp.inputTokens(), resp.outputTokens(), webSources);
    }

    private static String extractJson(String text) {
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

    public record TopicRadarResult(List<TopicTheme> themes, int inputTokens, int outputTokens,
                                   List<AiResponse.GroundingSource> webSources) {}
    public record TopicTheme(String topic, String title, String summary, List<AiResponse.GroundingSource> sources) {}
    public record PreviousRadar(String date, List<PreviousTheme> themes) {}
    public record PreviousTheme(String topic, String title, String summary) {}
}
