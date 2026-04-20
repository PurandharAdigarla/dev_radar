package com.devradar.observability;

import com.devradar.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyMetricsCounterTest extends AbstractIntegrationTest {

    @Autowired
    private DailyMetricsCounter counter;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void cleanRedis() {
        var keys = redis.keys("metrics:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void shouldIncrementAndReadSonnetCalls() {
        var today = LocalDate.of(2026, 4, 20);
        counter.incrementSonnetCalls(today);
        counter.incrementSonnetCalls(today);
        counter.incrementSonnetCalls(today);
        assertThat(counter.getSonnetCalls(today)).isEqualTo(3);
    }

    @Test
    void shouldIncrementAndReadHaikuCalls() {
        var today = LocalDate.of(2026, 4, 20);
        counter.incrementHaikuCalls(today);
        assertThat(counter.getHaikuCalls(today)).isEqualTo(1);
    }

    @Test
    void shouldTrackCacheHitsAndMisses() {
        var today = LocalDate.of(2026, 4, 20);
        counter.incrementCacheHit(today);
        counter.incrementCacheHit(today);
        counter.incrementCacheMiss(today);
        assertThat(counter.getCacheHits(today)).isEqualTo(2);
        assertThat(counter.getCacheMisses(today)).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroForMissingDate() {
        var missing = LocalDate.of(2020, 1, 1);
        assertThat(counter.getSonnetCalls(missing)).isZero();
        assertThat(counter.getCacheHits(missing)).isZero();
    }

    @Test
    void shouldTrackItemsIngestedAndDeduped() {
        var today = LocalDate.of(2026, 4, 20);
        counter.incrementItemsIngested(today, 25);
        counter.incrementItemsDeduped(today, 5);
        assertThat(counter.getItemsIngested(today)).isEqualTo(25);
        assertThat(counter.getItemsDeduped(today)).isEqualTo(5);
    }
}
