package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.RadarTheme;
import com.devradar.domain.User;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class EngagementResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepo;
    @Autowired RadarRepository radarRepo;
    @Autowired RadarThemeRepository themeRepo;
    @Autowired PasswordEncoder passwordEncoder;

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
        theme.setTitle("Spring Boot 3.5 release");
        theme.setSummary("Major release.");
        theme.setDisplayOrder(0);
        themeRepo.save(theme);

        return r.getId();
    }

    @Test
    void post_createsEngagementEvent_andGet_returnsSummary() throws Exception {
        String token = registerAndLogin("engage1@example.com");
        Long userId = getUserId("engage1@example.com");
        Long radarId = createRadar(userId);

        String body = json.writeValueAsString(java.util.Map.of(
            "radarId", radarId,
            "themeIndex", 0,
            "eventType", "THUMBS_UP"
        ));

        mvc.perform(post("/api/engagement")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/engagement/summary")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalInteractions").value(1))
            .andExpect(jsonPath("$.thumbsUpThemes[0]").value("Spring Boot 3.5 release"));
    }

    @Test
    void post_returns400_forInvalidEventType() throws Exception {
        String token = registerAndLogin("engage2@example.com");
        Long userId = getUserId("engage2@example.com");
        Long radarId = createRadar(userId);

        String body = json.writeValueAsString(java.util.Map.of(
            "radarId", radarId,
            "themeIndex", 0,
            "eventType", "INVALID_TYPE"
        ));

        mvc.perform(post("/api/engagement")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void get_returns401_whenNoToken() throws Exception {
        mvc.perform(get("/api/engagement/summary"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void post_returns401_whenNoToken() throws Exception {
        mvc.perform(post("/api/engagement")
                .contentType("application/json")
                .content("{\"radarId\":1,\"themeIndex\":0,\"eventType\":\"CLICK\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void summary_returnsEmptyProfile_whenNoEvents() throws Exception {
        String token = registerAndLogin("engage3@example.com");

        mvc.perform(get("/api/engagement/summary")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalInteractions").value(0))
            .andExpect(jsonPath("$.thumbsUpThemes").isEmpty())
            .andExpect(jsonPath("$.thumbsDownThemes").isEmpty());
    }
}
