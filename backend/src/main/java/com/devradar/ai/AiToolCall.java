package com.devradar.ai;

/** A tool call requested by the assistant. `id` is provider-assigned. `inputJson` is the raw arguments. */
public record AiToolCall(String id, String name, String inputJson) {}
