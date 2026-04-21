package com.devradar.config;

import com.devradar.mcp.InterestMcpTools;
import com.devradar.mcp.RadarMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider devradarTools(RadarMcpTools radarTools,
                                              InterestMcpTools interestTools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(radarTools, interestTools)
            .build();
    }
}
