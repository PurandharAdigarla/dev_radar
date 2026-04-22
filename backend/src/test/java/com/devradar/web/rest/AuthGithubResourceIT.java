package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.crypto.TokenEncryptor;
import com.devradar.repository.UserGithubIdentityRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthGithubResourceIT extends AbstractIntegrationTest {

    static WireMockServer wm;

    @BeforeAll
    static void startWm() {
        wm = new WireMockServer(0);
        wm.start();
    }

    @AfterAll
    static void stopWm() {
        if (wm != null) wm.stop();
    }

    @DynamicPropertySource
    static void overrideGithubUrls(DynamicPropertyRegistry r) {
        r.add("github.oauth.token-url", () -> "http://localhost:" + wm.port() + "/login/oauth/access_token");
        r.add("github.api.base-url", () -> "http://localhost:" + wm.port());
    }

    @Autowired MockMvc mvc;
    @Autowired UserGithubIdentityRepository identityRepo;
    @Autowired TokenEncryptor encryptor;

    @Test
    void githubStart_redirectsToGithub() throws Exception {
        mvc.perform(get("/api/auth/github/start"))
            .andExpect(status().is3xxRedirection())
            .andExpect(result -> {
                String loc = result.getResponse().getHeader("Location");
                assertThat(loc).contains("client_id=").contains("scope=").contains("state=");
            });
    }

    @Test
    void githubCallback_exchangesCode_createsUserAndIdentity_returnsToken() throws Exception {
        var startResp = mvc.perform(get("/api/auth/github/start")).andReturn().getResponse();
        String location = startResp.getHeader("Location");
        String state = location.substring(location.indexOf("state=") + 6);

        wm.stubFor(WireMock.post(WireMock.urlPathEqualTo("/login/oauth/access_token"))
            .willReturn(WireMock.okJson("""
                {"access_token":"gho_callback_test","token_type":"bearer","scope":"read:user,repo"}
                """)));
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/user"))
            .willReturn(WireMock.okJson("""
                {"login":"alice","id":7777}
                """)));

        var callbackResp = mvc.perform(get("/api/auth/github/callback")
                .param("code", "the-code")
                .param("state", state))
            .andExpect(status().isFound())
            .andReturn().getResponse();

        String callbackLocation = callbackResp.getHeader("Location");
        assertThat(callbackLocation).startsWith("http://localhost:5173/auth/github/complete#accessToken=");
        String jwt = callbackLocation.substring(callbackLocation.indexOf("accessToken=") + "accessToken=".length());
        assertThat(jwt).isNotBlank();

        var identity = identityRepo.findByGithubUserId(7777L).orElseThrow();
        assertThat(identity.getGithubLogin()).isEqualTo("alice");
        assertThat(identity.getAccessTokenEncrypted()).isNotEqualTo("gho_callback_test");
        assertThat(encryptor.decrypt(identity.getAccessTokenEncrypted())).isEqualTo("gho_callback_test");
        assertThat(identity.getGrantedScopes()).contains("repo");
    }
}
