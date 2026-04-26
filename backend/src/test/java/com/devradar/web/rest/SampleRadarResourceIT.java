package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.RadarTheme;
import com.devradar.domain.User;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class SampleRadarResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepo;
    @Autowired RadarRepository radarRepo;
    @Autowired RadarThemeRepository themeRepo;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void get_returns204_whenNoPublicRadarExists() throws Exception {
        mvc.perform(get("/api/sample-radar"))
            .andExpect(status().isNoContent());
    }

    @Test
    void get_returnsPublicRadar_anonymously() throws Exception {
        // Create a user and a public radar
        User u = new User();
        u.setEmail("sample-radar-user@example.com");
        u.setPasswordHash(passwordEncoder.encode("Password1!"));
        u.setDisplayName("SampleUser");
        u = userRepo.save(u);

        Radar r = new Radar();
        r.setUserId(u.getId());
        r.setStatus(RadarStatus.READY);
        r.setPeriodStart(Instant.now().minusSeconds(604800));
        r.setPeriodEnd(Instant.now());
        r.setGeneratedAt(Instant.now());
        r.setGenerationMs(500L);
        r.setTokenCount(100);
        r.setInputTokenCount(60);
        r.setOutputTokenCount(40);
        r.setPublic(true);
        r.setShareToken("sample-token-123");
        r = radarRepo.save(r);

        RadarTheme theme = new RadarTheme();
        theme.setRadarId(r.getId());
        theme.setTitle("Test Theme");
        theme.setSummary("Test summary.");
        theme.setDisplayOrder(0);
        themeRepo.save(theme);

        // Anonymous access should work
        mvc.perform(get("/api/sample-radar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(r.getId()))
            .andExpect(jsonPath("$.themes[0].title").value("Test Theme"));
    }
}
