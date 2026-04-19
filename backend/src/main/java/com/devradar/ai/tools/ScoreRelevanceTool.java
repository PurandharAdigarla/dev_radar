package com.devradar.ai.tools;

import com.devradar.ai.AiClient;
import com.devradar.ai.AiMessage;
import com.devradar.ai.AiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScoreRelevanceTool {

    public static final String NAME = "scoreRelevance";
    public static final String DESCRIPTION = "Score how relevant a small batch of items is to the user's interests. Returns array of {id, score 0..1}.";
    public static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "user_interests": { "type": "array", "items": {"type": "string"} },
            "items": { "type": "array", "items": { "type": "object",
              "properties": { "id": {"type": "integer"}, "title": {"type": "string"} },
              "required": ["id", "title"] } }
          },
          "required": ["user_interests", "items"]
        }
        """;

    private static final String SYSTEM = "You are a relevance scorer. Given a user's interest tags and a batch of items (id + title), output a JSON array: [{\"id\":N,\"score\":0..1}, ...]. Score = how likely this user wants to read this. Output ONLY the JSON array, no prose.";

    private final AiClient ai;
    private final String model;

    public ScoreRelevanceTool(AiClient ai, @Value("${anthropic.scoring-model}") String model) {
        this.ai = ai; this.model = model;
    }

    public ToolDefinition definition() {
        return new ToolDefinition(NAME, DESCRIPTION, INPUT_SCHEMA);
    }

    public String execute(String inputJson) {
        AiResponse r = ai.generate(model, SYSTEM, List.of(AiMessage.userText(inputJson)), List.of(), 512);
        return r.text();
    }
}
