package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.MetricsDailyRollup;
import com.devradar.repository.MetricsDailyRollupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ObservabilityResourceIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MetricsDailyRollupRepository rollupRepository;

    @Test
    void summaryShouldBePublicAndReturn200() throws Exception {
        seedRollup(LocalDate.now().minusDays(1), 5, 10000L, 2500L, 3000L, 7500L, 3500L);

        mockMvc.perform(get("/api/observability/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRadars24h").value(5))
                .andExpect(jsonPath("$.totalTokens24h").value(12500))
                .andExpect(jsonPath("$.p50Ms24h").value(3000))
                .andExpect(jsonPath("$.cacheHitRate24h").exists());
    }

    @Test
    void timeseriesShouldReturnDailyMetrics() throws Exception {
        seedRollup(LocalDate.now().minusDays(2), 3, 5000L, 1000L, 2000L, 4000L, 2500L);
        seedRollup(LocalDate.now().minusDays(1), 7, 15000L, 3000L, 3500L, 8000L, 4000L);

        mockMvc.perform(get("/api/observability/timeseries").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void summaryShouldNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/observability/summary"))
                .andExpect(status().isOk());
    }

    private void seedRollup(LocalDate date, int radars, long tokensIn, long tokensOut,
                             long p50, long p95, long avg) {
        var rollup = new MetricsDailyRollup();
        rollup.setDate(date);
        rollup.setTotalRadars(radars);
        rollup.setTotalTokensInput(tokensIn);
        rollup.setTotalTokensOutput(tokensOut);
        rollup.setSonnetCalls(radars * 4);
        rollup.setHaikuCalls(radars * 8);
        rollup.setCacheHits(radars * 2);
        rollup.setCacheMisses(radars);
        rollup.setP50Ms(p50);
        rollup.setP95Ms(p95);
        rollup.setAvgGenerationMs(avg);
        rollup.setItemsIngested(radars * 10);
        rollup.setItemsDeduped(radars * 2);
        rollup.setEvalScoreRelevance(new BigDecimal("0.850"));
        rollup.setEvalScoreCitations(new BigDecimal("0.920"));
        rollup.setEvalScoreDistinctness(new BigDecimal("0.780"));
        rollupRepository.save(rollup);
    }
}
