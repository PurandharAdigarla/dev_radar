package com.devradar.security;

import com.devradar.apikey.ApiKeyUsedEvent;
import com.devradar.domain.UserApiKey;
import com.devradar.repository.UserApiKeyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final UserApiKeyRepository repo;
    private final ApiKeyHasher hasher;
    private final ApplicationEventPublisher events;
    private final MeterRegistry meters;

    public ApiKeyAuthenticationFilter(UserApiKeyRepository repo,
                                       ApiKeyHasher hasher,
                                       ApplicationEventPublisher events,
                                       MeterRegistry meters) {
        this.repo = repo;
        this.hasher = hasher;
        this.events = events;
        this.meters = meters;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        if (!req.getRequestURI().startsWith("/mcp/")) {
            chain.doFilter(req, resp);
            return;
        }

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            meters.counter("mcp.auth.failures", "reason", "missing").increment();
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String raw = header.substring(BEARER.length()).trim();
        if (!raw.startsWith(ApiKeyGenerator.PREFIX)) {
            meters.counter("mcp.auth.failures", "reason", "malformed").increment();
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String hash = hasher.hash(raw);
        Optional<UserApiKey> keyOpt = repo.findByKeyHashAndRevokedAtIsNull(hash);
        if (keyOpt.isEmpty()) {
            meters.counter("mcp.auth.failures", "reason", "unknown_key").increment();
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        UserApiKey key = keyOpt.get();
        ApiKeyPrincipal principal = new ApiKeyPrincipal(key.getUserId(), key.getId(), key.getScope());
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken(principal));
        events.publishEvent(new ApiKeyUsedEvent(key.getId()));

        chain.doFilter(req, resp);
    }
}
