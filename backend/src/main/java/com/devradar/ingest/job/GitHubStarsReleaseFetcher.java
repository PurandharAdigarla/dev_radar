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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GitHubStarsReleaseFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubStarsReleaseFetcher.class);
    static final String CODE = "GH_STARS";

    private final GitHubApiClient github;
    private final GitHubReleasesClient releasesClient;
    private final IngestionService ingestion;
    private final SourceRepository sourceRepo;
    private final UserGithubIdentityRepository identityRepo;
    private final TokenEncryptor encryptor;

    public GitHubStarsReleaseFetcher(
        GitHubApiClient github,
        GitHubReleasesClient releasesClient,
        IngestionService ingestion,
        SourceRepository sourceRepo,
        UserGithubIdentityRepository identityRepo,
        TokenEncryptor encryptor
    ) {
        this.github = github;
        this.releasesClient = releasesClient;
        this.ingestion = ingestion;
        this.sourceRepo = sourceRepo;
        this.identityRepo = identityRepo;
        this.encryptor = encryptor;
    }

    public void tryFetchForUser(Long userId) {
        UserGithubIdentity identity = identityRepo.findById(userId).orElse(null);
        if (identity == null) return;

        String token = encryptor.decrypt(identity.getAccessTokenEncrypted());

        Source src = sourceRepo.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) return;

        List<String> starredRepos = github.listStarred(token);
        LOG.info("GH_STARS userId={} starred={}", userId, starredRepos.size());

        for (String repo : starredRepos) {
            try {
                List<FetchedItem> releases = releasesClient.fetchReleases(repo, List.of());
                ingestion.ingestBatch(src, releases);
            } catch (Exception e) {
                LOG.debug("Starred repo release fetch skipped repo={}: {}", repo, e.getMessage());
            }
        }
    }
}
