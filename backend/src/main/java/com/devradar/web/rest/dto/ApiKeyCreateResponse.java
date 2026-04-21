package com.devradar.web.rest.dto;

import com.devradar.domain.ApiKeyScope;

import java.time.Instant;

public record ApiKeyCreateResponse(
    Long id,
    String name,
    ApiKeyScope scope,
    String key,
    String keyPrefix,
    Instant createdAt
) {}
