package com.devradar.ai.tools;

/**
 * `inputSchemaJson` is a JSON Schema describing the tool's input.
 * Anthropic format: {"type":"object","properties":{...},"required":[...]}
 */
public record ToolDefinition(String name, String description, String inputSchemaJson) {}
