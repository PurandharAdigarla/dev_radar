package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.GHSAClient;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GHSAIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(GHSAIngestor.class);
    private static final String CODE = "GHSA";

    private final GHSAClient client;
    private final IngestionService ingestion;
    private final SourceRepository sources;

    public GHSAIngestor(GHSAClient client, IngestionService ingestion, SourceRepository sources) {
        this.client = client; this.ingestion = ingestion; this.sources = sources;
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.ghsa.fixed-delay-ms:1800000}", initialDelayString = "${devradar.ingest.ghsa.initial-delay-ms:90000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("GHSA source not active; skipping");
            return;
        }
        try {
            var items = client.fetchRecent();
            ingestion.ingestBatch(src, items);
        } catch (Exception e) {
            LOG.warn("GHSA ingestion failed: {}", e.toString());
        }
    }
}
