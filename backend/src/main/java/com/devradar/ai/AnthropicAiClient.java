package com.devradar.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.devradar.ai.tools.ToolDefinition;
import com.devradar.observability.DailyMetricsCounter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Calls Anthropic's Claude API via the official Java SDK.
 * Instantiated by MultiProviderAiClient — not a Spring bean itself.
 */
public class AnthropicAiClient implements AiClient {

    private final AnthropicClient sdk;
    private final ObjectMapper json = new ObjectMapper();

    public AnthropicAiClient(String apiKey, MeterRegistry meterRegistry, DailyMetricsCounter dailyMetrics) {
        if (apiKey == null || apiKey.isBlank()) {
            this.sdk = AnthropicOkHttpClient.builder().fromEnv().build();
        } else {
            this.sdk = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        }
    }

    @Override
    public AiResponse generate(
            String model,
            String systemPrompt,
            List<AiMessage> messages,
            List<ToolDefinition> tools,
            int maxTokens) {

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt);

        for (AiMessage m : messages) {
            if ("user".equals(m.role())) {
                if (m.toolResults() != null && !m.toolResults().isEmpty()) {
                    List<ContentBlockParam> blocks = new ArrayList<>();
                    for (AiToolResult r : m.toolResults()) {
                        ToolResultBlockParam.Builder rb = ToolResultBlockParam.builder()
                                .toolUseId(r.toolCallId())
                                .content(r.outputJson())
                                .isError(r.isError());
                        blocks.add(ContentBlockParam.ofToolResult(rb.build()));
                    }
                    builder.addUserMessageOfBlockParams(blocks);
                } else if (m.content() != null) {
                    builder.addUserMessage(m.content());
                }
            } else if ("assistant".equals(m.role())) {
                if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                    List<ContentBlockParam> blocks = new ArrayList<>();
                    if (m.content() != null && !m.content().isBlank()) {
                        blocks.add(ContentBlockParam.ofText(
                                com.anthropic.models.messages.TextBlockParam.builder()
                                        .text(m.content())
                                        .build()));
                    }
                    for (AiToolCall tc : m.toolCalls()) {
                        try {
                            JsonNode inputNode = json.readTree(tc.inputJson());
                            blocks.add(ContentBlockParam.ofToolUse(
                                    com.anthropic.models.messages.ToolUseBlockParam.builder()
                                            .id(tc.id())
                                            .name(tc.name())
                                            .input(JsonValue.fromJsonNode(inputNode))
                                            .build()));
                        } catch (Exception e) {
                            throw new AiProviderException("invalid tool call input for " + tc.name(), e);
                        }
                    }
                    builder.addAssistantMessageOfBlockParams(blocks);
                } else if (m.content() != null) {
                    builder.addAssistantMessage(m.content());
                }
            }
        }

        for (ToolDefinition t : tools) {
            try {
                JsonNode schema = json.readTree(t.inputSchemaJson());

                Tool.InputSchema.Builder isBuilder = Tool.InputSchema.builder();

                JsonNode propertiesNode = schema.get("properties");
                if (propertiesNode != null && propertiesNode.isObject()) {
                    Map<String, JsonValue> propsMap = new HashMap<>();
                    Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        propsMap.put(entry.getKey(), JsonValue.fromJsonNode(entry.getValue()));
                    }
                    isBuilder.properties(
                            Tool.InputSchema.Properties.builder()
                                    .putAllAdditionalProperties(propsMap)
                                    .build());
                }

                JsonNode requiredNode = schema.get("required");
                if (requiredNode != null && requiredNode.isArray()) {
                    List<String> required = new ArrayList<>();
                    for (JsonNode r : requiredNode) {
                        required.add(r.asText());
                    }
                    isBuilder.required(required);
                }

                builder.addTool(Tool.builder()
                        .name(t.name())
                        .description(t.description())
                        .inputSchema(isBuilder.build())
                        .build());
            } catch (Exception e) {
                throw new AiProviderException("invalid tool schema for " + t.name(), e);
            }
        }

        Message resp;
        try {
            resp = sdk.messages().create(builder.build());
        } catch (com.anthropic.errors.RateLimitException e) {
            throw new AiRateLimitException("anthropic", e.getMessage());
        } catch (Exception e) {
            throw new AiProviderException("anthropic: " + e.getMessage(), e);
        }

        StringBuilder textOut = new StringBuilder();
        List<AiToolCall> toolCalls = new ArrayList<>();
        for (ContentBlock block : resp.content()) {
            block.text().ifPresent(t -> textOut.append(t.text()));
            block.toolUse().ifPresent(tu -> {
                String inputJson;
                try {
                    inputJson = json.writeValueAsString(tu._input());
                } catch (Exception e) {
                    inputJson = "{}";
                }
                toolCalls.add(new AiToolCall(tu.id(), tu.name(), inputJson));
            });
        }

        String stop = resp.stopReason().map(Object::toString).orElse("end_turn");
        int in = (int) resp.usage().inputTokens();
        int out = (int) resp.usage().outputTokens();

        return new AiResponse(textOut.toString(), toolCalls, stop, in, out);
    }
}
