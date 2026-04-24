package com.devradar.repository;

import com.devradar.domain.OAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface OAuthStateRepository extends JpaRepository<OAuthState, String> {

    @Modifying
    @Transactional
    void deleteByExpiresAtBefore(Instant cutoff);
}
