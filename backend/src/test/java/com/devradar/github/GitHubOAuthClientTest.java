package com.devradar.github;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubOAuthClientTest {

    WireMockServer wm;
    GitHubOAuthClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new GitHubOAuthClient(
            RestClient.builder(),
            "test-cid",
            "test-secret",
            "http://localhost:8080/api/auth/github/callback",
            "http://localhost:" + wm.port() + "/login/oauth/access_token"
        );
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void exchangeCode_returnsTokenAndScopes() {
        wm.stubFor(WireMock.post(WireMock.urlPathEqualTo("/login/oauth/access_token"))
            .willReturn(WireMock.okJson("""
                {"access_token":"gho_abc123","token_type":"bearer","scope":"read:user,repo"}
                """)));

        GitHubOAuthClient.AccessTokenResponse r = client.exchangeCode("the-code-from-callback");

        assertThat(r.accessToken()).isEqualTo("gho_abc123");
        assertThat(r.grantedScopes()).contains("repo");
    }
}
