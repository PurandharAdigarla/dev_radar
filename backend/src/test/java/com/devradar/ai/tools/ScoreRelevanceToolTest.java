package com.devradar.ai.tools;

import com.devradar.ai.AiClient;
import com.devradar.ai.AiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoreRelevanceToolTest {

    @Test
    void execute_returnsScoresFromHaiku() throws Exception {
        AiClient ai = mock(AiClient.class);
        when(ai.generate(
                ArgumentMatchers.eq("claude-haiku-4-5-20251001"),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyList(),
                ArgumentMatchers.anyList(),
                ArgumentMatchers.anyInt()))
            .thenReturn(new AiResponse("[{\"id\":1,\"score\":0.92},{\"id\":2,\"score\":0.41}]", java.util.List.of(), "end_turn", 100, 30));

        ScoreRelevanceTool tool = new ScoreRelevanceTool(ai, "claude-haiku-4-5-20251001");
        String input = """
            {
              "user_interests": ["spring_boot","react"],
              "items": [
                {"id": 1, "title": "Spring Boot 3.5"},
                {"id": 2, "title": "Linux gaming benchmarks"}
              ]
            }
            """;

        String result = tool.execute(input);
        ObjectMapper om = new ObjectMapper();
        JsonNode arr = om.readTree(result);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isEqualTo(2);
        assertThat(arr.get(0).get("score").asDouble()).isGreaterThan(arr.get(1).get("score").asDouble());
    }
}
