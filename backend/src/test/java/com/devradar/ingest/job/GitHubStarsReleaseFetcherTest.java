package com.devradar.ingest.job;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.Source;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.ingest.client.GitHubReleasesClient;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitHubStarsReleaseFetcherTest {

    GitHubApiClient github;
    GitHubReleasesClient releasesClient;
    IngestionService ingestion;
    SourceRepository sources;
    UserGithubIdentityRepository identityRepo;
    TokenEncryptor encryptor;
    GitHubStarsReleaseFetcher fetcher;

    @BeforeEach
    void setup() {
        github = mock(GitHubApiClient.class);
        releasesClient = mock(GitHubReleasesClient.class);
        ingestion = mock(IngestionService.class);
        sources = mock(SourceRepository.class);
        identityRepo = mock(UserGithubIdentityRepository.class);
        encryptor = mock(TokenEncryptor.class);
        fetcher = new GitHubStarsReleaseFetcher(github, releasesClient, ingestion, sources, identityRepo, encryptor);
    }

    @Test
    void tryFetchForUser_fetchesReleasesForStarredRepos() {
        UserGithubIdentity identity = new UserGithubIdentity();
        identity.setUserId(7L);
        identity.setAccessTokenEncrypted("encrypted-token");
        when(identityRepo.findById(7L)).thenReturn(Optional.of(identity));
        when(encryptor.decrypt("encrypted-token")).thenReturn("ghp_real_token");

        Source src = new Source();
        src.setCode("GH_STARS");
        src.setActive(true);
        when(sources.findByCode("GH_STARS")).thenReturn(Optional.of(src));

        when(github.listStarred("ghp_real_token")).thenReturn(List.of("alice/lib-a", "bob/lib-b"));

        var item = new FetchedItem("alice/lib-a:v1.0.0", "https://example.com", "lib-a v1.0.0",
            "notes", "alice", Instant.now(), null, List.of());
        when(releasesClient.fetchReleases("alice/lib-a", List.of())).thenReturn(List.of(item));
        when(releasesClient.fetchReleases("bob/lib-b", List.of())).thenReturn(List.of());

        fetcher.tryFetchForUser(7L);

        verify(releasesClient).fetchReleases("alice/lib-a", List.of());
        verify(releasesClient).fetchReleases("bob/lib-b", List.of());
        verify(ingestion, times(2)).ingestBatch(eq(src), any());
    }

    @Test
    void tryFetchForUser_skipsWhenNoGithubIdentity() {
        when(identityRepo.findById(99L)).thenReturn(Optional.empty());

        fetcher.tryFetchForUser(99L);

        verifyNoInteractions(github);
        verifyNoInteractions(releasesClient);
        verifyNoInteractions(ingestion);
    }

    @Test
    void tryFetchForUser_skipsWhenSourceInactive() {
        UserGithubIdentity identity = new UserGithubIdentity();
        identity.setUserId(7L);
        identity.setAccessTokenEncrypted("encrypted-token");
        when(identityRepo.findById(7L)).thenReturn(Optional.of(identity));
        when(encryptor.decrypt("encrypted-token")).thenReturn("ghp_real_token");

        Source src = new Source();
        src.setActive(false);
        when(sources.findByCode("GH_STARS")).thenReturn(Optional.of(src));

        fetcher.tryFetchForUser(7L);

        verifyNoInteractions(github);
        verifyNoInteractions(releasesClient);
    }

    @Test
    void tryFetchForUser_continuesOnPerRepoFailure() {
        UserGithubIdentity identity = new UserGithubIdentity();
        identity.setUserId(7L);
        identity.setAccessTokenEncrypted("enc");
        when(identityRepo.findById(7L)).thenReturn(Optional.of(identity));
        when(encryptor.decrypt("enc")).thenReturn("token");

        Source src = new Source();
        src.setCode("GH_STARS");
        src.setActive(true);
        when(sources.findByCode("GH_STARS")).thenReturn(Optional.of(src));

        when(github.listStarred("token")).thenReturn(List.of("bad/repo", "good/repo"));
        when(releasesClient.fetchReleases("bad/repo", List.of())).thenThrow(new RuntimeException("API error"));
        when(releasesClient.fetchReleases("good/repo", List.of())).thenReturn(List.of());

        fetcher.tryFetchForUser(7L);

        verify(releasesClient).fetchReleases("good/repo", List.of());
        verify(ingestion).ingestBatch(eq(src), any());
    }
}
