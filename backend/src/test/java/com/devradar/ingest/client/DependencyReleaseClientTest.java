package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyReleaseClientTest {

    WireMockServer wm;
    DependencyReleaseClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        String base = "http://localhost:" + wm.port();
        client = new DependencyReleaseClient(RestClient.builder(), base, base);
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void checkMaven_returnsLatestVersion() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/solrsearch/select"))
            .willReturn(WireMock.okJson("""
                {
                  "response": {
                    "docs": [
                      {"latestVersion": "2.17.0", "p": "jar", "timestamp": 1700000000000}
                    ]
                  }
                }
                """)));

        Optional<FetchedItem> item = client.checkForNewerVersion("MAVEN",
            "com.fasterxml.jackson.core:jackson-databind", "2.16.1");

        assertThat(item).isPresent();
        assertThat(item.get().title()).contains("jackson-databind");
        assertThat(item.get().title()).contains("2.17.0");
        assertThat(item.get().externalId()).isEqualTo("MAVEN:com.fasterxml.jackson.core:jackson-databind:2.17.0");
    }

    @Test
    void checkMaven_returnsEmpty_whenSameVersion() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/solrsearch/select"))
            .willReturn(WireMock.okJson("""
                {"response": {"docs": [{"latestVersion": "2.16.1"}]}}
                """)));

        Optional<FetchedItem> item = client.checkForNewerVersion("MAVEN",
            "com.fasterxml.jackson.core:jackson-databind", "2.16.1");

        assertThat(item).isEmpty();
    }

    @Test
    void checkNpm_returnsLatestVersion() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/react/latest"))
            .willReturn(WireMock.okJson("""
                {"version": "19.0.0", "description": "A JavaScript library for building user interfaces"}
                """)));

        Optional<FetchedItem> item = client.checkForNewerVersion("NPM", "react", "18.2.0");

        assertThat(item).isPresent();
        assertThat(item.get().title()).contains("react");
        assertThat(item.get().title()).contains("19.0.0");
        assertThat(item.get().externalId()).isEqualTo("NPM:react:19.0.0");
    }

    @Test
    void checkNpm_returnsEmpty_whenSameVersion() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/react/latest"))
            .willReturn(WireMock.okJson("""
                {"version": "18.2.0"}
                """)));

        assertThat(client.checkForNewerVersion("NPM", "react", "18.2.0")).isEmpty();
    }
}
