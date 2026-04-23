package com.devradar.ingest.job;

import com.devradar.domain.FeedSubscription;
import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.RssFeedClient;
import com.devradar.repository.FeedSubscriptionRepository;
import com.devradar.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RssFeedIngestorTest {

    @Mock SourceRepository sources;
    @Mock FeedSubscriptionRepository feedSubRepo;
    @Mock RssFeedClient client;
    @Mock IngestionService ingestion;
    @InjectMocks RssFeedIngestor ingestor;

    @Test
    void run_skipsWhenSourceNotActive() {
        when(sources.findByCode("ARTICLE")).thenReturn(Optional.empty());

        ingestor.run();

        verifyNoInteractions(feedSubRepo, client, ingestion);
    }

    @Test
    void run_ingestsItemsWithTagSlugInjected() {
        Source src = mock(Source.class);
        when(src.isActive()).thenReturn(true);
        when(sources.findByCode("ARTICLE")).thenReturn(Optional.of(src));

        FeedSubscription sub = mock(FeedSubscription.class);
        when(sub.getFeedUrl()).thenReturn("https://spring.io/blog.atom");
        when(sub.getTagSlug()).thenReturn("spring_boot");
        when(feedSubRepo.findByActiveTrue()).thenReturn(List.of(sub));

        FetchedItem rawItem = new FetchedItem(
            "guid-1", "https://spring.io/blog/post1", "Spring Boot 3.5",
            "Summary", "author", Instant.now(), null, List.of()
        );
        when(client.fetch("https://spring.io/blog.atom")).thenReturn(List.of(rawItem));

        ingestor.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FetchedItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestion).ingestBatch(eq(src), captor.capture());
        List<FetchedItem> ingested = captor.getValue();
        assertThat(ingested).hasSize(1);
        assertThat(ingested.get(0).topics()).containsExactly("spring_boot");
        assertThat(ingested.get(0).title()).isEqualTo("Spring Boot 3.5");
    }

    @Test
    void run_continuesOnFeedFailure() {
        Source src = mock(Source.class);
        when(src.isActive()).thenReturn(true);
        when(sources.findByCode("ARTICLE")).thenReturn(Optional.of(src));

        FeedSubscription goodSub = mock(FeedSubscription.class);
        when(goodSub.getFeedUrl()).thenReturn("https://good.com/feed");
        when(goodSub.getTagSlug()).thenReturn("java");

        FeedSubscription badSub = mock(FeedSubscription.class);
        when(badSub.getFeedUrl()).thenReturn("https://bad.com/feed");

        when(feedSubRepo.findByActiveTrue()).thenReturn(List.of(badSub, goodSub));
        when(client.fetch("https://bad.com/feed")).thenThrow(new RuntimeException("network error"));
        when(client.fetch("https://good.com/feed")).thenReturn(List.of(
            new FetchedItem("g1", "https://good.com/1", "Good Post", null, null, Instant.now(), null, List.of())
        ));

        ingestor.run();

        verify(ingestion).ingestBatch(eq(src), any());
    }
}
