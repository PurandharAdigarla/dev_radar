package com.devradar.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper json = new ObjectMapper();
    private final int radarPerHour;

    public RateLimitFilter(RateLimitService rateLimitService,
                           @Value("${devradar.rate-limit.radar-per-hour:10}") int radarPerHour) {
        this.rateLimitService = rateLimitService;
        this.radarPerHour = radarPerHour;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isRadarCreation(request)) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            chain.doFilter(request, response);
            return;
        }

        String userId = auth.getPrincipal().toString();
        boolean allowed = rateLimitService.tryConsume("radar:" + userId, radarPerHour, Duration.ofHours(1));
        if (!allowed) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "3600");
            json.writeValue(response.getOutputStream(), Map.of(
                    "status", 429,
                    "message", "Rate limit exceeded. Try again later.",
                    "timestamp", Instant.now().toString()
            ));
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRadarCreation(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/radars".equals(request.getRequestURI());
    }
}
