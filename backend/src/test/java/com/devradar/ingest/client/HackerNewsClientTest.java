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
    void fetchRecent_returnsParsedItems_withDescription() {
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
                      "points": 280,
                      "num_comments": 142
                    },
                    {
                      "objectID": "12346",
                      "title": "Ask HN: Best practices for Java 21?",
                      "url": "https://news.ycombinator.com/item?id=12346",
                      "author": "javadev",
                      "created_at_i": 1755103600,
                      "points": 95,
                      "num_comments": 67,
                      "story_text": "I recently started using virtual threads and pattern matching. What other Java 21 features are you using in production?"
                    }
                  ]
                }
                """)));

        List<FetchedItem> items = client.fetchRecent(50);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("Spring Boot 3.5 released");
        assertThat(items.get(0).description()).isEqualTo("280 points, 142 comments on Hacker News");
        assertThat(items.get(1).description()).startsWith("I recently started using virtual threads");
    }

    @Test
    void fetchRecent_returnsEmpty_onEmptyHits() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/search_by_date"))
            .willReturn(WireMock.okJson("{\"hits\": []}")));

        assertThat(client.fetchRecent(50)).isEmpty();
    }
}
