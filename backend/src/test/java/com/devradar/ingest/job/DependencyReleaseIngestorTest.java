package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.DependencyReleaseClient;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.UserDependencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DependencyReleaseIngestorTest {

    @Mock SourceRepository sources;
    @Mock UserDependencyRepository depRepo;
    @Mock DependencyReleaseClient client;
    @Mock IngestionService ingestion;
    @InjectMocks DependencyReleaseIngestor ingestor;

    @Test
    void run_skipsWhenSourceNotActive() {
        when(sources.findByCode("DEP_RELEASE")).thenReturn(Optional.empty());

        ingestor.run();

        verifyNoInteractions(depRepo, client, ingestion);
    }

    @Test
    void run_checksRegistryAndIngestsNewReleases() {
        Source src = mock(Source.class);
        when(src.isActive()).thenReturn(true);
        when(sources.findByCode("DEP_RELEASE")).thenReturn(Optional.of(src));
        List<Object[]> packages = new ArrayList<>();
        packages.add(new Object[]{"MAVEN", "com.fasterxml.jackson.core:jackson-databind", "2.16.1"});
        when(depRepo.findDistinctPackages()).thenReturn(packages);

        FetchedItem releaseItem = new FetchedItem(
            "MAVEN:com.fasterxml.jackson.core:jackson-databind:2.17.0",
            "https://central.sonatype.com/artifact/com.fasterxml.jackson.core/jackson-databind/2.17.0",
            "jackson-databind 2.17.0 released", null, null, Instant.now(), null, List.of()
        );
        when(client.checkForNewerVersion("MAVEN", "com.fasterxml.jackson.core:jackson-databind", "2.16.1"))
            .thenReturn(Optional.of(releaseItem));

        ingestor.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FetchedItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestion).ingestBatch(eq(src), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).title()).contains("jackson-databind");
    }

    @Test
    void run_skipsWhenNoNewerVersion() {
        Source src = mock(Source.class);
        when(src.isActive()).thenReturn(true);
        when(sources.findByCode("DEP_RELEASE")).thenReturn(Optional.of(src));
        List<Object[]> packages = new ArrayList<>();
        packages.add(new Object[]{"NPM", "react", "18.2.0"});
        when(depRepo.findDistinctPackages()).thenReturn(packages);
        when(client.checkForNewerVersion("NPM", "react", "18.2.0")).thenReturn(Optional.empty());

        ingestor.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FetchedItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestion).ingestBatch(eq(src), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }
}
