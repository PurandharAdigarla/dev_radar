package com.devradar.ingest.client;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GitHubTrendingClient {

    private final RestClient http;

    public GitHubTrendingClient(RestClient.Builder builder,
                                @Value("${devradar.gh-trending.base-url:https://github.com}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public List<FetchedItem> fetchTrending(String language) {
        String path = (language == null || language.isBlank()) ? "/trending" : "/trending/" + language.toLowerCase(Locale.ROOT);
        String html = http.get().uri(path).retrieve().body(String.class);
        if (html == null) return List.of();

        Document doc = Jsoup.parse(html);
        List<FetchedItem> out = new ArrayList<>();
        Instant now = Instant.now();
        for (Element card : doc.select("article.Box-row")) {
            Element link = card.selectFirst("h2 a");
            if (link == null) continue;
            String href = link.attr("href").trim();
            if (href.startsWith("/")) href = href.substring(1);
            String url = "https://github.com/" + href;

            String repoName = href.contains("/") ? href.substring(href.lastIndexOf('/') + 1) : href;

            Element descEl = card.selectFirst("p");
            String repoDesc = descEl != null ? descEl.text().trim() : "";

            Element starsEl = card.selectFirst("a[href$=/stargazers]");
            String stars = starsEl != null ? starsEl.text().trim() : "";

            String description = buildDescription(repoDesc, stars);

            Element langEl = card.selectFirst("[itemprop=programmingLanguage]");
            List<String> topics = new ArrayList<>();
            if (langEl != null) topics.add(langEl.text().toLowerCase(Locale.ROOT));

            out.add(new FetchedItem(href, url, repoName, description, null, now, null, topics));
        }
        return out;
    }

    private static String buildDescription(String repoDesc, String stars) {
        StringBuilder sb = new StringBuilder();
        if (!repoDesc.isEmpty()) sb.append(repoDesc);
        if (!stars.isEmpty()) {
            if (sb.length() > 0) sb.append(". ");
            sb.append(stars).append(" stars on GitHub");
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
