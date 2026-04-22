package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.GitHubReleasesClient;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GitHubReleasesIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubReleasesIngestor.class);
    static final String CODE = "GH_RELEASES";

    private final GitHubReleasesClient client;
    private final IngestionService ingestion;
    private final SourceRepository sources;
    private final Map<String, List<String>> repoTags;

    public GitHubReleasesIngestor(
        GitHubReleasesClient client,
        IngestionService ingestion,
        SourceRepository sources,
        @Value("${devradar.ingest.gh-releases.repos:}") String reposCsv
    ) {
        this.client = client;
        this.ingestion = ingestion;
        this.sources = sources;
        this.repoTags = parseConfig(reposCsv);
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.gh-releases.fixed-delay-ms:7200000}",
               initialDelayString = "${devradar.ingest.gh-releases.initial-delay-ms:120000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("GH_RELEASES source not active; skipping");
            return;
        }
        for (var entry : repoTags.entrySet()) {
            try {
                var items = client.fetchReleases(entry.getKey(), entry.getValue());
                ingestion.ingestBatch(src, items);
            } catch (Exception e) {
                LOG.warn("GH releases fetch failed repo={}: {}", entry.getKey(), e.toString());
            }
        }
    }

    static Map<String, List<String>> parseConfig(String csv) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (csv == null || csv.isBlank()) return map;
        for (String entry : csv.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":", 2);
            String repo = parts[0].trim();
            List<String> tags = parts.length > 1
                ? Arrays.stream(parts[1].split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();
            if (!repo.isEmpty()) map.put(repo, tags);
        }
        return map;
    }
}
