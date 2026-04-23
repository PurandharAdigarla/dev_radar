package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RssFeedClientTest {

    WireMockServer wm;
    RssFeedClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new RssFeedClient();
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void fetch_parsesRss20Feed() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/feed"))
            .willReturn(WireMock.okXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Spring Blog</title>
                    <item>
                      <title>Spring Boot 3.5 Released</title>
                      <link>https://spring.io/blog/2026/04/20/spring-boot-3-5</link>
                      <description>Major release with virtual thread support.</description>
                      <author>Spring Team</author>
                      <pubDate>Sun, 20 Apr 2026 10:00:00 GMT</pubDate>
                      <guid>https://spring.io/blog/2026/04/20/spring-boot-3-5</guid>
                    </item>
                  </channel>
                </rss>
                """)));

        List<FetchedItem> items = client.fetch("http://localhost:" + wm.port() + "/feed");

        assertThat(items).hasSize(1);
        FetchedItem item = items.get(0);
        assertThat(item.title()).isEqualTo("Spring Boot 3.5 Released");
        assertThat(item.url()).isEqualTo("https://spring.io/blog/2026/04/20/spring-boot-3-5");
        assertThat(item.description()).isEqualTo("Major release with virtual thread support.");
        assertThat(item.author()).isEqualTo("Spring Team");
        assertThat(item.externalId()).isEqualTo("https://spring.io/blog/2026/04/20/spring-boot-3-5");
        assertThat(item.postedAt()).isNotNull();
    }

    @Test
    void fetch_parsesAtomFeed() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/atom"))
            .willReturn(WireMock.okXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Go Blog</title>
                  <entry>
                    <title>Go 1.23 is released</title>
                    <link href="https://go.dev/blog/go1.23"/>
                    <id>tag:go.dev,2026:go1.23</id>
                    <summary>New features in Go 1.23.</summary>
                    <author><name>Go Team</name></author>
                    <published>2026-04-18T12:00:00Z</published>
                  </entry>
                </feed>
                """)));

        List<FetchedItem> items = client.fetch("http://localhost:" + wm.port() + "/atom");

        assertThat(items).hasSize(1);
        FetchedItem item = items.get(0);
        assertThat(item.title()).isEqualTo("Go 1.23 is released");
        assertThat(item.url()).isEqualTo("https://go.dev/blog/go1.23");
        assertThat(item.externalId()).isEqualTo("tag:go.dev,2026:go1.23");
        assertThat(item.description()).isEqualTo("New features in Go 1.23.");
        assertThat(item.author()).isEqualTo("Go Team");
    }

    @Test
    void fetch_returnsEmpty_onMalformedFeed() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/bad"))
            .willReturn(WireMock.ok("this is not xml")));

        List<FetchedItem> items = client.fetch("http://localhost:" + wm.port() + "/bad");

        assertThat(items).isEmpty();
    }

    @Test
    void fetch_truncatesLongDescription() {
        String longDesc = "x".repeat(3000);
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/long"))
            .willReturn(WireMock.okXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Long Post</title>
                      <link>https://example.com/long</link>
                      <description>%s</description>
                      <pubDate>Sun, 20 Apr 2026 10:00:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """.formatted(longDesc))));

        List<FetchedItem> items = client.fetch("http://localhost:" + wm.port() + "/long");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).description()).hasSize(2048);
    }

    @Test
    void fetch_skipsEntriesWithoutLink() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/nolink"))
            .willReturn(WireMock.okXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>No Link Item</title>
                      <pubDate>Sun, 20 Apr 2026 10:00:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """)));

        assertThat(client.fetch("http://localhost:" + wm.port() + "/nolink")).isEmpty();
    }
}
