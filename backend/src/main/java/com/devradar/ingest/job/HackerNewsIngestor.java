package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.HackerNewsClient;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HackerNewsIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(HackerNewsIngestor.class);
    private static final String CODE = "HN";
    private static final int MIN_POINTS = 50;

    private final HackerNewsClient client;
    private final IngestionService ingestion;
    private final SourceRepository sources;

    public HackerNewsIngestor(HackerNewsClient client, IngestionService ingestion, SourceRepository sources) {
        this.client = client; this.ingestion = ingestion; this.sources = sources;
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.hn.fixed-delay-ms:3600000}", initialDelayString = "${devradar.ingest.hn.initial-delay-ms:30000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("HN source not active; skipping");
            return;
        }
        try {
            var items = client.fetchRecent(MIN_POINTS);
            ingestion.ingestBatch(src, items);
        } catch (Exception e) {
            LOG.warn("HN ingestion failed: {}", e.toString());
        }
    }
}
