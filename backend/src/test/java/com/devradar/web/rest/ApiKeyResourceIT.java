package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ApiKeyResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void createReturnsRawKeyOnceAndListReturnsSummariesWithoutIt() throws Exception {
        String token = registerAndLogin("apikey-rest@test.com");

        MvcResult create = mvc.perform(post("/api/users/me/api-keys")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cursor\",\"scope\":\"READ\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.startsWith("devr_")))
            .andExpect(jsonPath("$.keyPrefix").exists())
            .andExpect(jsonPath("$.scope").value("READ"))
            .andReturn();

        JsonNode created = json.readTree(create.getResponse().getContentAsString());
        Long keyId = created.get("id").asLong();

        MvcResult list = mvc.perform(get("/api/users/me/api-keys")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Cursor"))
            .andExpect(jsonPath("$[0].scope").value("READ"))
            .andReturn();

        assertThat(list.getResponse().getContentAsString()).doesNotContain("\"key\"");

        mvc.perform(delete("/api/users/me/api-keys/" + keyId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/users/me/api-keys")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createRequiresAuth() throws Exception {
        mvc.perform(post("/api/users/me/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"scope\":\"READ\"}"))
            .andExpect(status().isUnauthorized());
    }

    private String registerAndLogin(String email) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
            "email", email, "password", "Password1!", "displayName", "Api Rest"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        String resp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "Password1!"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("accessToken").asText();
    }
}
