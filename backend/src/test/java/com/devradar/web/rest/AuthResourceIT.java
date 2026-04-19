package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AuthResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void register_returns201_andLoginReturnsTokens() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
            "email", "alice@example.com",
            "password", "Password1!",
            "displayName", "Alice"
        ));

        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated());

        String loginBody = json.writeValueAsString(java.util.Map.of(
            "email", "alice@example.com",
            "password", "Password1!"
        ));

        mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void register_returns409_whenEmailDuplicated() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
            "email", "dup@example.com",
            "password", "Password1!",
            "displayName", "Dup"
        ));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void login_returns401_whenPasswordWrong() throws Exception {
        String reg = json.writeValueAsString(java.util.Map.of(
            "email", "wrong@example.com",
            "password", "RealPass1!",
            "displayName", "X"
        ));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(reg))
            .andExpect(status().isCreated());

        String bad = json.writeValueAsString(java.util.Map.of(
            "email", "wrong@example.com",
            "password", "BadPass!"
        ));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(bad))
            .andExpect(status().isUnauthorized());
    }
}
