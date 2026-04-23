package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.DependencyReleaseClient;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.UserDependencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class DependencyReleaseIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyReleaseIngestor.class);
    private static final String CODE = "DEP_RELEASE";

    private final SourceRepository sources;
    private final UserDependencyRepository depRepo;
    private final DependencyReleaseClient client;
    private final IngestionService ingestion;

    public DependencyReleaseIngestor(SourceRepository sources, UserDependencyRepository depRepo,
                                     DependencyReleaseClient client, IngestionService ingestion) {
        this.sources = sources;
        this.depRepo = depRepo;
        this.client = client;
        this.ingestion = ingestion;
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.dep-release.fixed-delay-ms:86400000}",
               initialDelayString = "${devradar.ingest.dep-release.initial-delay-ms:7200000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("DEP_RELEASE source not active; skipping");
            return;
        }

        List<Object[]> packages = depRepo.findDistinctPackages();
        LOG.info("dependency release check starting; {} distinct packages", packages.size());

        List<FetchedItem> allReleases = new ArrayList<>();
        for (Object[] row : packages) {
            String ecosystem = (String) row[0];
            String packageName = (String) row[1];
            String currentVersion = (String) row[2];
            try {
                Optional<FetchedItem> item = client.checkForNewerVersion(ecosystem, packageName, currentVersion);
                item.ifPresent(allReleases::add);
            } catch (Exception e) {
                LOG.debug("dep release check failed pkg={}: {}", packageName, e.getMessage());
            }
        }

        ingestion.ingestBatch(src, allReleases);
    }
}
