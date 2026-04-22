package com.devradar.ingest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

            String description = buildDescription(adv);
            out.add(new FetchedItem(ghsaId, url, summary, description, null, posted, adv.toString(), List.of("security")));
        }
        return out;
    }

    private static String buildDescription(JsonNode adv) {
        StringBuilder sb = new StringBuilder();
        String severity = textOrNull(adv, "severity");
        if (severity != null) sb.append(severity.toUpperCase(Locale.ROOT)).append(" severity");

        JsonNode vulns = adv.path("vulnerabilities");
        if (vulns.isArray() && vulns.size() > 0) {
            JsonNode first = vulns.get(0);
            String pkgName = first.path("package").path("name").asText(null);
            String ecosystem = first.path("package").path("ecosystem").asText(null);
            String vulnRange = textOrNull(first, "vulnerable_version_range");
            String patched = textOrNull(first, "patched_versions");

            if (pkgName != null) {
                sb.append(". Affects ").append(pkgName);
                if (ecosystem != null) sb.append(" (").append(ecosystem).append(")");
                if (vulnRange != null) sb.append(" ").append(vulnRange);
            }
            if (patched != null) sb.append(". Fix: upgrade to ").append(patched);
        }

        String cveId = textOrNull(adv, "cve_id");
        if (cveId != null) sb.append(". ").append(cveId);

        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
