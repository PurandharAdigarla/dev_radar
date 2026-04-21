package com.devradar.mcp;

import com.devradar.mcp.dto.RadarMcpDTO;
import com.devradar.radar.application.RadarApplicationService;
import com.devradar.security.SecurityUtils;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class RadarMcpTools {

    private final RadarApplicationService radars;
    private final MeterRegistry meters;

    public RadarMcpTools(RadarApplicationService radars, MeterRegistry meters) {
        this.radars = radars;
        this.meters = meters;
    }

    @Tool(name = "query_radar",
          description = "Returns the latest READY radar for the authenticated user, or an empty payload if none exist.")
    public RadarMcpDTO queryRadar() {
        try {
            Long uid = SecurityUtils.getCurrentUserId();
            RadarMcpDTO out = radars.getLatestForUser(uid)
                .orElse(new RadarMcpDTO(null, null, null, null, java.util.List.of()));
            meters.counter("mcp.tool.calls", "tool", "query_radar", "status", "success").increment();
            return out;
        } catch (RuntimeException e) {
            meters.counter("mcp.tool.calls", "tool", "query_radar", "status", "error").increment();
            throw e;
        }
    }
}
