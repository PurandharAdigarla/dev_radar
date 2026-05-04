package com.devradar.agent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.tools.GoogleSearchTool;
import com.google.genai.types.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RadarAgentFactory {

    private final String model;

    public RadarAgentFactory(@Value("${devradar.ai.model:gemini-2.5-flash}") String model) {
        this.model = model;
    }

    public SequentialAgent buildRadarPipeline(List<String> topics, LocalDate since,
                                              List<PreviousRadar> previousRadars,
                                              List<String> previousRepoUrls) {
        // Stage 1: Parallel AI news research per topic
        List<LlmAgent> researchers = new ArrayList<>();
        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            List<String> prevThemes = extractPreviousThemesFor(topic, previousRadars);

            researchers.add(LlmAgent.builder()
                .name("research_" + i)
                .model(model)
                .instruction(buildResearchInstruction(topic, since, prevThemes))
                .tools(GoogleSearchTool.INSTANCE)
                .outputKey("result_" + i)
                .build());
        }

        ParallelAgent research = ParallelAgent.builder()
            .name("topic_research")
            .subAgents(researchers)
            .build();

        // Stage 2: Parallel repo discovery per topic
        List<LlmAgent> repoScouts = new ArrayList<>();
        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            repoScouts.add(LlmAgent.builder()
                .name("repos_" + i)
                .model(model)
                .instruction(buildRepoDiscoveryInstruction(topic, previousRepoUrls))
                .tools(GoogleSearchTool.INSTANCE)
                .outputKey("repos_" + i)
                .build());
        }

        ParallelAgent repoDiscovery = ParallelAgent.builder()
            .name("repo_discovery")
            .subAgents(repoScouts)
            .build();

        return SequentialAgent.builder()
            .name("radar_pipeline")
            .subAgents(research, repoDiscovery)
            .build();
    }

    public LlmAgent buildValidationAgent() {
        return LlmAgent.builder()
            .name("topic_validator")
            .model(model)
            .instruction("""
                You validate whether topics are legitimate technology skills or domains.
                For each topic provided, determine if it's VALID or INVALID and normalize it.

                VALID: programming languages, frameworks, dev practices, tool categories,
                       cloud platforms, databases, AI/ML domains, mobile development, etc.
                INVALID: gibberish, single characters, offensive content, non-tech topics,
                         overly vague terms like "things" or "stuff".

                Normalize: fix casing, expand common abbreviations, remove trailing punctuation.
                Examples: "fe dev" → "Frontend Development", "k8s" → "Kubernetes", "ml ops" → "MLOps"
                """)
            .outputSchema(validationSchema())
            .outputKey("validation_result")
            .build();
    }

    private String buildResearchInstruction(String topic, LocalDate since, List<String> prevThemes) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            You are a Dev AI Radar research analyst. Your job is to find the most important
            recent developments — both AI-related AND general — in the technology domain: "%s".

            Search broadly for:
            - Major version releases, breaking changes, deprecations, or migration guides
            - New tools, libraries, frameworks, or significant feature additions
            - Security advisories (CVEs), critical bug fixes, or vulnerability patches
            - AI-powered tools, integrations, or workflow improvements for this domain
            - Notable blog posts, RFCs, or community discussions shaping the ecosystem
            - Performance benchmarks, adoption milestones, or ecosystem shifts

            Rules:
            - Every claim must be grounded in your Google Search results
            - Be SPECIFIC: name exact versions, dates, CVE numbers, product names
            - Cite at least 3 sources with their actual URLs (not redirect URLs)
            - Title: one specific headline under 120 chars (e.g. "Spring Boot 3.5.1 fixes critical OAuth2 token leak")
            - Summary: 3-5 sentences structured as:
              1. WHAT happened (the specific event, release, or announcement)
              2. WHY it matters to practitioners (impact on their work)
              3. WHAT TO DO about it (upgrade path, migration steps, things to try or watch)
            - Almost always set has_news to true — there is nearly always something notable.
              Only set false if you genuinely found zero developments after thorough searching.
            """.formatted(topic));

        if (since != null) {
            sb.append("\nTime period: Focus on developments from **")
              .append(since.format(DateTimeFormatter.ISO_DATE))
              .append("** to **")
              .append(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
              .append("**. If nothing happened in this exact window, broaden to the last 2 weeks.\n");
        } else {
            sb.append("""

                This is the user's FIRST radar. Do a deep sweep of notable developments
                (last 2-4 weeks) for this topic. Cover the most impactful story.
                """);
        }

        if (!prevThemes.isEmpty()) {
            sb.append("\nDO NOT repeat these previously reported findings:\n");
            for (String prev : prevThemes) {
                sb.append("- ").append(prev).append("\n");
            }
        }

        sb.append("""

            Search Google thoroughly — try multiple queries. Respond with ONLY valid JSON:
            {
              "topic": "<topic name>",
              "title": "<specific headline, max 120 chars>",
              "summary": "<3-5 sentences: what happened, why it matters, what to do>",
              "has_news": true,
              "sources": [{"url": "<direct source url>", "title": "<source title>"}]
            }
            """);
        return sb.toString();
    }


    private String buildRepoDiscoveryInstruction(String topic, List<String> previousRepoUrls) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            You are a Dev AI Radar repo scout. Your job is to discover GitHub repositories that are
            valuable for **agentic development** in the domain: "%s".

            Focus on repos that:
            - Provide MCP servers, Claude Code skills, or agent tools relevant to this domain
            - Are AI agent frameworks, SDKs, or libraries applicable to this domain
            - Offer AI-powered developer tools (code generation, testing, deployment) for this domain
            - Contain prompt engineering resources, skill templates, or agent patterns for this domain

            Well-known baselines to compare against (find what's BETTER or more specialized):
            - Claude Code official skills (anthropics/claude-code)
            - Cursor rules repositories
            - Generic MCP server collections
            - Standard LangChain/LlamaIndex/CrewAI templates

            Rules:
            - Search GitHub specifically: use queries like "github.com [topic] MCP server" or "github.com [topic] agent"
            - Every repo must be REAL — verified via Google Search results
            - Explain WHY this repo is notable vs. the baselines above
            - Category must be one of: mcp-server, agent-skill, agent-framework, dev-tool, prompt-library
            - Return 2-4 repos maximum — quality over quantity
            - If no noteworthy repos exist for this domain, return an empty repos array
            """.formatted(topic));

        if (!previousRepoUrls.isEmpty()) {
            sb.append("\nDO NOT recommend these previously surfaced repos:\n");
            for (String url : previousRepoUrls) {
                sb.append("- ").append(url).append("\n");
            }
        }

        sb.append("""

            Use Google Search to find repos. Respond with ONLY valid JSON:
            {
              "topic": "<topic name>",
              "repos": [
                {
                  "repo_url": "https://github.com/owner/repo",
                  "repo_name": "owner/repo",
                  "description": "<what it does>",
                  "why_notable": "<why this is better than baselines>",
                  "category": "mcp-server|agent-skill|agent-framework|dev-tool|prompt-library"
                }
              ]
            }
            """);
        return sb.toString();
    }

    private List<String> extractPreviousThemesFor(String topic, List<PreviousRadar> previousRadars) {
        List<String> result = new ArrayList<>();
        for (PreviousRadar radar : previousRadars) {
            for (PreviousTheme theme : radar.themes()) {
                if (topic.equalsIgnoreCase(theme.topic())) {
                    result.add("[%s] %s: %s".formatted(radar.date(), theme.title(), theme.summary()));
                }
            }
        }
        return result;
    }

    private static Schema validationSchema() {
        return Schema.builder()
            .type("OBJECT")
            .properties(Map.of(
                "results", Schema.builder()
                    .type("ARRAY")
                    .items(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                            "topic", Schema.builder().type("STRING").build(),
                            "valid", Schema.builder().type("BOOLEAN").build(),
                            "normalized", Schema.builder().type("STRING").build(),
                            "reason", Schema.builder().type("STRING").build()
                        ))
                        .required(List.of("topic", "valid", "normalized"))
                        .build())
                    .build()
            ))
            .required(List.of("results"))
            .build();
    }

    public record PreviousRadar(String date, List<PreviousTheme> themes) {}
    public record PreviousTheme(String topic, String title, String summary) {}
}
