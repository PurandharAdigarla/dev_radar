package com.devradar.apikey.application;

import com.devradar.apikey.ApiKeyService;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.ApiKeyCreateRequest;
import com.devradar.web.rest.dto.ApiKeyCreateResponse;
import com.devradar.web.rest.dto.ApiKeySummaryDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ApiKeyApplicationService {

    private final ApiKeyService service;

    public ApiKeyApplicationService(ApiKeyService service) { this.service = service; }

    public ApiKeyCreateResponse create(ApiKeyCreateRequest req) {
        Long uid = currentUser();
        ApiKeyService.GeneratedKey g = service.generate(uid, req.name(), req.scope());
        return new ApiKeyCreateResponse(g.id(), g.name(), g.scope(), g.rawKey(), g.keyPrefix(), Instant.now());
    }

    public List<ApiKeySummaryDTO> list() {
        Long uid = currentUser();
        return service.list(uid).stream()
            .map(k -> new ApiKeySummaryDTO(k.getId(), k.getName(), k.getScope(),
                k.getKeyPrefix(), k.getCreatedAt(), k.getLastUsedAt()))
            .toList();
    }

    public void revoke(Long keyId) {
        service.revoke(currentUser(), keyId);
    }

    private Long currentUser() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return uid;
    }
}
