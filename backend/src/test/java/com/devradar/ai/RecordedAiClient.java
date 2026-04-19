package com.devradar.ai;

import com.devradar.ai.tools.ToolDefinition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Replays a queue of canned AiResponses; used in tests to script the agent loop. */
public class RecordedAiClient implements AiClient {

    private final Deque<AiResponse> queue = new ArrayDeque<>();

    public void enqueue(AiResponse r) { queue.add(r); }

    @Override
    public AiResponse generate(String model, String systemPrompt, List<AiMessage> messages, List<ToolDefinition> tools, int maxTokens) {
        if (queue.isEmpty()) throw new IllegalStateException("No more recorded responses");
        return queue.poll();
    }
}
