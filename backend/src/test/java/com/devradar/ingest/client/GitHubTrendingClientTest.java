package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubTrendingClientTest {

    WireMockServer wm;
    GitHubTrendingClient client;
    String sampleHtml;

    @BeforeEach
    void setup() throws IOException {
        wm = new WireMockServer(0);
        wm.start();
        client = new GitHubTrendingClient(RestClient.builder(), "http://localhost:" + wm.port());
        sampleHtml = Files.readString(Path.of("src/test/resources/github-trending-sample.html"));
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void fetchTrending_parsesRepoCards_withDescriptionAndTitle() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/trending"))
            .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "text/html").withBody(sampleHtml)));

        List<FetchedItem> items = client.fetchTrending(null);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).externalId()).isEqualTo("spring-projects/spring-boot");
        assertThat(items.get(0).title()).isEqualTo("spring-boot");
        assertThat(items.get(0).description()).contains("Spring Boot makes it easy");
        assertThat(items.get(0).description()).contains("78,200 stars");
        assertThat(items.get(0).url()).isEqualTo("https://github.com/spring-projects/spring-boot");
        assertThat(items.get(0).topics()).contains("java");
        assertThat(items.get(1).title()).isEqualTo("htmx");
        assertThat(items.get(1).description()).contains("htmx - high power tools for HTML");
    }

    @Test
    void fetchTrending_filtersByLanguage() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/trending/java"))
            .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "text/html").withBody(sampleHtml)));

        List<FetchedItem> items = client.fetchTrending("java");

        assertThat(items).isNotEmpty();
    }
}
