package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class NotificationResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

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

    @Test
    void get_returnsDefaultPreferences() throws Exception {
        String token = registerAndLogin("notif1@example.com");
        mvc.perform(get("/api/users/me/notifications").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailEnabled").value(false))
            .andExpect(jsonPath("$.digestDayOfWeek").value(1))
            .andExpect(jsonPath("$.digestHourUtc").value(9));
    }

    @Test
    void put_updatesPreferences() throws Exception {
        String token = registerAndLogin("notif2@example.com");
        String body = json.writeValueAsString(java.util.Map.of(
            "emailEnabled", true,
            "emailAddress", "custom@example.com",
            "digestDayOfWeek", 5,
            "digestHourUtc", 14
        ));
        mvc.perform(put("/api/users/me/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailEnabled").value(true))
            .andExpect(jsonPath("$.emailAddress").value("custom@example.com"))
            .andExpect(jsonPath("$.digestDayOfWeek").value(5))
            .andExpect(jsonPath("$.digestHourUtc").value(14));

        mvc.perform(get("/api/users/me/notifications").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailEnabled").value(true))
            .andExpect(jsonPath("$.emailAddress").value("custom@example.com"));
    }

    @Test
    void get_returns401_whenNoToken() throws Exception {
        mvc.perform(get("/api/users/me/notifications"))
            .andExpect(status().isUnauthorized());
    }
}
