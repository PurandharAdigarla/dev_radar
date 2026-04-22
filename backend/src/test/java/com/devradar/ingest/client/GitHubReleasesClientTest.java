package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubReleasesClientTest {

    WireMockServer wm;
    GitHubReleasesClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new GitHubReleasesClient(RestClient.builder(), "http://localhost:" + wm.port());
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void fetchReleases_returnsParsedReleases() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/facebook/react/releases"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "tag_name": "v19.1.0",
                    "name": "Compiler improvements",
                    "html_url": "https://github.com/facebook/react/releases/tag/v19.1.0",
                    "body": "## What's Changed\\n- Improved compiler performance\\n- Fixed hydration bugs",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-20T10:00:00Z",
                    "author": { "login": "gaearon" }
                  },
                  {
                    "tag_name": "v19.0.0",
                    "name": "v19.0.0",
                    "html_url": "https://github.com/facebook/react/releases/tag/v19.0.0",
                    "body": "Major release",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-10T10:00:00Z",
                    "author": { "login": "gaearon" }
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchReleases("facebook/react", List.of("react", "frontend"));

        assertThat(items).hasSize(2);

        FetchedItem first = items.get(0);
        assertThat(first.externalId()).isEqualTo("facebook/react:v19.1.0");
        assertThat(first.url()).isEqualTo("https://github.com/facebook/react/releases/tag/v19.1.0");
        assertThat(first.title()).isEqualTo("react v19.1.0 — Compiler improvements");
        assertThat(first.description()).contains("Improved compiler performance");
        assertThat(first.author()).isEqualTo("gaearon");
        assertThat(first.topics()).containsExactlyInAnyOrder("react", "frontend");
        assertThat(first.rawPayload()).isNotNull();
    }

    @Test
    void fetchReleases_skipsDraftsAndPrereleases() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/owner/repo/releases"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "tag_name": "v2.0.0-beta.1",
                    "name": "Beta",
                    "html_url": "https://github.com/owner/repo/releases/tag/v2.0.0-beta.1",
                    "body": "Beta release",
                    "draft": false,
                    "prerelease": true,
                    "published_at": "2026-04-20T10:00:00Z",
                    "author": { "login": "dev" }
                  },
                  {
                    "tag_name": "v2.0.0-draft",
                    "name": "Draft",
                    "html_url": "https://github.com/owner/repo/releases/tag/v2.0.0-draft",
                    "body": "Draft release",
                    "draft": true,
                    "prerelease": false,
                    "published_at": "2026-04-20T10:00:00Z",
                    "author": { "login": "dev" }
                  },
                  {
                    "tag_name": "v1.5.0",
                    "name": "v1.5.0",
                    "html_url": "https://github.com/owner/repo/releases/tag/v1.5.0",
                    "body": "Stable release",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-18T10:00:00Z",
                    "author": { "login": "dev" }
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchReleases("owner/repo", List.of("tool"));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).externalId()).isEqualTo("owner/repo:v1.5.0");
    }

    @Test
    void fetchReleases_titleUsesTagNameWhenNameMatches() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/owner/repo/releases"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "tag_name": "v3.0.0",
                    "name": "v3.0.0",
                    "html_url": "https://github.com/owner/repo/releases/tag/v3.0.0",
                    "body": "Release notes",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-20T10:00:00Z",
                    "author": { "login": "dev" }
                  }
                ]
                """)));

        List<FetchedItem> items = client.fetchReleases("owner/repo", List.of());

        assertThat(items.get(0).title()).isEqualTo("repo v3.0.0");
    }

    @Test
    void fetchReleases_truncatesLongBody() {
        String longBody = "x".repeat(3000);
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/owner/repo/releases"))
            .willReturn(WireMock.okJson("""
                [
                  {
                    "tag_name": "v1.0.0",
                    "name": "v1.0.0",
                    "html_url": "https://github.com/owner/repo/releases/tag/v1.0.0",
                    "body": "%s",
                    "draft": false,
                    "prerelease": false,
                    "published_at": "2026-04-20T10:00:00Z",
                    "author": { "login": "dev" }
                  }
                ]
                """.formatted(longBody))));

        List<FetchedItem> items = client.fetchReleases("owner/repo", List.of());

        assertThat(items.get(0).description()).hasSize(2000);
    }

    @Test
    void fetchReleases_returnsEmptyListOnNullOrEmptyResponse() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/owner/repo/releases"))
            .willReturn(WireMock.okJson("[]")));

        List<FetchedItem> items = client.fetchReleases("owner/repo", List.of());

        assertThat(items).isEmpty();
    }
}
