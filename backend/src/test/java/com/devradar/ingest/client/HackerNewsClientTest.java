package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HackerNewsClientTest {

    WireMockServer wm;
    HackerNewsClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new HackerNewsClient(RestClient.builder(), "http://localhost:" + wm.port());
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void fetchRecent_returnsParsedItems() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/search_by_date"))
            .willReturn(WireMock.okJson("""
                {
                  "hits": [
                    {
                      "objectID": "12345",
                      "title": "Spring Boot 3.5 released",
                      "url": "https://example.com/spring-boot-3-5",
                      "author": "rstoyanchev",
                      "created_at_i": 1755100000,
                      "points": 280
                    },
                    {
                      "objectID": "12346",
                      "title": "Show HN: htmx 2.1",
                      "url": "https://example.com/htmx",
                      "author": "carson",
                      "created_at_i": 1755103600,
                      "points": 95
                    }
                  ]
                }
                """)));

        List<FetchedItem> items = client.fetchRecent(50);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).externalId()).isEqualTo("12345");
        assertThat(items.get(0).title()).isEqualTo("Spring Boot 3.5 released");
        assertThat(items.get(0).url()).isEqualTo("https://example.com/spring-boot-3-5");
        assertThat(items.get(0).author()).isEqualTo("rstoyanchev");
        assertThat(items.get(1).externalId()).isEqualTo("12346");
    }

    @Test
    void fetchRecent_returnsEmpty_onEmptyHits() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/search_by_date"))
            .willReturn(WireMock.okJson("{\"hits\": []}")));

        assertThat(client.fetchRecent(50)).isEmpty();
    }
}
