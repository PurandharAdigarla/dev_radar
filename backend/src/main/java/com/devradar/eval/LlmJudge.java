package com.devradar.eval;

import com.devradar.ai.AiClient;
import com.devradar.ai.AiMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class LlmJudge {

    private static final Logger log = LoggerFactory.getLogger(LlmJudge.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiClient aiClient;
    private final String judgeModel;

    public LlmJudge(AiClient aiClient,
                     @Value("${anthropic.orchestrator-model:claude-sonnet-4-6}") String judgeModel) {
        this.aiClient = aiClient;
        this.judgeModel = judgeModel;
    }

    public BigDecimal scoreRelevance(List<String> userInterests, List<String> themeTitles) {
        String prompt = String.format("""
                You are an eval judge. Rate how relevant these radar themes are to the user's declared interests.

                User interests: %s
                Theme titles: %s

                Respond with JSON only: {"score": <0.0 to 1.0>, "reasoning": "<brief explanation>"}
                Score 1.0 means all themes directly match user interests. Score 0.0 means none are relevant.""",
                userInterests, themeTitles);

        return callJudge(prompt);
    }

    public BigDecimal scoreDistinctness(List<String> themeTitles) {
        String prompt = String.format("""
                You are an eval judge. Rate how distinct these radar themes are from each other.

                Theme titles: %s

                Respond with JSON only: {"score": <0.0 to 1.0>, "reasoning": "<brief explanation>"}
                Score 1.0 means all themes cover completely different topics. Score 0.0 means they are all duplicates.""",
                themeTitles);

        return callJudge(prompt);
    }

    private BigDecimal callJudge(String prompt) {
        try {
            var response = aiClient.generate(
                    judgeModel,
                    "You are a strict evaluation judge. Always respond with valid JSON.",
                    List.of(AiMessage.userText(prompt)),
                    List.of(),
                    256
            );

            String text = response.text().trim();
            if (text.startsWith("```")) {
                text = text.replaceAll("```json?\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            JsonNode node = objectMapper.readTree(text);
            double score = node.get("score").asDouble();
            return BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("LLM judge failed to produce a valid score: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
