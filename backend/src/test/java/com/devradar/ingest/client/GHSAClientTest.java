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
    void fetchRecent_returnsParsedAdvisories_withStructuredDescription() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/advisories"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "ghsa_id": "GHSA-xxxx-yyyy-zzzz",
                    "cve_id": "CVE-2026-12345",
                    "summary": "jackson-databind RCE in 2.16.x",
                    "html_url": "https://github.com/advisories/GHSA-xxxx-yyyy-zzzz",
                    "severity": "high",
                    "published_at": "2026-04-15T12:00:00Z",
                    "vulnerabilities": [{
                      "package": {"ecosystem": "maven", "name": "com.fasterxml.jackson.core:jackson-databind"},
                      "vulnerable_version_range": "< 2.16.3",
                      "patched_versions": "2.16.3"
                    }]
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchRecent();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).contains("jackson-databind");
        assertThat(items.get(0).description()).contains("HIGH");
        assertThat(items.get(0).description()).contains("jackson-databind");
        assertThat(items.get(0).description()).contains("2.16.3");
        assertThat(items.get(0).description()).contains("CVE-2026-12345");
        assertThat(items.get(0).topics()).contains("security");
    }

    @Test
    void fetchRecent_handlesAdvisoryWithoutVulnerabilities() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/advisories"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "ghsa_id": "GHSA-aaaa-bbbb-cccc",
                    "summary": "Some generic advisory",
                    "html_url": "https://github.com/advisories/GHSA-aaaa-bbbb-cccc",
                    "severity": "low",
                    "published_at": "2026-04-16T12:00:00Z"
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchRecent();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).description()).contains("LOW");
    }
}
