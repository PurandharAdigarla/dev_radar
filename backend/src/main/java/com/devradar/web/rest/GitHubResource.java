package com.devradar.web.rest;

import com.devradar.domain.UserGithubIdentity;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.repository.UserGithubIdentityRepository;
import com.devradar.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/github")
public class GitHubResource {

    private final UserGithubIdentityRepository identityRepo;

    public GitHubResource(UserGithubIdentityRepository identityRepo) {
        this.identityRepo = identityRepo;
    }

    public record GitHubStatusDTO(boolean linked, String login) {}

    @GetMapping("/status")
    public GitHubStatusDTO status() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();

        Optional<UserGithubIdentity> identity = identityRepo.findById(uid);
        return identity
            .map(id -> new GitHubStatusDTO(true, id.getGithubLogin()))
            .orElse(new GitHubStatusDTO(false, null));
    }
}
