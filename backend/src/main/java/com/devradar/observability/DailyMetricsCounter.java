package com.devradar.observability;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

@Component
public class DailyMetricsCounter {

    private static final Duration TTL = Duration.ofDays(7);
    private final StringRedisTemplate redis;

    public DailyMetricsCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void incrementSonnetCalls(LocalDate date) { increment(key(date, "sonnet_calls"), 1); }
    public void incrementHaikuCalls(LocalDate date) { increment(key(date, "haiku_calls"), 1); }
    public void incrementCacheHit(LocalDate date) { increment(key(date, "cache_hits"), 1); }
    public void incrementCacheMiss(LocalDate date) { increment(key(date, "cache_misses"), 1); }
    public void incrementItemsIngested(LocalDate date, int count) { increment(key(date, "items_ingested"), count); }
    public void incrementItemsDeduped(LocalDate date, int count) { increment(key(date, "items_deduped"), count); }

    public int getSonnetCalls(LocalDate date) { return get(key(date, "sonnet_calls")); }
    public int getHaikuCalls(LocalDate date) { return get(key(date, "haiku_calls")); }
    public int getCacheHits(LocalDate date) { return get(key(date, "cache_hits")); }
    public int getCacheMisses(LocalDate date) { return get(key(date, "cache_misses")); }
    public int getItemsIngested(LocalDate date) { return get(key(date, "items_ingested")); }
    public int getItemsDeduped(LocalDate date) { return get(key(date, "items_deduped")); }

    private void increment(String key, int delta) {
        redis.opsForValue().increment(key, delta);
        redis.expire(key, TTL);
    }

    private int get(String key) {
        String val = redis.opsForValue().get(key);
        return val == null ? 0 : Integer.parseInt(val);
    }

    private String key(LocalDate date, String metric) {
        return "metrics:" + date + ":" + metric;
    }
}
