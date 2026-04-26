package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.RadarTheme;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ShareableRadarIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepo;
    @Autowired RadarRepository radarRepo;
    @Autowired RadarThemeRepository themeRepo;

    private String registerAndLogin(String email) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
            "email", email, "password", "Password1!", "displayName", "TestUser"
        ));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated());

        String resp = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "Password1!"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(resp);
        return node.get("accessToken").asText();
    }

    private Long getUserId(String email) {
        return userRepo.findByEmail(email).orElseThrow().getId();
    }

    private Long createRadar(Long userId) {
        Radar r = new Radar();
        r.setUserId(userId);
        r.setStatus(RadarStatus.READY);
        r.setPeriodStart(Instant.now().minusSeconds(604800));
        r.setPeriodEnd(Instant.now());
        r.setGeneratedAt(Instant.now());
        r.setGenerationMs(500L);
        r.setTokenCount(100);
        r.setInputTokenCount(60);
        r.setOutputTokenCount(40);
        r = radarRepo.save(r);

        RadarTheme theme = new RadarTheme();
        theme.setRadarId(r.getId());
        theme.setTitle("Shareable Theme");
        theme.setSummary("A theme for sharing.");
        theme.setDisplayOrder(0);
        themeRepo.save(theme);

        return r.getId();
    }

    @Test
    void shareFlow_generatesToken_andAllowsAnonymousAccess() throws Exception {
        String token = registerAndLogin("share1@example.com");
        Long userId = getUserId("share1@example.com");
        Long radarId = createRadar(userId);

        // Share the radar
        String shareResp = mvc.perform(post("/api/radars/" + radarId + "/share")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shareToken").isNotEmpty())
            .andExpect(jsonPath("$.shareUrl").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        JsonNode shareNode = json.readTree(shareResp);
        String shareToken = shareNode.get("shareToken").asText();

        // Anonymous access via share token
        mvc.perform(get("/api/radars/shared/" + shareToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(radarId))
            .andExpect(jsonPath("$.themes[0].title").value("Shareable Theme"));
    }

    @Test
    void shareFlow_returnsExistingToken_onSecondShare() throws Exception {
        String token = registerAndLogin("share2@example.com");
        Long userId = getUserId("share2@example.com");
        Long radarId = createRadar(userId);

        // Share twice
        String resp1 = mvc.perform(post("/api/radars/" + radarId + "/share")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String resp2 = mvc.perform(post("/api/radars/" + radarId + "/share")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String token1 = json.readTree(resp1).get("shareToken").asText();
        String token2 = json.readTree(resp2).get("shareToken").asText();
        assert token1.equals(token2) : "Share token should be stable across multiple share calls";
    }

    @Test
    void getShared_returns404_forInvalidToken() throws Exception {
        mvc.perform(get("/api/radars/shared/nonexistent-token"))
            .andExpect(status().isNotFound());
    }

    @Test
    void share_returns401_whenNotAuthenticated() throws Exception {
        mvc.perform(post("/api/radars/1/share"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void share_returns403_whenNotOwner() throws Exception {
        String token1 = registerAndLogin("share-owner@example.com");
        Long user1Id = getUserId("share-owner@example.com");
        Long radarId = createRadar(user1Id);

        String token2 = registerAndLogin("share-other@example.com");

        mvc.perform(post("/api/radars/" + radarId + "/share")
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isForbidden());
    }
}
