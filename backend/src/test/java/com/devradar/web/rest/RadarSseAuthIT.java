package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RadarSseAuthIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;

    @Test
    void streamEndpointAcceptsQueryParamToken() throws Exception {
        var body = json.writeValueAsString(java.util.Map.of(
            "email", "sse-qp@test.com", "password", "Password1!", "displayName", "Sse"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        var loginResp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                    "email", "sse-qp@test.com", "password", "Password1!"))))
            .andReturn().getResponse().getContentAsString();
        var tok = json.readTree(loginResp).get("accessToken").asText();

        var createResp = mvc.perform(post("/api/radars").header("Authorization", "Bearer " + tok))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        var radarId = json.readTree(createResp).get("id").asLong();

        mvc.perform(get("/api/radars/" + radarId + "/stream").param("token", tok))
            .andExpect(status().isOk());
    }

    @Test
    void streamEndpointRejectsMissingToken() throws Exception {
        mvc.perform(get("/api/radars/999/stream"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void otherEndpointsDoNotAcceptQueryParamToken() throws Exception {
        var body = json.writeValueAsString(java.util.Map.of(
            "email", "qp-rest@test.com", "password", "Password1!", "displayName", "Qp"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        var loginResp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                    "email", "qp-rest@test.com", "password", "Password1!"))))
            .andReturn().getResponse().getContentAsString();
        var tok = json.readTree(loginResp).get("accessToken").asText();

        mvc.perform(get("/api/users/me").param("token", tok))
            .andExpect(status().isUnauthorized());
    }
}
