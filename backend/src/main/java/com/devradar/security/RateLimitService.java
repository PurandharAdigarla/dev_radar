package com.devradar.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitService {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitService.class);

    @Nullable
    private final StringRedisTemplate redis;

    private record InMemoryEntry(AtomicInteger count, Instant expiresAt) {}

    private final ConcurrentHashMap<String, InMemoryEntry> inMemoryCounters = new ConcurrentHashMap<>();

    public RateLimitService(@Nullable StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryConsume(String key, int maxRequests, Duration window) {
        if (redis == null) {
            return tryConsumeInMemory(key, maxRequests, window);
        }
        try {
            String redisKey = "rate:" + key;
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) return true;
            if (count == 1L) {
                redis.expire(redisKey, window);
            }
            return count <= maxRequests;
        } catch (Exception e) {
            LOG.warn("Redis rate-limit check failed, falling back to in-memory: {}", e.getMessage());
            return tryConsumeInMemory(key, maxRequests, window);
        }
    }

    public long remainingRequests(String key, int maxRequests) {
        if (redis == null) {
            return remainingInMemory(key, maxRequests);
        }
        try {
            String redisKey = "rate:" + key;
            String val = redis.opsForValue().get(redisKey);
            if (val == null) return maxRequests;
            long used = Long.parseLong(val);
            return Math.max(0, maxRequests - used);
        } catch (Exception e) {
            LOG.warn("Redis rate-limit remaining check failed: {}", e.getMessage());
            return remainingInMemory(key, maxRequests);
        }
    }

    private boolean tryConsumeInMemory(String key, int maxRequests, Duration window) {
        Instant now = Instant.now();
        InMemoryEntry entry = inMemoryCounters.compute(key, (k, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)) {
                return new InMemoryEntry(new AtomicInteger(1), now.plus(window));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return entry.count().get() <= maxRequests;
    }

    private long remainingInMemory(String key, int maxRequests) {
        InMemoryEntry entry = inMemoryCounters.get(key);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return maxRequests;
        }
        return Math.max(0, maxRequests - entry.count().get());
    }

    @Scheduled(fixedRate = 60_000)
    void cleanupExpiredEntries() {
        Instant now = Instant.now();
        inMemoryCounters.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
