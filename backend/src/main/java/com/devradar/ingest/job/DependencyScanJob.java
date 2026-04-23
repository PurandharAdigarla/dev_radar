package com.devradar.ingest.job;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.UserDependency;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.github.GitHubApiClient.DirEntry;
import com.devradar.github.GitHubApiClient.FileContent;
import com.devradar.github.GitHubApiClient.RepoInfo;
import com.devradar.ingest.deps.*;
import com.devradar.repository.UserDependencyRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DependencyScanJob {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyScanJob.class);

    private static final Map<String, DependencyFileParser> PARSERS = Map.of(
        "pom.xml", new PomParser(),
        "package.json", new PackageJsonParser(),
        "build.gradle", new GradleParser(),
        "build.gradle.kts", new GradleParser()
    );

    private static final Set<String> DEP_FILES = PARSERS.keySet();

    private final UserGithubIdentityRepository identityRepo;
    private final UserDependencyRepository depRepo;
    private final GitHubApiClient github;
    private final TokenEncryptor encryptor;
    private final int maxReposPerUser;

    public DependencyScanJob(UserGithubIdentityRepository identityRepo,
                             UserDependencyRepository depRepo,
                             GitHubApiClient github,
                             TokenEncryptor encryptor,
                             @Value("${devradar.ingest.dep-scan.max-repos-per-user:20}") int maxReposPerUser) {
        this.identityRepo = identityRepo;
        this.depRepo = depRepo;
        this.github = github;
        this.encryptor = encryptor;
        this.maxReposPerUser = maxReposPerUser;
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.dep-scan.fixed-delay-ms:86400000}",
               initialDelayString = "${devradar.ingest.dep-scan.initial-delay-ms:120000}")
    public void run() {
        List<UserGithubIdentity> users = identityRepo.findAll();
        LOG.info("dependency scan starting; {} users with GitHub identity", users.size());

        for (UserGithubIdentity identity : users) {
            try {
                scanUser(identity);
            } catch (Exception e) {
                LOG.warn("dependency scan failed userId={}: {}", identity.getUserId(), e.toString());
            }
        }
    }

    private void scanUser(UserGithubIdentity identity) {
        String token = encryptor.decrypt(identity.getAccessTokenEncrypted());
        List<RepoInfo> repos = github.listRepos(token);
        int limit = Math.min(repos.size(), maxReposPerUser);

        for (int i = 0; i < limit; i++) {
            RepoInfo repo = repos.get(i);
            try {
                scanRepo(identity.getUserId(), token, repo.fullName());
            } catch (Exception e) {
                LOG.debug("repo scan skipped repo={}: {}", repo.fullName(), e.getMessage());
            }
        }
    }

    private void scanRepo(Long userId, String token, String repoFullName) {
        List<DirEntry> rootEntries = github.listDirectoryEntries(token, repoFullName, "");

        for (DirEntry entry : rootEntries) {
            if ("file".equals(entry.type()) && DEP_FILES.contains(entry.name())) {
                parseAndUpsert(userId, token, repoFullName, entry.name());
            }
        }

        for (DirEntry entry : rootEntries) {
            if (!"dir".equals(entry.type())) continue;
            try {
                List<DirEntry> subEntries = github.listDirectoryEntries(token, repoFullName, entry.name());
                for (DirEntry sub : subEntries) {
                    if ("file".equals(sub.type()) && DEP_FILES.contains(sub.name())) {
                        parseAndUpsert(userId, token, repoFullName, entry.name() + "/" + sub.name());
                    }
                }
            } catch (Exception e) {
                LOG.debug("subdir scan skipped {}/{}: {}", repoFullName, entry.name(), e.getMessage());
            }
        }
    }

    private void parseAndUpsert(Long userId, String token, String repoFullName, String filePath) {
        String fileName = filePath.contains("/") ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath;
        DependencyFileParser parser = PARSERS.get(fileName);
        if (parser == null) return;

        FileContent content = github.getFileContent(token, repoFullName, filePath, null);
        List<ParsedDependency> deps = parser.parse(content.text());

        Instant now = Instant.now();
        for (ParsedDependency dep : deps) {
            UserDependency existing = depRepo.findByUserIdAndRepoFullNameAndFilePathAndPackageName(
                userId, repoFullName, filePath, dep.packageName()).orElse(null);

            if (existing != null) {
                existing.setCurrentVersion(dep.version());
                existing.setScannedAt(now);
                depRepo.save(existing);
            } else {
                UserDependency ud = new UserDependency();
                ud.setUserId(userId);
                ud.setRepoFullName(repoFullName);
                ud.setFilePath(filePath);
                ud.setEcosystem(dep.ecosystem());
                ud.setPackageName(dep.packageName());
                ud.setCurrentVersion(dep.version());
                ud.setScannedAt(now);
                depRepo.save(ud);
            }
        }
    }
}
