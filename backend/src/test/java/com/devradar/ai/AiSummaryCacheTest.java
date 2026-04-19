package com.devradar.ai;

import com.devradar.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AiSummaryCacheTest extends AbstractIntegrationTest {

    @Autowired AiSummaryCache cache;

    @Test
    void putAndGet_roundTrip() {
        List<Long> ids = List.of(1L, 2L, 3L);
        cache.put(ids, "Spring Boot 3.5 ships virtual threads.");

        Optional<String> got = cache.get(ids);
        assertThat(got).contains("Spring Boot 3.5 ships virtual threads.");
    }

    @Test
    void get_missingKey_returnsEmpty() {
        assertThat(cache.get(List.of(99999L))).isEmpty();
    }

    @Test
    void key_isOrderIndependent() {
        cache.put(List.of(10L, 20L, 30L), "summary text");
        assertThat(cache.get(List.of(30L, 10L, 20L))).contains("summary text");
    }
}
