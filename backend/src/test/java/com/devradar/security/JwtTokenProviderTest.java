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

    @Test
    void parse_returnsNullWhenSignatureMismatch() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
            "different-secret-must-be-at-least-256-bits-long-for-hs256-algorithm-to-work-zzz",
            60
        );
        String token = otherProvider.generateAccessToken(42L, "alice@example.com");
        // Original provider should reject token signed with a different secret
        assertThat(provider.parseAccessToken(token)).isNull();
    }

    @Test
    void parse_returnsNullForExpiredToken() throws Exception {
        // ttl = 0 minutes → token expires the instant it's issued.
        // Sleep briefly to ensure clock has crossed the expiration boundary.
        JwtTokenProvider shortLived = new JwtTokenProvider(
            "test-only-secret-must-be-at-least-256-bits-long-for-hs256-algorithm-to-work",
            0
        );
        String token = shortLived.generateAccessToken(42L, "alice@example.com");
        Thread.sleep(50);
        assertThat(shortLived.parseAccessToken(token)).isNull();
    }
}
