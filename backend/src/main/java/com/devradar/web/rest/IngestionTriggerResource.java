package com.devradar.web.rest;

import com.devradar.ingest.job.*;
import com.devradar.notification.CveAlertService;
import com.devradar.notification.DigestService;
import com.devradar.observability.MetricsAggregationJob;
import com.devradar.domain.NotificationPreference;
import com.devradar.repository.NotificationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/internal/ingest")
public class IngestionTriggerResource {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionTriggerResource.class);

    private final RssFeedIngestor rss;
    private final HackerNewsIngestor hn;
    private final GHSAIngestor ghsa;
    private final GitHubTrendingIngestor ghTrending;
    private final GitHubReleasesIngestor ghReleases;
    private final DependencyReleaseIngestor depReleases;
    private final DependencyScanJob depScan;
    private final MetricsAggregationJob metrics;
    private final DigestService digestService;
    private final CveAlertService cveAlertService;
    private final NotificationPreferenceRepository prefRepo;
    private final DemoSeedJob demoSeed;

    public IngestionTriggerResource(
        RssFeedIngestor rss, HackerNewsIngestor hn, GHSAIngestor ghsa,
        GitHubTrendingIngestor ghTrending, GitHubReleasesIngestor ghReleases,
        DependencyReleaseIngestor depReleases, DependencyScanJob depScan,
        MetricsAggregationJob metrics, DigestService digestService,
        CveAlertService cveAlertService,
        NotificationPreferenceRepository prefRepo,
        DemoSeedJob demoSeed
    ) {
        this.rss = rss;
        this.hn = hn;
        this.ghsa = ghsa;
        this.ghTrending = ghTrending;
        this.ghReleases = ghReleases;
        this.depReleases = depReleases;
        this.depScan = depScan;
        this.metrics = metrics;
        this.digestService = digestService;
        this.cveAlertService = cveAlertService;
        this.prefRepo = prefRepo;
        this.demoSeed = demoSeed;
    }

    @PostMapping("/rss")
    public ResponseEntity<Void> triggerRss() {
        LOG.info("trigger: rss");
        rss.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/hn")
    public ResponseEntity<Void> triggerHn() {
        LOG.info("trigger: hn");
        hn.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ghsa")
    public ResponseEntity<Void> triggerGhsa() {
        LOG.info("trigger: ghsa");
        ghsa.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/gh-trending")
    public ResponseEntity<Void> triggerGhTrending() {
        LOG.info("trigger: gh-trending");
        ghTrending.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/gh-releases")
    public ResponseEntity<Void> triggerGhReleases() {
        LOG.info("trigger: gh-releases");
        ghReleases.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dep-releases")
    public ResponseEntity<Void> triggerDepReleases() {
        LOG.info("trigger: dep-releases");
        depReleases.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dep-scan")
    public ResponseEntity<Void> triggerDepScan() {
        LOG.info("trigger: dep-scan");
        depScan.run();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/seed-demo")
    public ResponseEntity<String> triggerDemoSeed() {
        LOG.info("trigger: seed-demo");
        int count = demoSeed.run();
        return ResponseEntity.ok(count + " demo items seeded");
    }

    @PostMapping("/metrics-aggregate")
    public ResponseEntity<Void> triggerMetrics() {
        LOG.info("trigger: metrics-aggregate");
        metrics.aggregateYesterday();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/metrics-aggregate-today")
    public ResponseEntity<Void> triggerMetricsToday() {
        LOG.info("trigger: metrics-aggregate-today");
        metrics.aggregateForDate(java.time.LocalDate.now());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/all")
    public ResponseEntity<Void> triggerAll() {
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

    /** HTTP trigger for weekly CVE alert — sends lightweight CVE count emails to all users. */
    @PostMapping("/cve-alerts")
    public ResponseEntity<Void> triggerCveAlerts() {
        LOG.info("trigger: cve-alerts");
        cveAlertService.sendCveAlertsForAllUsers();
        return ResponseEntity.ok().build();
    }

    /** HTTP trigger for weekly digest — use Cloud Scheduler to call this hourly on Cloud Run. */
    @PostMapping("/weekly-digest")
    public ResponseEntity<Void> triggerWeeklyDigest() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        int dayOfWeek = now.getDayOfWeek().getValue();
        int hour = now.getHour();
        LOG.info("trigger: weekly-digest day={} hour={}", dayOfWeek, hour);

        List<NotificationPreference> prefs =
                prefRepo.findByEmailEnabledTrueAndDigestDayOfWeekAndDigestHourUtc(dayOfWeek, hour);
        for (var pref : prefs) {
            try {
                digestService.sendDigestForUser(pref.getUserId());
            } catch (Exception e) {
                LOG.error("Failed to send digest for user={}: {}", pref.getUserId(), e.getMessage(), e);
            }
        }
        return ResponseEntity.ok().build();
    }
}
