package com.devradar.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RateLimitServiceTest {

    StringRedisTemplate redis;
    ValueOperations<String, String> ops;
    RateLimitService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        service = new RateLimitService(redis);
    }

    @Test
    void allowsRequestsUnderLimit() {
        when(ops.increment("rate:radar:user1")).thenReturn(3L);

        boolean allowed = service.tryConsume("radar:user1", 10, Duration.ofHours(1));

        assertThat(allowed).isTrue();
    }

    @Test
    void blocksRequestsOverLimit() {
        when(ops.increment("rate:radar:user1")).thenReturn(11L);

        boolean allowed = service.tryConsume("radar:user1", 10, Duration.ofHours(1));

        assertThat(allowed).isFalse();
    }

    @Test
    void setsExpiryOnFirstRequest() {
        when(ops.increment("rate:radar:user1")).thenReturn(1L);

        service.tryConsume("radar:user1", 10, Duration.ofHours(1));

        verify(redis).expire("rate:radar:user1", Duration.ofHours(1));
    }

    @Test
    void doesNotSetExpiryOnSubsequentRequests() {
        when(ops.increment("rate:radar:user1")).thenReturn(5L);

        service.tryConsume("radar:user1", 10, Duration.ofHours(1));

        verify(redis, never()).expire(any(), any(Duration.class));
    }

    @Test
    void fallbackToAllowWhenRedisNull() {
        RateLimitService noRedis = new RateLimitService(null);

        boolean allowed = noRedis.tryConsume("radar:user1", 10, Duration.ofHours(1));

        assertThat(allowed).isTrue();
    }

    @Test
    void remainingRequestsReturnsCorrectCount() {
        when(ops.get("rate:radar:user1")).thenReturn("7");

        long remaining = service.remainingRequests("radar:user1", 10);

        assertThat(remaining).isEqualTo(3);
    }

    @Test
    void remainingRequestsReturnsMaxWhenNoRedis() {
        RateLimitService noRedis = new RateLimitService(null);

        long remaining = noRedis.remainingRequests("radar:user1", 10);

        assertThat(remaining).isEqualTo(10);
    }
}
