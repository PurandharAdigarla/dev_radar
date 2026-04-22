package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.GitHubReleasesClient;
import com.devradar.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitHubReleasesIngestorTest {

    GitHubReleasesClient client;
    IngestionService ingestion;
    SourceRepository sources;

    @BeforeEach
    void setup() {
        client = mock(GitHubReleasesClient.class);
        ingestion = mock(IngestionService.class);
        sources = mock(SourceRepository.class);
    }

    @Test
    void run_parsesConfigAndCallsClientPerRepo() {
        String config = "facebook/react:react,frontend;spring-projects/spring-boot:spring_boot,java";
        var ingestor = new GitHubReleasesIngestor(client, ingestion, sources, config);

        Source src = new Source();
        src.setCode("GH_RELEASES");
        src.setActive(true);
        when(sources.findByCode("GH_RELEASES")).thenReturn(Optional.of(src));

        var item = new FetchedItem("facebook/react:v19.0.0", "https://example.com", "react v19.0.0",
            "notes", "author", Instant.now(), null, List.of("react", "frontend"));
        when(client.fetchReleases(eq("facebook/react"), eq(List.of("react", "frontend")))).thenReturn(List.of(item));
        when(client.fetchReleases(eq("spring-projects/spring-boot"), eq(List.of("spring_boot", "java")))).thenReturn(List.of());

        ingestor.run();

        verify(client).fetchReleases("facebook/react", List.of("react", "frontend"));
        verify(client).fetchReleases("spring-projects/spring-boot", List.of("spring_boot", "java"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FetchedItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestion, times(2)).ingestBatch(eq(src), captor.capture());

        List<List<FetchedItem>> batches = captor.getAllValues();
        assertThat(batches.get(0)).hasSize(1);
        assertThat(batches.get(1)).isEmpty();
    }

    @Test
    void run_skipsWhenSourceNotActive() {
        var ingestor = new GitHubReleasesIngestor(client, ingestion, sources, "facebook/react:react");

        Source src = new Source();
        src.setActive(false);
        when(sources.findByCode("GH_RELEASES")).thenReturn(Optional.of(src));

        ingestor.run();

        verifyNoInteractions(client);
        verifyNoInteractions(ingestion);
    }

    @Test
    void run_continuesOnPerRepoFailure() {
        String config = "bad/repo:tag;good/repo:tag";
        var ingestor = new GitHubReleasesIngestor(client, ingestion, sources, config);

        Source src = new Source();
        src.setCode("GH_RELEASES");
        src.setActive(true);
        when(sources.findByCode("GH_RELEASES")).thenReturn(Optional.of(src));

        when(client.fetchReleases(eq("bad/repo"), any())).thenThrow(new RuntimeException("API error"));
        when(client.fetchReleases(eq("good/repo"), any())).thenReturn(List.of());

        ingestor.run();

        verify(client).fetchReleases(eq("good/repo"), any());
        verify(ingestion).ingestBatch(eq(src), any());
    }
}
