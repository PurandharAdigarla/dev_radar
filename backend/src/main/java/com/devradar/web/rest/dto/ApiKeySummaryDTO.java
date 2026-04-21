package com.devradar.web.rest.dto;

import com.devradar.domain.ApiKeyScope;

import java.time.Instant;

public record ApiKeySummaryDTO(
    Long id,
    String name,
    ApiKeyScope scope,
    String keyPrefix,
    Instant createdAt,
    Instant lastUsedAt
) {}
