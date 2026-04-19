package com.devradar.ai;

import com.devradar.ai.tools.ToolDefinition;
import java.util.List;

/**
 * Provider-agnostic interface to a chat model with tool use.
 * Production impl uses Anthropic SDK; tests use a recorded fake.
 */
public interface AiClient {
    AiResponse generate(String model, String systemPrompt, List<AiMessage> messages, List<ToolDefinition> tools, int maxTokens);
}
