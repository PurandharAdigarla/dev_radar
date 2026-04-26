package com.devradar.ai;

import com.devradar.ai.tools.ToolDefinition;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Returns a canned radar JSON. Useful for demos, frontend dev, and running the system without any AI provider.
 * Activated with -Dspring-boot.run.profiles=demo
 */
@Component
@Profile("demo")
public class MockAiClient implements AiClient {

    private static final String CANNED = """
        {
          "themes": [
            {
              "title": "DEMO MODE — Spring Boot 3.5 release patterns",
              "summary": "This is a stub response from MockAiClient. Set GOOGLE_AI_API_KEY to enable real AI-generated radars.",
              "item_ids": []
            },
            {
              "title": "DEMO MODE — htmx is having a moment",
              "summary": "Replace this stub with a real LLM by switching profile. The agent loop, tool dispatch, persistence, SSE — everything else is real.",
              "item_ids": []
            }
          ]
        }
        """;

    @Override
    public AiResponse generate(String model, String systemPrompt, List<AiMessage> messages, List<ToolDefinition> tools, int maxTokens) {
        return new AiResponse(CANNED, List.of(), "end_turn", 100, 200);
    }
}
