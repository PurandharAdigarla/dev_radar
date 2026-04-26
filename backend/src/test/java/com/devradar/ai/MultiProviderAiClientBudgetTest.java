package com.devradar.ai;

import com.devradar.ai.tools.ToolDefinition;
import com.devradar.observability.DailyMetricsCounter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MultiProviderAiClientBudgetTest {

    DailyMetricsCounter dailyMetrics;
    AiClient mockProvider;
    MultiProviderAiClient client;

    @BeforeEach
    void setUp() throws Exception {
        dailyMetrics = mock(DailyMetricsCounter.class);
        mockProvider = mock(AiClient.class);

        client = new MultiProviderAiClient(
                new SimpleMeterRegistry(),
                dailyMetrics,
                "", "", "", "", "llama-3.3-70b-versatile", "",
                0, 100, 5.0
        );

        // Inject a mock provider via reflection since constructor builds concrete clients
        Field providersField = MultiProviderAiClient.class.getDeclaredField("providers");
        providersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> providers = (List<Object>) providersField.get(client);
        providers.clear();

        var providerRecord = MultiProviderAiClient.class.getDeclaredClasses();
        Class<?> providerClass = null;
        for (Class<?> c : providerRecord) {
            if (c.getSimpleName().equals("Provider")) {
                providerClass = c;
                break;
            }
        }
        assert providerClass != null;
        var constructor = providerClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object provider = constructor.newInstance("gemini", "gemini-2.5-flash", mockProvider);
        providers.add(provider);
    }

    @Test
    void generatesNormallyWhenUnderBudget() {
        when(dailyMetrics.estimatedCostUsd(any(LocalDate.class))).thenReturn(1.0);
        AiResponse expected = new AiResponse("test", List.of(), "end_turn", 100, 50);
        when(mockProvider.generate(anyString(), anyString(), anyList(), anyList(), anyInt())).thenReturn(expected);

        AiResponse result = client.generate("gemini-2.5-flash", "sys", List.of(AiMessage.userText("hi")), List.of(), 100);

        assertThat(result.text()).isEqualTo("test");
        verify(mockProvider).generate(anyString(), anyString(), anyList(), anyList(), anyInt());
    }

    @Test
    void throwsWhenBudgetExceeded() {
        when(dailyMetrics.estimatedCostUsd(any(LocalDate.class))).thenReturn(5.0);

        assertThatThrownBy(() -> client.generate("gemini-2.5-flash", "sys", List.of(AiMessage.userText("hi")), List.of(), 100))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Daily AI budget of $5.0 exhausted");
    }
}
