package com.devradar.mcp;

import com.devradar.mcp.dto.InterestMcpDTO;
import com.devradar.service.application.InterestApplicationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterestMcpTools {

    private final InterestApplicationService interests;

    public InterestMcpTools(InterestApplicationService interests) { this.interests = interests; }

    @Tool(name = "get_user_interests",
          description = "Returns the authenticated user's interest tags.")
    public List<InterestMcpDTO> getUserInterests() {
        return interests.myInterests().stream()
            .map(t -> new InterestMcpDTO(t.slug(), t.displayName(),
                t.category() == null ? null : t.category().name()))
            .toList();
    }
}
