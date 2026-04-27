package com.devradar.ingest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Client for the FIRST.org EPSS (Exploit Prediction Scoring System) API.
 * Fetches exploit probability scores for CVE identifiers.
 */
@Component
public class EpssClient {

    private static final Logger LOG = LoggerFactory.getLogger(EpssClient.class);

    private final RestClient http;

    public EpssClient(RestClient.Builder builder,
                      @Value("${devradar.epss.base-url:https://api.first.org}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    /**
     * Batch-query EPSS scores for a collection of CVE IDs.
     * Returns a map of CVE ID -> EpssScore. CVEs without data are omitted.
     */
    public Map<String, EpssScore> fetchScores(Collection<String> cveIds) {
        Map<String, EpssScore> result = new HashMap<>();
        if (cveIds == null || cveIds.isEmpty()) return result;

        String joined = String.join(",", cveIds);
        try {
            JsonNode body = http.get()
                .uri(uri -> uri.path("/data/v1/epss").queryParam("cve", joined).build())
                .retrieve()
                .body(JsonNode.class);

            if (body == null) return result;
            JsonNode data = body.path("data");
            if (!data.isArray()) return result;

            for (JsonNode entry : data) {
                String cve = entry.path("cve").asText(null);
                if (cve == null) continue;
                double epss = entry.path("epss").asDouble(0.0);
                double percentile = entry.path("percentile").asDouble(0.0);
                result.put(cve, new EpssScore(epss, percentile));
            }
        } catch (Exception e) {
            LOG.warn("EPSS API call failed for {} CVEs: {}", cveIds.size(), e.toString());
        }
        return result;
    }

    public record EpssScore(double epss, double percentile) {

        /** Returns EPSS probability as a percentage string, e.g. "92.3%". */
        public String epssPercent() {
            return String.format("%.1f%%", epss * 100);
        }

        /** Human-readable risk label based on EPSS score. */
        public String riskLabel() {
            if (epss >= 0.1) return "likely exploited";
            if (epss >= 0.01) return "moderate risk";
            return "low risk";
        }
    }
}
