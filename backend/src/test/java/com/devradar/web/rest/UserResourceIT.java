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
class UserResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String registerAndLogin(String email) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
            "email", email, "password", "Password1!", "displayName", "Alice"
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
    void me_returnsAuthenticatedUser() throws Exception {
        String token = registerAndLogin("me1@example.com");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("me1@example.com"))
            .andExpect(jsonPath("$.displayName").value("Alice"));
    }

    @Test
    void me_returns401_whenNoToken() throws Exception {
        mvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMe_changesDisplayName() throws Exception {
        String token = registerAndLogin("me2@example.com");
        mvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("displayName", "Bob"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Bob"));
    }
}
