package com.devradar.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-token-ttl-minutes}") long ttlMinutes
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    public String generateAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(email)
            .claim("userId", userId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
            .signWith(key)
            .compact();
    }

    public JwtUserDetails parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            Long userId = claims.get("userId", Long.class);
            String email = claims.getSubject();
            return new JwtUserDetails(userId, email);
        } catch (Exception e) {
            return null;
        }
    }
}
