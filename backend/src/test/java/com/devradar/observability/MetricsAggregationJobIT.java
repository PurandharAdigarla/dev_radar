package com.devradar.observability;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.User;
import com.devradar.repository.MetricsDailyRollupRepository;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsAggregationJobIT extends AbstractIntegrationTest {

    @Autowired
    private MetricsAggregationJob job;

    @Autowired
    private RadarRepository radarRepository;

    @Autowired
    private MetricsDailyRollupRepository rollupRepository;

    @Autowired
    private DailyMetricsCounter dailyMetrics;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldAggregateYesterdayMetrics() {
        var yesterday = LocalDate.now().minusDays(1);
        var yesterdayInstant = yesterday.atStartOfDay(ZoneId.of("UTC")).plusHours(12).toInstant();

        var user1 = createUser("aggr-user1@test.com", "User One");
        var user2 = createUser("aggr-user2@test.com", "User Two");

        var radar1 = createReadyRadar(user1.getId(), 3000L, 500, 100, 400, yesterdayInstant);
        var radar2 = createReadyRadar(user2.getId(), 5000L, 800, 200, 600, yesterdayInstant);
        radarRepository.save(radar1);
        radarRepository.save(radar2);

        dailyMetrics.incrementSonnetCalls(yesterday);
        dailyMetrics.incrementSonnetCalls(yesterday);
        dailyMetrics.incrementHaikuCalls(yesterday);
        dailyMetrics.incrementCacheHit(yesterday);
        dailyMetrics.incrementCacheMiss(yesterday);
        dailyMetrics.incrementCacheMiss(yesterday);
        dailyMetrics.incrementItemsIngested(yesterday, 50);
        dailyMetrics.incrementItemsDeduped(yesterday, 10);

        job.aggregateForDate(yesterday);

        var rollup = rollupRepository.findById(yesterday);
        assertThat(rollup).isPresent();
        var r = rollup.get();
        assertThat(r.getTotalRadars()).isEqualTo(2);
        assertThat(r.getSonnetCalls()).isEqualTo(2);
        assertThat(r.getHaikuCalls()).isEqualTo(1);
        assertThat(r.getCacheHits()).isEqualTo(1);
        assertThat(r.getCacheMisses()).isEqualTo(2);
        assertThat(r.getItemsIngested()).isEqualTo(50);
        assertThat(r.getItemsDeduped()).isEqualTo(10);
    }

    private Radar createReadyRadar(Long userId, long generationMs, int tokens, int inputTokens, int outputTokens, Instant generatedAt) {
        var radar = new Radar();
        radar.setUserId(userId);
        radar.setPeriodStart(Instant.now().minusSeconds(604800));
        radar.setPeriodEnd(Instant.now());
        radar.setStatus(RadarStatus.READY);
        radar.setGeneratedAt(generatedAt);
        radar.setGenerationMs(generationMs);
        radar.setTokenCount(tokens);
        radar.setInputTokenCount(inputTokens);
        radar.setOutputTokenCount(outputTokens);
        return radar;
    }

    private User createUser(String email, String displayName) {
        var user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash("$2a$10$dummyhash");
        return userRepository.save(user);
    }
}
