package com.devradar.agent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class TopicValidationAgent {

    private static final Logger LOG = LoggerFactory.getLogger(TopicValidationAgent.class);

    private final RadarAgentFactory agentFactory;

    public TopicValidationAgent(RadarAgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    @SuppressWarnings("unchecked")
    public List<ValidatedTopic> validate(List<String> topics) {
        try {
            LlmAgent agent = agentFactory.buildValidationAgent();
            InMemoryRunner runner = new InMemoryRunner(agent, "validation");

            Session session = runner.sessionService()
                .createSession("validation", "system", (Map<String, Object>) null, null)
                .blockingGet();

            String input = "Validate these topics:\n" + String.join("\n", topics);
            Content message = Content.fromParts(Part.fromText(input));

            runner.runAsync("system", session.id(), message,
                    RunConfig.builder().maxLlmCalls(3).autoCreateSession(false).build())
                .blockingSubscribe();

            Session finalSession = runner.sessionService()
                .getSession("validation", "system", session.id(), Optional.empty())
                .blockingGet();

            Object result = finalSession.state().get("validation_result");
            return parseValidationResult(result, topics);
        } catch (Exception e) {
            LOG.warn("AI topic validation failed, accepting topics as-is: {}", e.getMessage());
            return topics.stream()
                .map(t -> new ValidatedTopic(t, true, t, null))
                .toList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ValidatedTopic> parseValidationResult(Object result, List<String> originalTopics) {
        if (result == null) {
            return originalTopics.stream()
                .map(t -> new ValidatedTopic(t, true, t, null))
                .toList();
        }

        List<ValidatedTopic> validated = new ArrayList<>();

        if (result instanceof Map<?, ?> map) {
            Object results = map.get("results");
            if (results instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        String topic = m.get("topic") != null ? m.get("topic").toString() : "";
                        boolean valid = m.get("valid") != null && Boolean.parseBoolean(m.get("valid").toString());
                        String normalized = m.get("normalized") != null ? m.get("normalized").toString() : topic;
                        String reason = m.get("reason") != null ? m.get("reason").toString() : null;
                        validated.add(new ValidatedTopic(topic, valid, normalized, reason));
                    }
                }
            }
        }

        if (validated.isEmpty()) {
            return originalTopics.stream()
                .map(t -> new ValidatedTopic(t, true, t, null))
                .toList();
        }

        return validated;
    }

    public record ValidatedTopic(String topic, boolean valid, String normalized, String reason) {}
}
