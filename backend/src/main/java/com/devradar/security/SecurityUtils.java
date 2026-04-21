package com.devradar.security;

import com.devradar.domain.ApiKeyScope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth instanceof ApiKeyAuthenticationToken api) return api.getUserId();
        if (auth.getDetails() instanceof JwtUserDetails d) return d.userId();
        return null;
    }

    public static ApiKeyScope getCurrentApiKeyScope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyAuthenticationToken api) return api.getScope();
        return null;
    }
}
