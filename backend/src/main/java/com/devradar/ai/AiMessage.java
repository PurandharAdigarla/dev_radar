package com.devradar.ai;

import java.util.List;

/**
 * A message in the conversation. role is "user" or "assistant".
 * `content` is the text part; `toolCalls` and `toolResults` carry structured pieces.
 */
public record AiMessage(String role, String content, List<AiToolCall> toolCalls, List<AiToolResult> toolResults) {
    public static AiMessage userText(String text) {
        return new AiMessage("user", text, List.of(), List.of());
    }
    public static AiMessage userToolResults(List<AiToolResult> results) {
        return new AiMessage("user", null, List.of(), results);
    }
}
