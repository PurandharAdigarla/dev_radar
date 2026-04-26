package com.devradar.ai;

import com.devradar.ai.tools.ToolRegistry;
import com.devradar.service.EngagementProfileService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RadarOrchestratorTest {

    @Test
    void runs_toolCallLoop_untilEndTurn() {
        RecordedAiClient ai = new RecordedAiClient();
        // Turn 1: model wants to call searchItems
        ai.enqueue(new AiResponse("", List.of(new AiToolCall("call_1", "searchItems", "{\"tag_slugs\":[\"spring_boot\"]}")),
            "tool_use", 100, 20));
        // Turn 2: end_turn with structured radar text
        ai.enqueue(new AiResponse("""
            {"themes":[
              {"title":"Spring Boot 3.5 ships","summary":"VTs default for @Async","item_ids":[1,2]}
            ]}
            """, List.of(), "end_turn", 80, 50));

        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.definitions()).thenReturn(List.of());
        when(tools.dispatch(eq("searchItems"), eq("{\"tag_slugs\":[\"spring_boot\"]}"), any())).thenReturn("[{\"id\":1,\"title\":\"sb 3.5\"},{\"id\":2,\"title\":\"sb perf\"}]");

        RadarOrchestrator orch = new RadarOrchestrator(ai, tools, mock(EngagementProfileService.class), "claude-sonnet-4-6", 8, 4096);

        var result = orch.generate(List.of("spring_boot"), List.of(1L, 2L, 3L), new com.devradar.ai.tools.ToolContext(null, null));

        assertThat(result.themes()).hasSize(1);
        assertThat(result.themes().get(0).title()).isEqualTo("Spring Boot 3.5 ships");
        assertThat(result.themes().get(0).itemIds()).containsExactly(1L, 2L);
        assertThat(result.totalInputTokens()).isEqualTo(180);
        assertThat(result.totalOutputTokens()).isEqualTo(70);
        verify(tools).dispatch(eq("searchItems"), eq("{\"tag_slugs\":[\"spring_boot\"]}"), any());
    }

    @Test
    void respects_maxIterations_evenIfModelKeepsCallingTools() {
        RecordedAiClient ai = new RecordedAiClient();
        for (int i = 0; i < 5; i++) {
            ai.enqueue(new AiResponse("", List.of(new AiToolCall("call_" + i, "searchItems", "{}")), "tool_use", 50, 10));
        }
        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.definitions()).thenReturn(List.of());
        when(tools.dispatch(eq("searchItems"), anyString(), any())).thenReturn("[]");

        RadarOrchestrator orch = new RadarOrchestrator(ai, tools, mock(EngagementProfileService.class), "claude-sonnet-4-6", 3, 4096);

        var result = orch.generate(List.of(), List.of(), new com.devradar.ai.tools.ToolContext(null, null));
        // 3 iterations, then we stop with whatever themes (none in this case)
        assertThat(result.themes()).isEmpty();
        verify(tools, times(3)).dispatch(eq("searchItems"), anyString(), any());
    }
}
