package com.devradar.ingest;

import com.devradar.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DedupServiceTest extends AbstractIntegrationTest {

    @Autowired DedupService dedup;
    @Autowired StringRedisTemplate redis;

    @Test
    void tryAcquire_returnsTrueFirstTime_falseSecondTime() {
        String key = "ingest:HN:" + UUID.randomUUID();

        boolean first = dedup.tryAcquire(key, Duration.ofMinutes(5));
        boolean second = dedup.tryAcquire(key, Duration.ofMinutes(5));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void release_clearsLock_soNextAcquireSucceeds() {
        String key = "ingest:HN:" + UUID.randomUUID();
        dedup.tryAcquire(key, Duration.ofMinutes(5));
        dedup.release(key);

        assertThat(dedup.tryAcquire(key, Duration.ofMinutes(5))).isTrue();
    }
}
