package com.devradar.action;

import com.devradar.AbstractIntegrationTest;
import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.*;
import com.devradar.github.GitHubApiClient;
import com.devradar.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutoPrExecutorTest extends AbstractIntegrationTest {

    @MockBean GitHubApiClient gh;

    @Autowired AutoPrExecutor executor;
    @Autowired UserRepository userRepo;
    @Autowired UserGithubIdentityRepository identityRepo;
    @Autowired RadarRepository radarRepo;
    @Autowired ActionProposalRepository proposalRepo;
    @Autowired TokenEncryptor encryptor;
    @Autowired ObjectMapper json;

    @Test
    void execute_createsBranch_putsFile_opensPR_updatesProposal() throws Exception {
        User u = new User();
        u.setEmail("autopr@example.com");
        u.setDisplayName("A");
        u.setActive(true);
        u = userRepo.save(u);

        UserGithubIdentity gid = new UserGithubIdentity();
        gid.setUserId(u.getId());
        gid.setGithubUserId(11111L);
        gid.setGithubLogin("alice");
        gid.setAccessTokenEncrypted(encryptor.encrypt("gho_pr_token"));
        gid.setGrantedScopes("repo");
        identityRepo.save(gid);

        Radar r = new Radar();
        r.setUserId(u.getId());
        r.setStatus(RadarStatus.READY);
        r.setPeriodStart(Instant.now().minusSeconds(86400));
        r.setPeriodEnd(Instant.now());
        r = radarRepo.save(r);

        ObjectNode payload = json.createObjectNode();
        payload.put("repo", "alice/repo1");
        payload.put("file_path", "pom.xml");
        payload.put("file_sha", "old-sha");
        payload.put("package", "jackson-databind");
        payload.put("current_version", "2.16.2");
        payload.put("ghsa_id", "GHSA-test-9876");

        ActionProposal p = new ActionProposal();
        p.setRadarId(r.getId());
        p.setUserId(u.getId());
        p.setKind(ActionProposalKind.auto_pr_cve);
        p.setStatus(ActionProposalStatus.PROPOSED);
        p.setPayload(json.writeValueAsString(payload));
        p = proposalRepo.save(p);

        String pomBefore = "<dependency><artifactId>jackson-databind</artifactId><version>2.16.2</version></dependency>";
        when(gh.getFileContent(anyString(), eq("alice/repo1"), eq("pom.xml"), isNull()))
            .thenReturn(new GitHubApiClient.FileContent(pomBefore, "old-sha", java.util.Base64.getEncoder().encodeToString(pomBefore.getBytes())));
        when(gh.listRepos(anyString())).thenReturn(List.of(new GitHubApiClient.RepoInfo("alice/repo1", "main")));
        when(gh.createPullRequest(anyString(), eq("alice/repo1"), anyString(), anyString(), anyString(), anyString()))
            .thenReturn("https://github.com/alice/repo1/pull/7");

        executor.execute(p.getId(), "2.16.3");

        var updated = proposalRepo.findById(p.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ActionProposalStatus.EXECUTED);
        assertThat(updated.getPrUrl()).isEqualTo("https://github.com/alice/repo1/pull/7");
        verify(gh).createBranch(anyString(), eq("alice/repo1"), contains("dev-radar/cve-"), eq("main"));
        verify(gh).putFileContent(anyString(), any());
    }
}
