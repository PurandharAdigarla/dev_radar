package com.devradar.ingest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.Source;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceItemTagRepository;
import com.devradar.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionServiceIT extends AbstractIntegrationTest {

    @Autowired IngestionService ingestion;
    @Autowired SourceRepository sources;
    @Autowired SourceItemRepository items;
    @Autowired SourceItemTagRepository tags;

    @Test
    void ingestBatch_persistsItemsAndExtractsTags() {
        Source hn = sources.findByCode("HN").orElseThrow();

        List<FetchedItem> batch = List.of(
            new FetchedItem("hn-1001", "https://example.com/1", "Spring Boot 3.5 release notes", null, "alice", Instant.now(), "{}", List.of()),
            new FetchedItem("hn-1002", "https://example.com/2", "React 19 hooks deep dive", null, "bob", Instant.now(), "{}", List.of())
        );

        int inserted = ingestion.ingestBatch(hn, batch);

        assertThat(inserted).isEqualTo(2);
        assertThat(items.findBySourceIdAndExternalId(hn.getId(), "hn-1001")).isPresent();
        assertThat(tags.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void ingestBatch_isIdempotent_onRepeatCall() {
        Source hn = sources.findByCode("HN").orElseThrow();

        FetchedItem dup = new FetchedItem("hn-2000", "https://example.com/x", "Python news", null, "x", Instant.now(), "{}", List.of());

        int firstRun = ingestion.ingestBatch(hn, List.of(dup));
        int secondRun = ingestion.ingestBatch(hn, List.of(dup));

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isEqualTo(0);
    }

    @Test
    void ingestBatch_continuesPastFailingItems() {
        Source hn = sources.findByCode("HN").orElseThrow();

        String tooLong = "x".repeat(2000);
        List<FetchedItem> batch = List.of(
            new FetchedItem("hn-3001", "https://example.com/a", tooLong, null, "x", Instant.now(), "{}", List.of()),
            new FetchedItem("hn-3002", "https://example.com/b", "MySQL 8 vs 9 benchmarks", null, "y", Instant.now(), "{}", List.of())
        );

        int inserted = ingestion.ingestBatch(hn, batch);

        assertThat(inserted).isEqualTo(1);
        assertThat(items.findBySourceIdAndExternalId(hn.getId(), "hn-3002")).isPresent();
    }
}
