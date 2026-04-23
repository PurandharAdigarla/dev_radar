package com.devradar.ingest.job;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.UserDependency;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.github.GitHubApiClient.DirEntry;
import com.devradar.github.GitHubApiClient.FileContent;
import com.devradar.github.GitHubApiClient.RepoInfo;
import com.devradar.repository.UserDependencyRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DependencyScanJobTest {

    @Mock UserGithubIdentityRepository identityRepo;
    @Mock UserDependencyRepository depRepo;
    @Mock GitHubApiClient github;
    @Mock TokenEncryptor encryptor;
    DependencyScanJob job;

    @BeforeEach
    void setUp() {
        job = new DependencyScanJob(identityRepo, depRepo, github, encryptor, 20);
    }

    @Test
    void run_scansPomXmlAtRoot() {
        UserGithubIdentity identity = new UserGithubIdentity();
        identity.setUserId(1L);
        identity.setAccessTokenEncrypted("enc");
        when(identityRepo.findAll()).thenReturn(List.of(identity));
        when(encryptor.decrypt("enc")).thenReturn("token");
        when(github.listRepos("token")).thenReturn(List.of(new RepoInfo("alice/api", "main")));
        when(github.listDirectoryEntries("token", "alice/api", ""))
            .thenReturn(List.of(new DirEntry("pom.xml", "file"), new DirEntry("src", "dir")));
        when(github.getFileContent("token", "alice/api", "pom.xml", null))
            .thenReturn(new FileContent("""
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.16.1</version>
                        </dependency>
                    </dependencies>
                </project>
                """, "sha1", "base64"));
        when(depRepo.findByUserIdAndRepoFullNameAndFilePathAndPackageName(
            any(), any(), any(), any())).thenReturn(Optional.empty());

        job.run();

        ArgumentCaptor<UserDependency> cap = ArgumentCaptor.forClass(UserDependency.class);
        verify(depRepo).save(cap.capture());
        UserDependency saved = cap.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getRepoFullName()).isEqualTo("alice/api");
        assertThat(saved.getFilePath()).isEqualTo("pom.xml");
        assertThat(saved.getEcosystem()).isEqualTo("MAVEN");
        assertThat(saved.getPackageName()).isEqualTo("com.fasterxml.jackson.core:jackson-databind");
        assertThat(saved.getCurrentVersion()).isEqualTo("2.16.1");
    }

    @Test
    void run_scansSubdirectories() {
        UserGithubIdentity identity = new UserGithubIdentity();
        identity.setUserId(2L);
        identity.setAccessTokenEncrypted("enc");
        when(identityRepo.findAll()).thenReturn(List.of(identity));
        when(encryptor.decrypt("enc")).thenReturn("token");
        when(github.listRepos("token")).thenReturn(List.of(new RepoInfo("bob/mono", "main")));
        when(github.listDirectoryEntries("token", "bob/mono", ""))
            .thenReturn(List.of(new DirEntry("frontend", "dir"), new DirEntry("README.md", "file")));
        when(github.listDirectoryEntries("token", "bob/mono", "frontend"))
            .thenReturn(List.of(new DirEntry("package.json", "file")));
        when(github.getFileContent("token", "bob/mono", "frontend/package.json", null))
            .thenReturn(new FileContent("""
                {"dependencies":{"react":"^18.2.0"}}
                """, "sha2", "base64"));
        when(depRepo.findByUserIdAndRepoFullNameAndFilePathAndPackageName(
            any(), any(), any(), any())).thenReturn(Optional.empty());

        job.run();

        ArgumentCaptor<UserDependency> cap = ArgumentCaptor.forClass(UserDependency.class);
        verify(depRepo).save(cap.capture());
        assertThat(cap.getValue().getFilePath()).isEqualTo("frontend/package.json");
        assertThat(cap.getValue().getEcosystem()).isEqualTo("NPM");
    }

    @Test
    void run_skipsUsersWithNoGithubIdentity() {
        when(identityRepo.findAll()).thenReturn(List.of());

        job.run();

        verifyNoInteractions(github, depRepo);
    }
}
