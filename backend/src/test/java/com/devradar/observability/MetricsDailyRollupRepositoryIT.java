package com.devradar.observability;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.MetricsDailyRollup;
import com.devradar.repository.MetricsDailyRollupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsDailyRollupRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private MetricsDailyRollupRepository repository;

    @Test
    void shouldUpsertAndRetrieveRollup() {
        var rollup = new MetricsDailyRollup();
        rollup.setDate(LocalDate.of(2026, 4, 20));
        rollup.setTotalRadars(10);
        rollup.setTotalTokensInput(50000L);
        rollup.setTotalTokensOutput(12000L);
        rollup.setSonnetCalls(40);
        rollup.setHaikuCalls(80);
        rollup.setCacheHits(15);
        rollup.setCacheMisses(5);
        rollup.setP50Ms(2500L);
        rollup.setP95Ms(8000L);
        rollup.setAvgGenerationMs(3200L);
        rollup.setItemsIngested(150);
        rollup.setItemsDeduped(30);
        rollup.setEvalScoreRelevance(new BigDecimal("0.850"));
        rollup.setEvalScoreCitations(new BigDecimal("0.920"));
        rollup.setEvalScoreDistinctness(new BigDecimal("0.780"));
        repository.save(rollup);

        var found = repository.findById(LocalDate.of(2026, 4, 20));
        assertThat(found).isPresent();
        assertThat(found.get().getTotalRadars()).isEqualTo(10);
        assertThat(found.get().getSonnetCalls()).isEqualTo(40);
    }

    @Test
    void shouldFindRecentDays() {
        for (int i = 0; i < 3; i++) {
            var rollup = new MetricsDailyRollup();
            rollup.setDate(LocalDate.of(2026, 4, 18 + i));
            rollup.setTotalRadars(i + 1);
            rollup.setTotalTokensInput(0L);
            rollup.setTotalTokensOutput(0L);
            repository.save(rollup);
        }

        List<MetricsDailyRollup> recent = repository.findByDateBetweenOrderByDateDesc(
                LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 20));
        assertThat(recent).hasSizeGreaterThanOrEqualTo(3);
        assertThat(recent.getFirst().getDate()).isAfterOrEqualTo(recent.getLast().getDate());
    }
}
