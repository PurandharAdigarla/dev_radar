package com.devradar.ai;

import com.devradar.ai.tools.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Talks to any OpenAI-compatible API (Groq, Cerebras, OpenRouter, NVIDIA NIM).
 * Change the baseUrl and apiKey to switch providers.
 */
public class OpenAiCompatibleClient implements AiClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    private final RestClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String providerName;
    private final String defaultModel;

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String providerName, String defaultModel) {
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.providerName = providerName;
        this.defaultModel = defaultModel;
    }

    public String getProviderName() { return providerName; }
    public String getDefaultModel() { return defaultModel; }

    @Override
    public AiResponse generate(String model, String systemPrompt, List<AiMessage> messages,
                               List<ToolDefinition> tools, int maxTokens) {
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);

        ArrayNode msgs = body.putArray("messages");

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sysMsg = msgs.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
        }

        for (AiMessage m : messages) {
            if (m.toolResults() != null && !m.toolResults().isEmpty()) {
                for (AiToolResult r : m.toolResults()) {
                    ObjectNode toolMsg = msgs.addObject();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", r.toolCallId());
                    toolMsg.put("content", r.outputJson());
                }
            } else if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                ObjectNode assistantMsg = msgs.addObject();
                assistantMsg.put("role", "assistant");
                if (m.content() != null && !m.content().isBlank()) {
                    assistantMsg.put("content", m.content());
                } else {
                    assistantMsg.putNull("content");
                }
                ArrayNode toolCallsArr = assistantMsg.putArray("tool_calls");
                for (AiToolCall tc : m.toolCalls()) {
                    ObjectNode tcNode = toolCallsArr.addObject();
                    tcNode.put("id", tc.id());
                    tcNode.put("type", "function");
                    ObjectNode fn = tcNode.putObject("function");
                    fn.put("name", tc.name());
                    fn.put("arguments", tc.inputJson());
                }
            } else {
                ObjectNode msg = msgs.addObject();
                msg.put("role", m.role());
                if (m.content() != null) {
                    msg.put("content", m.content());
                } else {
                    msg.put("content", "");
                }
            }
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArr = body.putArray("tools");
            for (ToolDefinition t : tools) {
                ObjectNode tool = toolsArr.addObject();
                tool.put("type", "function");
                ObjectNode fn = tool.putObject("function");
                fn.put("name", t.name());
                fn.put("description", t.description());
                try {
                    fn.set("parameters", json.readTree(t.inputSchemaJson()));
                } catch (Exception e) {
                    fn.putObject("parameters");
                }
            }
        }

        JsonNode resp;
        try {
            resp = http.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            String msg = extractError(e.getResponseBodyAsString());
            if (status == 429) {
                throw new AiRateLimitException(providerName, msg);
            }
            throw new AiProviderException(providerName + " error " + status + ": " + msg, e);
        } catch (HttpServerErrorException e) {
            throw new AiProviderException(providerName + " server error: " + e.getStatusCode(), e);
        }

        StringBuilder textOut = new StringBuilder();
        List<AiToolCall> toolCalls = new ArrayList<>();
        String stopReason = "end_turn";
        int inputTokens = 0, outputTokens = 0;

        if (resp != null) {
            JsonNode choices = resp.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode choice = choices.get(0);
                String finishReason = choice.path("finish_reason").asText("stop");
                stopReason = "tool_calls".equals(finishReason) ? "tool_use" : "end_turn";

                JsonNode message = choice.path("message");
                String content = message.path("content").asText(null);
                if (content != null) textOut.append(content);

                JsonNode tcs = message.path("tool_calls");
                if (tcs.isArray()) {
                    for (JsonNode tc : tcs) {
                        String id = tc.path("id").asText();
                        String name = tc.path("function").path("name").asText();
                        String args = tc.path("function").path("arguments").asText("{}");
                        toolCalls.add(new AiToolCall(id, name, args));
                        stopReason = "tool_use";
                    }
                }
            }

            JsonNode usage = resp.path("usage");
            inputTokens = usage.path("prompt_tokens").asInt(0);
            outputTokens = usage.path("completion_tokens").asInt(0);
        }

        return new AiResponse(textOut.toString(), toolCalls, stopReason, inputTokens, outputTokens);
    }

    private String extractError(String responseBody) {
        try {
            JsonNode error = json.readTree(responseBody).path("error").path("message");
            String msg = error.asText("");
            return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
        } catch (Exception e) {
            return responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
        }
    }
}
