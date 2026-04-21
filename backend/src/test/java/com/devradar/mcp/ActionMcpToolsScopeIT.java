package com.devradar.mcp;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.ApiKeyScope;
import com.devradar.security.ApiKeyAuthenticationToken;
import com.devradar.security.ApiKeyPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionMcpToolsScopeIT extends AbstractIntegrationTest {

    @Autowired ActionMcpTools tool;
    @Autowired ToolCallbackProvider toolCallbackProvider;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void proposePrForCveToolIsRegistered() {
        List<String> toolNames = Arrays.stream(toolCallbackProvider.getToolCallbacks())
            .map(cb -> cb.getToolDefinition().name())
            .toList();
        assertThat(toolNames).contains("propose_pr_for_cve");
    }

    @Test
    void readScopeKeyCannotCallProposePrForCve() {
        SecurityContextHolder.getContext().setAuthentication(
            new ApiKeyAuthenticationToken(new ApiKeyPrincipal(7L, 1L, ApiKeyScope.READ)));

        assertThatThrownBy(() -> tool.proposePrForCve(1L, "2.17.0"))
            .isInstanceOf(McpScopeException.class)
            .hasMessageContaining("WRITE");
    }
}
