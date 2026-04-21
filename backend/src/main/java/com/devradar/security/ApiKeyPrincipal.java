package com.devradar.security;

import com.devradar.domain.ApiKeyScope;

public record ApiKeyPrincipal(Long userId, Long keyId, ApiKeyScope scope) {}
