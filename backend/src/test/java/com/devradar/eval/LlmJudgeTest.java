package com.devradar.eval;

import com.devradar.ai.AiClient;
import com.devradar.ai.AiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmJudgeTest {

    @Mock
    private AiClient aiClient;

    private LlmJudge judge;

    @BeforeEach
    void setUp() {
        judge = new LlmJudge(aiClient, "claude-sonnet-4-6");
    }

    @Test
    void shouldParseRelevanceScoreFromLlmResponse() {
        when(aiClient.generate(anyString(), anyString(), anyList(), anyList(), anyInt()))
                .thenReturn(new AiResponse(
                        "{\"score\": 0.85, \"reasoning\": \"Themes closely match user interests\"}",
                        List.of(), "end_turn", 200, 50));

        BigDecimal score = judge.scoreRelevance(
                List.of("java", "spring_boot"),
                List.of("Spring Boot 3.5 updates", "Java security advisories")
        );

        assertThat(score).isEqualTo(new BigDecimal("0.850"));
    }

    @Test
    void shouldParseDistinctnessScoreFromLlmResponse() {
        when(aiClient.generate(anyString(), anyString(), anyList(), anyList(), anyInt()))
                .thenReturn(new AiResponse(
                        "{\"score\": 0.90, \"reasoning\": \"All themes cover different topics\"}",
                        List.of(), "end_turn", 200, 50));

        BigDecimal score = judge.scoreDistinctness(
                List.of("Spring Boot releases", "Security vulnerabilities", "Docker tooling")
        );

        assertThat(score).isEqualTo(new BigDecimal("0.900"));
    }

    @Test
    void shouldReturnZeroOnMalformedResponse() {
        when(aiClient.generate(anyString(), anyString(), anyList(), anyList(), anyInt()))
                .thenReturn(new AiResponse("not valid json", List.of(), "end_turn", 100, 20));

        BigDecimal score = judge.scoreRelevance(List.of("java"), List.of("Theme A"));
        assertThat(score).isEqualTo(BigDecimal.ZERO);
    }
}
