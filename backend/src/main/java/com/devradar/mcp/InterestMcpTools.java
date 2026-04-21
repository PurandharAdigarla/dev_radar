package com.devradar.mcp;

import com.devradar.mcp.dto.InterestMcpDTO;
import com.devradar.service.application.InterestApplicationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterestMcpTools {

    private final InterestApplicationService interests;
    private final MeterRegistry meters;

    public InterestMcpTools(InterestApplicationService interests, MeterRegistry meters) {
        this.interests = interests;
        this.meters = meters;
    }

    @Tool(name = "get_user_interests",
          description = "Returns the authenticated user's interest tags.")
    public List<InterestMcpDTO> getUserInterests() {
        try {
            List<InterestMcpDTO> out = interests.myInterests().stream()
                .map(t -> new InterestMcpDTO(t.slug(), t.displayName(),
                    t.category() == null ? null : t.category().name()))
                .toList();
            meters.counter("mcp.tool.calls", "tool", "get_user_interests", "status", "success").increment();
            return out;
        } catch (RuntimeException e) {
            meters.counter("mcp.tool.calls", "tool", "get_user_interests", "status", "error").increment();
            throw e;
        }
    }
}
