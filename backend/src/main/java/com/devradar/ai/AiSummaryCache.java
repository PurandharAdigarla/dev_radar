package com.devradar.ai;

import com.devradar.observability.DailyMetricsCounter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class AiSummaryCache {

    private static final Duration TTL = Duration.ofDays(30);
    private static final String PREFIX = "ai:summary:";

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final DailyMetricsCounter dailyMetrics;

    public AiSummaryCache(StringRedisTemplate redis, MeterRegistry meterRegistry, DailyMetricsCounter dailyMetrics) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
        this.dailyMetrics = dailyMetrics;
    }

    public Optional<String> get(List<Long> sourceItemIds) {
        String key = buildKey(sourceItemIds);
        String v = redis.opsForValue().get(key);
        var result = Optional.ofNullable(v);

        var today = LocalDate.now();
        if (result.isPresent()) {
            meterRegistry.counter("ai.summary.cache", "result", "hit").increment();
            dailyMetrics.incrementCacheHit(today);
        } else {
            meterRegistry.counter("ai.summary.cache", "result", "miss").increment();
            dailyMetrics.incrementCacheMiss(today);
        }

        return result;
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
