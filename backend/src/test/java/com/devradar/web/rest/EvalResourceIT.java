package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.*;
import com.devradar.repository.EvalRunRepository;
import com.devradar.repository.EvalScoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class EvalResourceIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EvalRunRepository evalRunRepository;
    @Autowired private EvalScoreRepository evalScoreRepository;
    @Autowired private ObjectMapper json;

    @Test
    void getRunsShouldReturnEvalHistory() throws Exception {
        var run = new EvalRun();
        run.setStatus(EvalRunStatus.COMPLETED);
        run.setRadarCount(5);
        run.setStartedAt(Instant.now().minusSeconds(120));
        run.setCompletedAt(Instant.now());
        run = evalRunRepository.save(run);

        var score = new EvalScore();
        score.setEvalRunId(run.getId());
        score.setCategory(EvalScoreCategory.RELEVANCE);
        score.setScore(new BigDecimal("0.850"));
        evalScoreRepository.save(score);

        mockMvc.perform(get("/api/evals/runs")
                        .header("Authorization", "Bearer " + registerAndLogin("eval-runs@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].radarCount").value(5))
                .andExpect(jsonPath("$[0].scores[0].category").value("RELEVANCE"))
                .andExpect(jsonPath("$[0].scores[0].score").value(0.85));
    }

    @Test
    void postRunShouldTriggerEvalAndReturn201() throws Exception {
        mockMvc.perform(post("/api/evals/run")
                        .header("Authorization", "Bearer " + registerAndLogin("eval-trigger@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"radarCount\": 5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void evalEndpointsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/evals/runs"))
                .andExpect(status().isUnauthorized());
    }

    private String registerAndLogin(String email) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
            "email", email, "password", "Password1!", "displayName", "Eval Tester"
        ));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        String resp = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "Password1!"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(resp);
        return node.get("accessToken").asText();
    }
}
