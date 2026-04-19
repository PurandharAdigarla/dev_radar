package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GHSAClientTest {

    WireMockServer wm;
    GHSAClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new GHSAClient(RestClient.builder(), "http://localhost:" + wm.port());
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void fetchRecent_returnsParsedAdvisories() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/advisories"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "ghsa_id": "GHSA-xxxx-yyyy-zzzz",
                    "summary": "jackson-databind RCE in 2.16.x",
                    "html_url": "https://github.com/advisories/GHSA-xxxx-yyyy-zzzz",
                    "severity": "high",
                    "published_at": "2026-04-15T12:00:00Z",
                    "vulnerabilities": [{"package": {"ecosystem": "maven", "name": "com.fasterxml.jackson.core:jackson-databind"}}]
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchRecent();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).externalId()).isEqualTo("GHSA-xxxx-yyyy-zzzz");
        assertThat(items.get(0).title()).contains("jackson-databind");
        assertThat(items.get(0).url()).isEqualTo("https://github.com/advisories/GHSA-xxxx-yyyy-zzzz");
        assertThat(items.get(0).topics()).contains("security");
    }
}
