package com.devradar.ingest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class HackerNewsClient {

    private final RestClient http;

    public HackerNewsClient(RestClient.Builder builder,
                            @Value("${devradar.hn.base-url:https://hn.algolia.com}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public List<FetchedItem> fetchRecent(int minPoints) {
        JsonNode body = http.get()
            .uri(uri -> uri.path("/api/v1/search_by_date")
                .queryParam("tags", "story")
                .queryParam("numericFilters", "points>" + minPoints)
                .build())
            .retrieve()
            .body(JsonNode.class);

        List<FetchedItem> out = new ArrayList<>();
        if (body == null || !body.has("hits")) return out;
        for (JsonNode hit : body.get("hits")) {
            String externalId = textOrNull(hit, "objectID");
            String title = textOrNull(hit, "title");
            String url = textOrNull(hit, "url");
            if (externalId == null || title == null || url == null) continue;
            String author = textOrNull(hit, "author");
            long createdAtSec = hit.path("created_at_i").asLong();
            Instant posted = createdAtSec > 0 ? Instant.ofEpochSecond(createdAtSec) : Instant.now();

            String description = buildDescription(hit);
            out.add(new FetchedItem(externalId, url, title, description, author, posted, hit.toString(), List.of()));
        }
        return out;
    }

    private static String buildDescription(JsonNode hit) {
        String storyText = textOrNull(hit, "story_text");
        if (storyText != null && !storyText.isBlank()) {
            return storyText.length() > 500 ? storyText.substring(0, 500) : storyText;
        }
        int points = hit.path("points").asInt(0);
        int comments = hit.path("num_comments").asInt(0);
        if (points > 0) {
            return points + " points, " + comments + " comments on Hacker News";
        }
        return null;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
