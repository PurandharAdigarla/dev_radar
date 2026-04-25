package com.devradar.ai;

import com.devradar.ai.tools.ToolDefinition;
import com.devradar.observability.DailyMetricsCounter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Routes LLM calls to the right provider based on model name, retries on rate limits
 * with exponential backoff, and falls back to the next available provider on failure.
 */
@Component
@Profile("!demo")
public class MultiProviderAiClient implements AiClient {

    private static final Logger LOG = LoggerFactory.getLogger(MultiProviderAiClient.class);

    record Provider(String name, String defaultModel, AiClient client) {}

    private final List<Provider> providers;
    private final MeterRegistry meterRegistry;
    private final DailyMetricsCounter dailyMetrics;
    private final int maxRetries;
    private final long initialDelayMs;

    public MultiProviderAiClient(
            MeterRegistry meterRegistry,
            DailyMetricsCounter dailyMetrics,
            @Value("${google-ai.api-key:}") String geminiKey,
            @Value("${google-ai.base-url:https://generativelanguage.googleapis.com}") String geminiUrl,
            @Value("${groq.api-key:}") String groqKey,
            @Value("${groq.base-url:https://api.groq.com/openai/v1}") String groqUrl,
            @Value("${groq.default-model:llama-3.3-70b-versatile}") String groqDefaultModel,
            @Value("${anthropic.api-key:}") String anthropicKey,
            @Value("${devradar.ai.retry.max-attempts:3}") int maxRetries,
            @Value("${devradar.ai.retry.initial-delay-ms:1000}") long initialDelayMs
    ) {
        this.meterRegistry = meterRegistry;
        this.dailyMetrics = dailyMetrics;
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.providers = new ArrayList<>();

        if (isConfigured(geminiKey)) {
            providers.add(new Provider("gemini", "gemini-2.5-flash",
                    new GeminiAiClient(geminiUrl, geminiKey)));
            LOG.info("AI provider registered: gemini (default model: gemini-2.5-flash)");
        }
        if (isConfigured(groqKey)) {
            providers.add(new Provider("groq", groqDefaultModel,
                    new OpenAiCompatibleClient(groqUrl, groqKey, "groq", groqDefaultModel)));
            LOG.info("AI provider registered: groq (default model: {})", groqDefaultModel);
        }
        if (isConfigured(anthropicKey)) {
            providers.add(new Provider("anthropic", "claude-sonnet-4-6",
                    new AnthropicAiClient(anthropicKey, meterRegistry, dailyMetrics)));
            LOG.info("AI provider registered: anthropic (default model: claude-sonnet-4-6)");
        }

        if (providers.isEmpty()) {
            LOG.warn("No AI provider configured. Set at least one of: GOOGLE_AI_API_KEY, GROQ_API_KEY, ANTHROPIC_API_KEY");
        } else {
            LOG.info("AI providers ready: {} (primary: {})", providers.stream().map(Provider::name).toList(), providers.get(0).name());
        }
    }

    @Override
    public AiResponse generate(String model, String systemPrompt, List<AiMessage> messages,
                               List<ToolDefinition> tools, int maxTokens) {
        if (providers.isEmpty()) {
            throw new AiProviderException("No AI provider configured. Set at least one of: GOOGLE_AI_API_KEY, GROQ_API_KEY, ANTHROPIC_API_KEY");
        }
        Provider primary = resolveProvider(model);

        // Try primary with retry
        try {
            AiResponse resp = executeWithRetry(primary, model, systemPrompt, messages, tools, maxTokens);
            trackMetrics(primary.name(), model, resp);
            return resp;
        } catch (Exception e) {
            LOG.warn("Primary provider {} failed for model={}: {}", primary.name(), model, e.getMessage());
        }

        // Try fallbacks in registration order
        for (Provider fallback : providers) {
            if (fallback == primary) continue;
            try {
                LOG.info("Falling back to {} with model={}", fallback.name(), fallback.defaultModel());
                AiResponse resp = executeWithRetry(fallback, fallback.defaultModel(), systemPrompt, messages, tools, maxTokens);
                trackMetrics(fallback.name(), fallback.defaultModel(), resp);
                Counter.builder("ai.client.fallback")
                        .tag("from", primary.name())
                        .tag("to", fallback.name())
                        .register(meterRegistry)
                        .increment();
                return resp;
            } catch (Exception e) {
                LOG.warn("Fallback provider {} also failed: {}", fallback.name(), e.getMessage());
            }
        }

        throw new AiProviderException("All AI providers failed for model=" + model);
    }

    private AiResponse executeWithRetry(Provider provider, String model, String systemPrompt,
                                        List<AiMessage> messages, List<ToolDefinition> tools, int maxTokens) {
        long delay = initialDelayMs;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                var sample = Timer.start(meterRegistry);
                AiResponse resp = provider.client().generate(model, systemPrompt, messages, tools, maxTokens);
                sample.stop(Timer.builder("ai.client.duration")
                        .tag("provider", provider.name())
                        .tag("model", model)
                        .register(meterRegistry));
                return resp;
            } catch (AiRateLimitException e) {
                lastException = e;
                if (attempt < maxRetries) {
                    LOG.info("Rate limited by {} (attempt {}/{}), backing off {}ms",
                            provider.name(), attempt + 1, maxRetries, delay);
                    sleep(delay);
                    delay = Math.min(delay * 2, 30_000);
                }
            } catch (AiProviderException e) {
                throw e;
            } catch (Exception e) {
                throw new AiProviderException(provider.name() + ": " + e.getMessage(), e);
            }
        }

        throw (lastException instanceof AiProviderException ape) ? ape
                : new AiProviderException(provider.name() + " failed after " + maxRetries + " retries", lastException);
    }

    private Provider resolveProvider(String model) {
        if (model == null || model.isBlank()) return providers.get(0);

        for (Provider p : providers) {
            if (model.startsWith("gemini") && "gemini".equals(p.name())) return p;
            if (model.startsWith("claude") && "anthropic".equals(p.name())) return p;
            if (isGroqModel(model) && "groq".equals(p.name())) return p;
        }

        return providers.get(0);
    }

    private static boolean isGroqModel(String model) {
        return model.startsWith("llama") || model.startsWith("gemma")
                || model.startsWith("mixtral") || model.startsWith("deepseek")
                || model.startsWith("qwen");
    }

    private void trackMetrics(String providerName, String model, AiResponse resp) {
        Counter.builder("ai.client.tokens")
                .tag("provider", providerName)
                .tag("model", model)
                .tag("direction", "input")
                .register(meterRegistry)
                .increment(resp.inputTokens());
        Counter.builder("ai.client.tokens")
                .tag("provider", providerName)
                .tag("model", model)
                .tag("direction", "output")
                .register(meterRegistry)
                .increment(resp.outputTokens());

        var today = LocalDate.now();
        if (model.contains("sonnet")) dailyMetrics.incrementSonnetCalls(today);
        else if (model.contains("haiku")) dailyMetrics.incrementHaikuCalls(today);
    }

    private static boolean isConfigured(String key) {
        return key != null && !key.isBlank();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted during retry backoff", e);
        }
    }
}
