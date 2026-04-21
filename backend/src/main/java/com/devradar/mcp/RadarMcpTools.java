package com.devradar.mcp;

import com.devradar.mcp.dto.RadarMcpDTO;
import com.devradar.radar.application.RadarApplicationService;
import com.devradar.security.SecurityUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class RadarMcpTools {

    private final RadarApplicationService radars;

    public RadarMcpTools(RadarApplicationService radars) { this.radars = radars; }

    @Tool(name = "query_radar",
          description = "Returns the latest READY radar for the authenticated user, or an empty payload if none exist.")
    public RadarMcpDTO queryRadar() {
        Long uid = SecurityUtils.getCurrentUserId();
        return radars.getLatestForUser(uid)
            .orElse(new RadarMcpDTO(null, null, null, null, java.util.List.of()));
    }
}
