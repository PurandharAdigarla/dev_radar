package com.devradar.repository;

import com.devradar.domain.UserApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserApiKeyRepository extends JpaRepository<UserApiKey, Long> {
    Optional<UserApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);
    List<UserApiKey> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);
}
