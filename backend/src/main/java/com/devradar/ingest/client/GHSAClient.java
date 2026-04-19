package com.devradar.ingest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GHSAClient {

    private final RestClient http;

    public GHSAClient(RestClient.Builder builder,
                      @Value("${devradar.ghsa.base-url:https://api.github.com}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public List<FetchedItem> fetchRecent() {
        JsonNode arr = http.get()
            .uri(uri -> uri.path("/advisories").queryParam("per_page", "50").build())
            .retrieve()
            .body(JsonNode.class);

        List<FetchedItem> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode adv : arr) {
            String ghsaId = textOrNull(adv, "ghsa_id");
            String summary = textOrNull(adv, "summary");
            String url = textOrNull(adv, "html_url");
            String publishedAt = textOrNull(adv, "published_at");
            if (ghsaId == null || summary == null || url == null) continue;
            Instant posted = publishedAt != null ? Instant.parse(publishedAt) : Instant.now();
            out.add(new FetchedItem(ghsaId, url, summary, null, posted, adv.toString(), List.of("security")));
        }
        return out;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
