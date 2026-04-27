package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.EpssClient;
import com.devradar.ingest.client.EpssClient.EpssScore;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.GHSAClient;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GHSAIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(GHSAIngestor.class);
    private static final String CODE = "GHSA";
    private static final Pattern CVE_PATTERN = Pattern.compile("CVE-\\d{4}-\\d+");

    private final GHSAClient client;
    private final EpssClient epssClient;
    private final IngestionService ingestion;
    private final SourceRepository sources;

    public GHSAIngestor(GHSAClient client, EpssClient epssClient,
                        IngestionService ingestion, SourceRepository sources) {
        this.client = client;
        this.epssClient = epssClient;
        this.ingestion = ingestion;
        this.sources = sources;
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
            var enriched = enrichWithEpss(items);
            ingestion.ingestBatch(src, enriched);
        } catch (Exception e) {
            LOG.warn("GHSA ingestion failed: {}", e.toString());
        }
    }

    /**
     * Extract CVE IDs from all items, batch-query EPSS, and append EPSS data to descriptions.
     */
    List<FetchedItem> enrichWithEpss(List<FetchedItem> items) {
        // Collect all CVE IDs and map them back to item indices
        Map<Integer, Set<String>> itemCves = new LinkedHashMap<>();
        Set<String> allCves = new LinkedHashSet<>();

        for (int i = 0; i < items.size(); i++) {
            FetchedItem item = items.get(i);
            Set<String> cves = extractCves(item.title(), item.description());
            if (!cves.isEmpty()) {
                itemCves.put(i, cves);
                allCves.addAll(cves);
            }
        }

        if (allCves.isEmpty()) return items;

        Map<String, EpssScore> scores = epssClient.fetchScores(allCves);
        if (scores.isEmpty()) return items;

        LOG.info("EPSS enrichment: {} CVEs queried, {} scores returned", allCves.size(), scores.size());

        List<FetchedItem> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            FetchedItem item = items.get(i);
            Set<String> cves = itemCves.get(i);
            if (cves == null) {
                result.add(item);
                continue;
            }

            // Find the highest-scoring CVE for this item
            EpssScore best = null;
            String bestCve = null;
            for (String cve : cves) {
                EpssScore score = scores.get(cve);
                if (score != null && (best == null || score.epss() > best.epss())) {
                    best = score;
                    bestCve = cve;
                }
            }

            if (best == null) {
                result.add(item);
                continue;
            }

            String epssTag = String.format("[EPSS: %s (%s)]", best.epssPercent(), best.riskLabel());
            String enrichedDesc = item.description() != null
                ? item.description() + ". " + epssTag
                : bestCve + " " + epssTag;

            result.add(new FetchedItem(
                item.externalId(), item.url(), item.title(),
                enrichedDesc, item.author(), item.postedAt(),
                item.rawPayload(), item.topics()
            ));
        }
        return result;
    }

    static Set<String> extractCves(String title, String description) {
        Set<String> cves = new LinkedHashSet<>();
        if (title != null) {
            Matcher m = CVE_PATTERN.matcher(title);
            while (m.find()) cves.add(m.group());
        }
        if (description != null) {
            Matcher m = CVE_PATTERN.matcher(description);
            while (m.find()) cves.add(m.group());
        }
        return cves;
    }
}
