package com.devradar.action;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.ActionProposal;
import com.devradar.domain.ActionProposalStatus;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.github.GitHubFileMutation;
import com.devradar.repository.ActionProposalRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class AutoPrExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AutoPrExecutor.class);
    private final ObjectMapper json = new ObjectMapper();

    private final GitHubApiClient gh;
    private final ActionProposalRepository proposalRepo;
    private final UserGithubIdentityRepository identityRepo;
    private final TokenEncryptor encryptor;

    public AutoPrExecutor(GitHubApiClient gh, ActionProposalRepository proposalRepo,
                          UserGithubIdentityRepository identityRepo, TokenEncryptor encryptor) {
        this.gh = gh;
        this.proposalRepo = proposalRepo;
        this.identityRepo = identityRepo;
        this.encryptor = encryptor;
    }

    @Transactional
    public void execute(Long proposalId, String fixVersion) {
        ActionProposal p = proposalRepo.findById(proposalId).orElseThrow();
        if (p.getStatus() != ActionProposalStatus.PROPOSED) {
            throw new IllegalStateException("proposal not in PROPOSED state: " + p.getStatus());
        }
        try {
            JsonNode payload = json.readTree(p.getPayload());
            String repo = payload.get("repo").asText();
            String filePath = payload.get("file_path").asText();
            String pkg = payload.get("package").asText();
            String currentVersion = payload.get("current_version").asText();
            String ghsaId = payload.get("ghsa_id").asText();

            UserGithubIdentity identity = identityRepo.findById(p.getUserId()).orElseThrow();
            String token = encryptor.decrypt(identity.getAccessTokenEncrypted());

            String defaultBranch = gh.listRepos(token).stream()
                .filter(rinfo -> rinfo.fullName().equals(repo))
                .findFirst()
                .map(GitHubApiClient.RepoInfo::defaultBranch)
                .orElse("main");

            GitHubApiClient.FileContent current = gh.getFileContent(token, repo, filePath, null);
            String newText = current.text().replace(currentVersion, fixVersion);
            if (newText.equals(current.text())) {
                throw new RuntimeException("no occurrence of version " + currentVersion + " in " + filePath);
            }

            String branch = "dev-radar/cve-" + ghsaId.toLowerCase().replaceAll("[^a-z0-9-]", "-");
            gh.createBranch(token, repo, branch, defaultBranch);

            gh.putFileContent(token, new GitHubFileMutation(
                repo, filePath,
                Base64.getEncoder().encodeToString(newText.getBytes(StandardCharsets.UTF_8)),
                current.sha(),
                "chore(security): bump " + pkg + " to " + fixVersion + " (fixes " + ghsaId + ")",
                branch
            ));

            String prTitle = "chore(security): bump " + pkg + " to " + fixVersion + " (fixes " + ghsaId + ")";
            String prBody = "Automated PR proposed by Dev Radar.\n\nFixes [" + ghsaId + "](https://github.com/advisories/" + ghsaId + ").\n\nDiff: bumps `" + pkg + "` from `" + currentVersion + "` to `" + fixVersion + "` in `" + filePath + "`.";
            String prUrl = gh.createPullRequest(token, repo, prTitle, prBody, branch, defaultBranch);

            p.setStatus(ActionProposalStatus.EXECUTED);
            p.setPrUrl(prUrl);
            proposalRepo.save(p);
            LOG.info("auto-pr executed proposal={} pr_url={}", proposalId, prUrl);
        } catch (Exception e) {
            LOG.error("auto-pr failed proposal={}: {}", proposalId, e.toString(), e);
            p.setStatus(ActionProposalStatus.FAILED);
            p.setFailureReason(e.getMessage());
            proposalRepo.save(p);
            throw new RuntimeException("auto-pr failed: " + e.getMessage(), e);
        }
    }
}
