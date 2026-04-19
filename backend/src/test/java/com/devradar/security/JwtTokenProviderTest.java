package com.devradar.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
        "test-only-secret-must-be-at-least-256-bits-long-for-hs256-algorithm-to-work",
        60
    );

    @Test
    void generateAndParse_roundTrip() {
        String token = provider.generateAccessToken(42L, "alice@example.com");
        JwtUserDetails details = provider.parseAccessToken(token);
        assertThat(details.userId()).isEqualTo(42L);
        assertThat(details.email()).isEqualTo("alice@example.com");
    }

    @Test
    void parse_returnsNullForInvalidToken() {
        assertThat(provider.parseAccessToken("not-a-token")).isNull();
    }
}
