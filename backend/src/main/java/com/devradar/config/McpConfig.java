package com.devradar.config;

import com.devradar.mcp.ActionMcpTools;
import com.devradar.mcp.InterestMcpTools;
import com.devradar.mcp.RadarMcpTools;
import com.devradar.mcp.RecentItemsMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider devradarTools(RadarMcpTools radarTools,
                                              InterestMcpTools interestTools,
                                              RecentItemsMcpTools recentTools,
                                              ActionMcpTools actionTools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(radarTools, interestTools, recentTools, actionTools)
            .build();
    }
}
