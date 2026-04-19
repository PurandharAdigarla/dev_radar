package com.devradar.ai;

/** Our response to a tool call, sent back as a user message. `outputJson` is the tool's result text. */
public record AiToolResult(String toolCallId, String outputJson, boolean isError) {}
