package com.devradar.ingest.job;

import com.devradar.domain.FeedSubscription;
import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.RssFeedClient;
import com.devradar.repository.FeedSubscriptionRepository;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RssFeedIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(RssFeedIngestor.class);
    private static final String CODE = "ARTICLE";

    private final RssFeedClient client;
    private final IngestionService ingestion;
    private final SourceRepository sources;
    private final FeedSubscriptionRepository feedSubRepo;

    public RssFeedIngestor(RssFeedClient client, IngestionService ingestion,
                           SourceRepository sources, FeedSubscriptionRepository feedSubRepo) {
        this.client = client;
        this.ingestion = ingestion;
        this.sources = sources;
        this.feedSubRepo = feedSubRepo;
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.article.fixed-delay-ms:7200000}",
               initialDelayString = "${devradar.ingest.article.initial-delay-ms:60000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("ARTICLE source not active; skipping");
            return;
        }

        List<FeedSubscription> subs = feedSubRepo.findByActiveTrue();
        LOG.info("article ingestion starting; {} active feeds", subs.size());

        List<FetchedItem> allItems = new ArrayList<>();
        for (FeedSubscription sub : subs) {
            try {
                List<FetchedItem> raw = client.fetch(sub.getFeedUrl());
                for (FetchedItem item : raw) {
                    allItems.add(new FetchedItem(
                        item.externalId(), item.url(), item.title(),
                        item.description(), item.author(), item.postedAt(),
                        item.rawPayload(), List.of(sub.getTagSlug())
                    ));
                }
            } catch (Exception e) {
                LOG.warn("feed fetch failed url={}: {}", sub.getFeedUrl(), e.toString());
            }
        }

        ingestion.ingestBatch(src, allItems);
    }
}
