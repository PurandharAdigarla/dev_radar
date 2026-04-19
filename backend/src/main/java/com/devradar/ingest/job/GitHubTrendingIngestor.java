package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.GitHubTrendingClient;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class GitHubTrendingIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubTrendingIngestor.class);
    private static final String CODE = "GH_TRENDING";

    private final GitHubTrendingClient client;
    private final IngestionService ingestion;
    private final SourceRepository sources;
    private final List<String> trackedLanguages;

    public GitHubTrendingIngestor(
        GitHubTrendingClient client,
        IngestionService ingestion,
        SourceRepository sources,
        @Value("${devradar.ingest.gh-trending.languages:java,python,javascript,typescript,go,rust}") String csv
    ) {
        this.client = client; this.ingestion = ingestion; this.sources = sources;
        this.trackedLanguages = Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.gh-trending.fixed-delay-ms:21600000}", initialDelayString = "${devradar.ingest.gh-trending.initial-delay-ms:60000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("GH_TRENDING source not active; skipping");
            return;
        }
        List<FetchedItem> all = new ArrayList<>();
        for (String lang : trackedLanguages) {
            try {
                all.addAll(client.fetchTrending(lang));
            } catch (Exception e) {
                LOG.warn("GH trending fetch failed lang={}: {}", lang, e.toString());
            }
        }
        ingestion.ingestBatch(src, all);
    }
}
