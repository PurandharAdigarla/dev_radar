package com.devradar.ai;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class AiSummaryCache {

    private static final Duration TTL = Duration.ofDays(30);
    private static final String PREFIX = "ai:summary:";

    private final StringRedisTemplate redis;

    public AiSummaryCache(StringRedisTemplate redis) { this.redis = redis; }

    public Optional<String> get(List<Long> sourceItemIds) {
        String key = buildKey(sourceItemIds);
        String v = redis.opsForValue().get(key);
        return Optional.ofNullable(v);
    }

    public void put(List<Long> sourceItemIds, String summary) {
        String key = buildKey(sourceItemIds);
        redis.opsForValue().set(key, summary, TTL);
    }

    private static String buildKey(List<Long> ids) {
        List<Long> sorted = ids.stream().sorted().toList();
        String joined = String.join(",", sorted.stream().map(String::valueOf).toList());
        return PREFIX + sha256Hex(joined);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
