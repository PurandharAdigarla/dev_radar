package com.devradar.observability;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

@Component
public class DailyMetricsCounter {

    private static final Duration TTL = Duration.ofDays(7);
    @Nullable
    private final StringRedisTemplate redis;

    public DailyMetricsCounter(@Nullable StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void incrementSonnetCalls(LocalDate date) { increment(key(date, "sonnet_calls"), 1); }
    public void incrementHaikuCalls(LocalDate date) { increment(key(date, "haiku_calls"), 1); }
    public void incrementCacheHit(LocalDate date) { increment(key(date, "cache_hits"), 1); }
    public void incrementCacheMiss(LocalDate date) { increment(key(date, "cache_misses"), 1); }
    public void incrementItemsIngested(LocalDate date, int count) { increment(key(date, "items_ingested"), count); }
    public void incrementItemsDeduped(LocalDate date, int count) { increment(key(date, "items_deduped"), count); }

    public void incrementTokens(LocalDate date, String provider, String direction, int count) {
        increment(key(date, "tokens:" + provider + ":" + direction), count);
    }

    public int getTokens(LocalDate date, String provider, String direction) {
        return get(key(date, "tokens:" + provider + ":" + direction));
    }

    public double estimatedCostUsd(LocalDate date) {
        double cost = 0.0;

        int geminiIn = getTokens(date, "gemini", "input");
        int geminiOut = getTokens(date, "gemini", "output");
        cost += geminiIn * (0.15 / 1_000_000.0);
        cost += geminiOut * (0.60 / 1_000_000.0);

        int anthropicIn = getTokens(date, "anthropic", "input");
        int anthropicOut = getTokens(date, "anthropic", "output");
        cost += anthropicIn * (3.00 / 1_000_000.0);
        cost += anthropicOut * (15.00 / 1_000_000.0);

        return cost;
    }

    public int getSonnetCalls(LocalDate date) { return get(key(date, "sonnet_calls")); }
    public int getHaikuCalls(LocalDate date) { return get(key(date, "haiku_calls")); }
    public int getCacheHits(LocalDate date) { return get(key(date, "cache_hits")); }
    public int getCacheMisses(LocalDate date) { return get(key(date, "cache_misses")); }
    public int getItemsIngested(LocalDate date) { return get(key(date, "items_ingested")); }
    public int getItemsDeduped(LocalDate date) { return get(key(date, "items_deduped")); }

    private void increment(String key, int delta) {
        if (redis == null) return;
        redis.opsForValue().increment(key, delta);
        redis.expire(key, TTL);
    }

    private int get(String key) {
        if (redis == null) return 0;
        String val = redis.opsForValue().get(key);
        return val == null ? 0 : Integer.parseInt(val);
    }

    private String key(LocalDate date, String metric) {
        return "metrics:" + date + ":" + metric;
    }
}
