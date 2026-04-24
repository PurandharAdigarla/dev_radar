package com.devradar.web.rest;

import com.devradar.ingest.job.*;
import com.devradar.observability.MetricsAggregationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/ingest")
public class IngestionTriggerResource {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionTriggerResource.class);

    @Value("${devradar.internal.trigger-secret:}")
    private String triggerSecret;

    private final RssFeedIngestor rss;
    private final HackerNewsIngestor hn;
    private final GHSAIngestor ghsa;
    private final GitHubTrendingIngestor ghTrending;
    private final GitHubReleasesIngestor ghReleases;
    private final DependencyReleaseIngestor depReleases;
    private final DependencyScanJob depScan;
    private final MetricsAggregationJob metrics;

    public IngestionTriggerResource(
        RssFeedIngestor rss, HackerNewsIngestor hn, GHSAIngestor ghsa,
        GitHubTrendingIngestor ghTrending, GitHubReleasesIngestor ghReleases,
        DependencyReleaseIngestor depReleases, DependencyScanJob depScan,
        MetricsAggregationJob metrics
    ) {
        this.rss = rss;
        this.hn = hn;
        this.ghsa = ghsa;
        this.ghTrending = ghTrending;
        this.ghReleases = ghReleases;
        this.depReleases = depReleases;
        this.depScan = depScan;
        this.metrics = metrics;
    }

    @PostMapping("/rss")
    public ResponseEntity<Void> triggerRss(@RequestHeader("X-Trigger-Secret") String secret) {
        validateSecret(secret);
        LOG.info("trigger: rss");
        rss.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/hn")
    public ResponseEntity<Void> triggerHn(@RequestHeader("X-Trigger-Secret") String secret) {
        validateSecret(secret);
        LOG.info("trigger: hn");
        hn.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ghsa")
    public ResponseEntity<Void> triggerGhsa(@RequestHeader("X-Trigger-Secret") String secret) {
        validateSecret(secret);
        LOG.info("trigger: ghsa");
        ghsa.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/gh-trending")
    public ResponseEntity<Void> triggerGhTrending(@RequestHeader("X-Trigger-Secret") String secret) {
        validateSecret(secret);
        LOG.info("trigger: gh-trending");
        ghTrending.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/gh-releases")
    public ResponseEntity<Void> triggerGhReleases(@RequestHeader("X-Trigger-Secret") String secret) {
        validateSecret(secret);
        LOG.info("trigger: gh-releases");
        ghReleases.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dep-releases")
    public ResponseEntity<Void> triggerDepReleases(@RequestHeader("X-Trigger-Secret") String secret) {
        validateSecret(secret);
        LOG.info("trigger: dep-releases");
        depReleases.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dep-scan")
    public ResponseEntity<Void> triggerDepScan(@RequestHeader("X-Trigger-Secret") String secret) {
        validateSecret(secret);
        LOG.info("trigger: dep-scan");
        depScan.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/metrics-aggregate")
    public ResponseEntity<Void> triggerMetrics(@RequestHeader("X-Trigger-Secret") String secret) {
        validateSecret(secret);
        LOG.info("trigger: metrics-aggregate");
        metrics.aggregateYesterday();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/all")
    public ResponseEntity<Void> triggerAll(@RequestHeader("X-Trigger-Secret") String secret) {
        validateSecret(secret);
        LOG.info("trigger: all data ingestors");
        rss.run();
        hn.run();
        ghsa.run();
        ghTrending.run();
        ghReleases.run();
        depReleases.run();
        depScan.run();
        return ResponseEntity.ok().build();
    }

    private void validateSecret(String secret) {
        if (triggerSecret.isEmpty() || !triggerSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid trigger secret");
        }
    }
}
