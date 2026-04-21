package com.devradar.security;

import com.devradar.domain.ApiKeyScope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final ApiKeyPrincipal principal;

    public ApiKeyAuthenticationToken(ApiKeyPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("SCOPE_" + principal.scope().name())));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return principal; }

    public ApiKeyScope getScope() { return principal.scope(); }
    public Long getUserId() { return principal.userId(); }
    public Long getKeyId() { return principal.keyId(); }
}
