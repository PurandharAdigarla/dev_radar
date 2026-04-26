package com.devradar.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class TriggerSecretFilter extends OncePerRequestFilter {

    private final String triggerSecret;
    private final ObjectMapper json = new ObjectMapper();

    public TriggerSecretFilter(@Value("${devradar.internal.trigger-secret:}") String triggerSecret) {
        this.triggerSecret = triggerSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/internal/")) {
            chain.doFilter(request, response);
            return;
        }

        String secret = request.getHeader("X-Trigger-Secret");
        if (triggerSecret.isEmpty() || secret == null || !triggerSecret.equals(secret)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(), Map.of(
                    "status", 403,
                    "message", "Invalid or missing trigger secret",
                    "timestamp", Instant.now().toString()
            ));
            return;
        }

        var auth = new PreAuthenticatedAuthenticationToken(
                "internal-trigger", null,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
