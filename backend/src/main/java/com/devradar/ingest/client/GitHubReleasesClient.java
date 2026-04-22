package com.devradar.ingest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GitHubReleasesClient {

    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final RestClient http;

    public GitHubReleasesClient(RestClient.Builder builder,
                                @Value("${devradar.gh-releases.base-url:https://api.github.com}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public List<FetchedItem> fetchReleases(String repoFullName, List<String> topics) {
        return fetchReleases(repoFullName, topics, null);
    }

    public List<FetchedItem> fetchReleases(String repoFullName, List<String> topics, String token) {
        var req = http.get()
            .uri(uri -> uri.path("/repos/" + repoFullName + "/releases")
                .queryParam("per_page", "3").build());
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        JsonNode arr = req.retrieve().body(JsonNode.class);

        List<FetchedItem> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;

        String repoShortName = repoFullName.contains("/")
            ? repoFullName.substring(repoFullName.lastIndexOf('/') + 1)
            : repoFullName;

        for (JsonNode rel : arr) {
            if (rel.path("draft").asBoolean(false)) continue;
            if (rel.path("prerelease").asBoolean(false)) continue;

            String tagName = textOrNull(rel, "tag_name");
            String htmlUrl = textOrNull(rel, "html_url");
            String publishedAt = textOrNull(rel, "published_at");
            if (tagName == null || htmlUrl == null) continue;

            String releaseName = textOrNull(rel, "name");
            String title = buildTitle(repoShortName, tagName, releaseName);
            String body = textOrNull(rel, "body");
            String description = body != null && body.length() > MAX_DESCRIPTION_LENGTH
                ? body.substring(0, MAX_DESCRIPTION_LENGTH)
                : body;

            String author = rel.path("author").path("login").asText(null);
            Instant posted = publishedAt != null ? Instant.parse(publishedAt) : Instant.now();
            String externalId = repoFullName + ":" + tagName;

            out.add(new FetchedItem(externalId, htmlUrl, title, description, author, posted, rel.toString(), topics));
        }
        return out;
    }

    private static String buildTitle(String repoShortName, String tagName, String releaseName) {
        String base = repoShortName + " " + tagName;
        if (releaseName != null && !releaseName.isBlank()
                && !releaseName.equals(tagName) && !releaseName.equals("v" + tagName)
                && !tagName.equals("v" + releaseName)) {
            base += " — " + releaseName;
        }
        return base;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
