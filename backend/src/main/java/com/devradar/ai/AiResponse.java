package com.devradar.ai;

import java.util.List;

/**
 * What the model returned. `text` is the assistant's text content (may be empty).
 * `toolCalls` is non-empty when the model wants to invoke tools.
 * `stopReason` is "end_turn" when done, "tool_use" when more tools to run.
 */
public record AiResponse(String text, List<AiToolCall> toolCalls, String stopReason,
                          int inputTokens, int outputTokens, List<GroundingSource> groundingSources) {

    public AiResponse(String text, List<AiToolCall> toolCalls, String stopReason, int inputTokens, int outputTokens) {
        this(text, toolCalls, stopReason, inputTokens, outputTokens, List.of());
    }

    public record GroundingSource(String uri, String title) {}
}
