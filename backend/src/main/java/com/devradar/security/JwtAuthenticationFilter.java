package com.devradar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider provider;

    public JwtAuthenticationFilter(JwtTokenProvider provider) { this.provider = provider; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            JwtUserDetails details = provider.parseAccessToken(token);
            if (details != null) {
                var auth = new UsernamePasswordAuthenticationToken(details.email(), null, List.of());
                auth.setDetails(details);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Tokens normally come from the Authorization header. SSE streams are an
     * exception — browser-native EventSource cannot set headers — so we also
     * accept a ?token= query param, but ONLY for /api/radars/{id}/stream.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (isSseStreamPath(request.getRequestURI())) {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken;
            }
        }
        return null;
    }

    private boolean isSseStreamPath(String uri) {
        if (uri == null || !uri.startsWith("/api/radars/")) return false;
        if (!uri.endsWith("/stream")) return false;
        String middle = uri.substring("/api/radars/".length(), uri.length() - "/stream".length());
        return !middle.isEmpty() && middle.indexOf('/') < 0;
    }
}
