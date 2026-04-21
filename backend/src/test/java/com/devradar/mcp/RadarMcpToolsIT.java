package com.devradar.mcp;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.User;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.UserRepository;
import com.devradar.apikey.ApiKeyService;
import com.devradar.mcp.dto.RadarMcpDTO;
import com.devradar.security.ApiKeyAuthenticationToken;
import com.devradar.security.ApiKeyPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class RadarMcpToolsIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RadarRepository radars;
    @Autowired ApiKeyService apiKeys;
    @Autowired ToolCallbackProvider toolCallbackProvider;
    @Autowired RadarMcpTools radarMcpTools;
    @Autowired InterestMcpTools interestMcpTools;

    @Test
    void queryRadarToolRequiresApiKey() throws Exception {
        String envelope = json.writeValueAsString(java.util.Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "tools/call",
            "params", java.util.Map.of("name", "query_radar", "arguments", java.util.Map.of())
        ));

        mvc.perform(post("/mcp/message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void queryRadarAndUserInterestsToolsAreRegisteredWithProvider() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        var names = Arrays.stream(callbacks)
            .map(cb -> cb.getToolDefinition().name())
            .toList();
        assertThat(names).contains("query_radar", "get_user_interests");
    }

    @Test
    void queryRadarReturnsLatestRadarForAuthenticatedUser() {
        User u = new User();
        u.setEmail("mcp-query@test.com");
        u.setDisplayName("Mcp");
        u.setPasswordHash("h");
        u.setActive(true);
        u = users.save(u);

        Radar r = new Radar();
        r.setUserId(u.getId());
        r.setStatus(RadarStatus.READY);
        r.setPeriodStart(Instant.now().minusSeconds(604800));
        r.setPeriodEnd(Instant.now());
        r.setGeneratedAt(Instant.now());
        r.setGenerationMs(1000L);
        r.setTokenCount(100);
        radars.save(r);

        var generated = apiKeys.generate(u.getId(), "test-key", ApiKeyScope.READ);

        ApiKeyPrincipal principal = new ApiKeyPrincipal(u.getId(), generated.id(), ApiKeyScope.READ);
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken(principal));
        try {
            RadarMcpDTO dto = radarMcpTools.queryRadar();
            assertThat(dto).isNotNull();
            assertThat(dto.radarId()).isNotNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
