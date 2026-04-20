package com.devradar.repository;

import com.devradar.domain.UserGithubIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserGithubIdentityRepository extends JpaRepository<UserGithubIdentity, Long> {
    Optional<UserGithubIdentity> findByGithubUserId(Long githubUserId);
}
