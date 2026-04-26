package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.repository.TeamMemberRepository;
import com.devradar.repository.TeamRepository;
import com.devradar.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class TeamResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepo;
    @Autowired TeamRepository teamRepo;
    @Autowired TeamMemberRepository memberRepo;

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
        return json.readTree(resp).get("accessToken").asText();
    }

    private Long getUserId(String email) {
        return userRepo.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void createTeam_returnsCreatedTeam() throws Exception {
        String token = registerAndLogin("team-create@example.com");

        String body = json.writeValueAsString(java.util.Map.of("name", "Alpha Team"));

        String resp = mvc.perform(post("/api/teams")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Alpha Team"))
            .andExpect(jsonPath("$.slug").value("alpha-team"))
            .andExpect(jsonPath("$.plan").value("TEAM"))
            .andExpect(jsonPath("$.memberCount").value(1))
            .andReturn().getResponse().getContentAsString();

        JsonNode node = json.readTree(resp);
        Long teamId = node.get("id").asLong();

        // Owner should be listed as member
        mvc.perform(get("/api/teams/" + teamId + "/members")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void createTeam_returns400_forBlankName() throws Exception {
        String token = registerAndLogin("team-blank@example.com");

        mvc.perform(post("/api/teams")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createTeam_returns401_withoutToken() throws Exception {
        mvc.perform(post("/api/teams")
                .contentType("application/json")
                .content("{\"name\":\"Test\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listTeams_returnsOnlyMyTeams() throws Exception {
        String token1 = registerAndLogin("team-list1@example.com");
        String token2 = registerAndLogin("team-list2@example.com");

        mvc.perform(post("/api/teams")
                .header("Authorization", "Bearer " + token1)
                .contentType("application/json")
                .content("{\"name\":\"Team One\"}"))
            .andExpect(status().isCreated());

        mvc.perform(post("/api/teams")
                .header("Authorization", "Bearer " + token2)
                .contentType("application/json")
                .content("{\"name\":\"Team Two\"}"))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/teams")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Team One"));
    }

    @Test
    void addMember_andRemoveMember_worksForAdmin() throws Exception {
        String ownerToken = registerAndLogin("team-owner-add@example.com");
        String memberToken = registerAndLogin("team-member-add@example.com");
        Long memberId = getUserId("team-member-add@example.com");

        String resp = mvc.perform(post("/api/teams")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content("{\"name\":\"Add Test\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long teamId = json.readTree(resp).get("id").asLong();

        // Add member
        String addBody = json.writeValueAsString(java.util.Map.of("userId", memberId, "role", "MEMBER"));
        mvc.perform(post("/api/teams/" + teamId + "/members")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content(addBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("MEMBER"));

        // List members - should have 2
        mvc.perform(get("/api/teams/" + teamId + "/members")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        // Remove member
        mvc.perform(delete("/api/teams/" + teamId + "/members/" + memberId)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isNoContent());

        // Members should be back to 1
        mvc.perform(get("/api/teams/" + teamId + "/members")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void addMember_returns403_forNonAdmin() throws Exception {
        String ownerToken = registerAndLogin("team-owner-auth@example.com");
        String memberToken = registerAndLogin("team-member-auth@example.com");
        String otherToken = registerAndLogin("team-other-auth@example.com");
        Long memberUserId = getUserId("team-member-auth@example.com");
        Long otherUserId = getUserId("team-other-auth@example.com");

        String resp = mvc.perform(post("/api/teams")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content("{\"name\":\"Auth Test\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long teamId = json.readTree(resp).get("id").asLong();

        // Add member (as owner)
        String addBody = json.writeValueAsString(java.util.Map.of("userId", memberUserId, "role", "MEMBER"));
        mvc.perform(post("/api/teams/" + teamId + "/members")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content(addBody))
            .andExpect(status().isCreated());

        // Member tries to add another user - should be 403
        String addOther = json.writeValueAsString(java.util.Map.of("userId", otherUserId, "role", "MEMBER"));
        mvc.perform(post("/api/teams/" + teamId + "/members")
                .header("Authorization", "Bearer " + memberToken)
                .contentType("application/json")
                .content(addOther))
            .andExpect(status().isForbidden());
    }

    @Test
    void getTeam_returns404_forNonExistent() throws Exception {
        String token = registerAndLogin("team-404@example.com");

        mvc.perform(get("/api/teams/999999")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void getTeam_returns403_forNonMember() throws Exception {
        String ownerToken = registerAndLogin("team-owner-403@example.com");
        String otherToken = registerAndLogin("team-other-403@example.com");

        String resp = mvc.perform(post("/api/teams")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content("{\"name\":\"Private Team\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long teamId = json.readTree(resp).get("id").asLong();

        mvc.perform(get("/api/teams/" + teamId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isForbidden());
    }
}
