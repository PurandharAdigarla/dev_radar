package com.devradar.ai;

import com.devradar.ai.tools.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Calls Google AI Studio's Gemini API via REST.
 * Instantiated by MultiProviderAiClient — not a Spring bean itself.
 */
public class GeminiAiClient implements AiClient {

    private final RestClient http;
    private final String apiKey;
    private final ObjectMapper json = new ObjectMapper();

    public GeminiAiClient(String baseUrl, String apiKey) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public AiResponse generate(String model, String systemPrompt, List<AiMessage> messages,
                               List<ToolDefinition> tools, int maxTokens, boolean enableWebSearch) {
        AiResponse resp = doGenerate(model, systemPrompt, messages, tools, maxTokens, enableWebSearch);
        return resp;
    }

    @Override
    public AiResponse generate(String model, String systemPrompt, List<AiMessage> messages, List<ToolDefinition> tools, int maxTokens) {
        return doGenerate(model, systemPrompt, messages, tools, maxTokens, false);
    }

    private AiResponse doGenerate(String model, String systemPrompt, List<AiMessage> messages,
                                  List<ToolDefinition> tools, int maxTokens, boolean enableWebSearch) {
        // Gemini uses model name like "gemini-2.0-flash" not "claude-sonnet-4-6".
        // If the caller passes a Claude model name, ignore it and use the configured Gemini default.
        String geminiModel = model.startsWith("gemini") ? model : "gemini-2.5-flash";

        ObjectNode body = json.createObjectNode();

        // System instruction
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sysInstr = body.putObject("systemInstruction");
            ArrayNode sysParts = sysInstr.putArray("parts");
            sysParts.addObject().put("text", systemPrompt);
        }

        // Contents: translate each AiMessage
        ArrayNode contents = body.putArray("contents");
        for (AiMessage m : messages) {
            ObjectNode content = contents.addObject();
            String role = "assistant".equals(m.role()) ? "model" : "user";
            content.put("role", role);
            ArrayNode parts = content.putArray("parts");

            if (m.toolResults() != null && !m.toolResults().isEmpty()) {
                for (AiToolResult r : m.toolResults()) {
                    ObjectNode part = parts.addObject();
                    ObjectNode fr = part.putObject("functionResponse");
                    fr.put("name", extractFunctionName(r.toolCallId()));
                    ObjectNode response = fr.putObject("response");
                    try {
                        response.set("result", json.readTree(r.outputJson()));
                    } catch (Exception e) {
                        response.put("result", r.outputJson());
                    }
                }
            } else if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                if (m.content() != null && !m.content().isBlank()) {
                    parts.addObject().put("text", m.content());
                }
                for (AiToolCall tc : m.toolCalls()) {
                    ObjectNode part = parts.addObject();
                    ObjectNode fc = part.putObject("functionCall");
                    fc.put("name", tc.name());
                    try {
                        fc.set("args", json.readTree(tc.inputJson()));
                    } catch (Exception e) {
                        fc.putObject("args");
                    }
                }
            } else if (m.content() != null) {
                parts.addObject().put("text", m.content());
            }
        }

        // Tools
        ArrayNode toolsArr = body.putArray("tools");
        if (tools != null && !tools.isEmpty()) {
            ObjectNode toolEntry = toolsArr.addObject();
            ArrayNode declarations = toolEntry.putArray("functionDeclarations");
            for (ToolDefinition t : tools) {
                ObjectNode decl = declarations.addObject();
                decl.put("name", t.name());
                decl.put("description", t.description());
                try {
                    decl.set("parameters", json.readTree(t.inputSchemaJson()));
                } catch (Exception e) {
                    // skip malformed schema
                }
            }
        }
        if (enableWebSearch) {
            toolsArr.addObject().putObject("google_search");
        }
        if (toolsArr.isEmpty()) {
            body.remove("tools");
        }

        // Generation config
        ObjectNode genCfg = body.putObject("generationConfig");
        genCfg.put("maxOutputTokens", maxTokens);

        // Call API
        JsonNode resp;
        try {
            resp = http.post()
                .uri(uri -> uri.path("/v1beta/models/" + geminiModel + ":generateContent")
                    .queryParam("key", apiKey)
                    .build())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(JsonNode.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 429) {
                throw new AiRateLimitException("gemini", extractGeminiError(e.getResponseBodyAsString()));
            }
            String shortMsg = switch (status) {
                case 400 -> "Gemini bad request: " + extractGeminiError(e.getResponseBodyAsString());
                case 403 -> "Gemini API key invalid or forbidden.";
                default -> "Gemini API error " + status + ": " + extractGeminiError(e.getResponseBodyAsString());
            };
            throw new AiProviderException(shortMsg);
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            throw new AiProviderException("Gemini server error: " + e.getStatusCode(), e);
        }

        // Parse response
        StringBuilder textOut = new StringBuilder();
        List<AiToolCall> toolCalls = new ArrayList<>();
        List<AiResponse.GroundingSource> groundingSources = new ArrayList<>();
        String stopReason = "end_turn";
        int inputTokens = 0;
        int outputTokens = 0;

        if (resp != null) {
            JsonNode candidates = resp.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode candidate = candidates.get(0);
                JsonNode finishReason = candidate.path("finishReason");
                if ("STOP".equals(finishReason.asText("STOP"))) stopReason = "end_turn";
                else if ("MAX_TOKENS".equals(finishReason.asText())) stopReason = "end_turn";
                JsonNode parts = candidate.path("content").path("parts");
                int callIdx = 0;
                for (JsonNode part : parts) {
                    if (part.has("text")) {
                        textOut.append(part.get("text").asText());
                    } else if (part.has("functionCall")) {
                        JsonNode fc = part.get("functionCall");
                        String name = fc.path("name").asText();
                        String argsJson = fc.path("args").toString();
                        String fakeId = "gemini_" + callIdx++ + "_" + name;
                        toolCalls.add(new AiToolCall(fakeId, name, argsJson));
                        stopReason = "tool_use";
                    }
                }

                JsonNode grounding = candidate.path("groundingMetadata");
                if (grounding.has("groundingChunks")) {
                    for (JsonNode chunk : grounding.path("groundingChunks")) {
                        JsonNode web = chunk.path("web");
                        if (!web.isMissingNode()) {
                            groundingSources.add(new AiResponse.GroundingSource(
                                    web.path("uri").asText(), web.path("title").asText("")));
                        }
                    }
                }
            }
            JsonNode usage = resp.path("usageMetadata");
            inputTokens = usage.path("promptTokenCount").asInt(0);
            outputTokens = usage.path("candidatesTokenCount").asInt(0);
        }

        return new AiResponse(textOut.toString(), toolCalls, stopReason, inputTokens, outputTokens, groundingSources);
    }

    private String extractGeminiError(String responseBody) {
        try {
            JsonNode error = json.readTree(responseBody).path("error").path("message");
            String msg = error.asText("");
            return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
        } catch (Exception e) {
            return responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
        }
    }

    /** Extract the function name from our fabricated tool_call id (format: "gemini_<idx>_<name>"). */
    private static String extractFunctionName(String toolCallId) {
        if (toolCallId == null) return "unknown";
        int firstUnderscore = toolCallId.indexOf('_');
        int secondUnderscore = toolCallId.indexOf('_', firstUnderscore + 1);
        if (secondUnderscore > 0) return toolCallId.substring(secondUnderscore + 1);
        return toolCallId;
    }
}
