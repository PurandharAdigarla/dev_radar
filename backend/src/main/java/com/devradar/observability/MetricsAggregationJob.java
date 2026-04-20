package com.devradar.observability;

import com.devradar.domain.MetricsDailyRollup;
import com.devradar.repository.MetricsDailyRollupRepository;
import com.devradar.repository.RadarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class MetricsAggregationJob {

    private static final Logger log = LoggerFactory.getLogger(MetricsAggregationJob.class);

    private final RadarRepository radarRepository;
    private final MetricsDailyRollupRepository rollupRepository;
    private final DailyMetricsCounter dailyMetrics;

    public MetricsAggregationJob(RadarRepository radarRepository,
                                  MetricsDailyRollupRepository rollupRepository,
                                  DailyMetricsCounter dailyMetrics) {
        this.radarRepository = radarRepository;
        this.rollupRepository = rollupRepository;
        this.dailyMetrics = dailyMetrics;
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void aggregateYesterday() {
        aggregateForDate(LocalDate.now().minusDays(1));
    }

    @Transactional
    public void aggregateForDate(LocalDate date) {
        log.info("aggregating metrics for date={}", date);

        var rollup = rollupRepository.findById(date).orElseGet(() -> {
            var r = new MetricsDailyRollup();
            r.setDate(date);
            return r;
        });

        rollup.setTotalRadars(radarRepository.countReadyByDate(date));
        rollup.setTotalTokensInput(radarRepository.sumInputTokensByDate(date));
        rollup.setTotalTokensOutput(radarRepository.sumOutputTokensByDate(date));

        List<Long> latencies = radarRepository.findGenerationMsByDate(date);
        if (!latencies.isEmpty()) {
            rollup.setP50Ms(percentile(latencies, 50));
            rollup.setP95Ms(percentile(latencies, 95));
            long sum = latencies.stream().mapToLong(Long::longValue).sum();
            rollup.setAvgGenerationMs(sum / latencies.size());
        }

        rollup.setSonnetCalls(dailyMetrics.getSonnetCalls(date));
        rollup.setHaikuCalls(dailyMetrics.getHaikuCalls(date));
        rollup.setCacheHits(dailyMetrics.getCacheHits(date));
        rollup.setCacheMisses(dailyMetrics.getCacheMisses(date));
        rollup.setItemsIngested(dailyMetrics.getItemsIngested(date));
        rollup.setItemsDeduped(dailyMetrics.getItemsDeduped(date));

        rollupRepository.save(rollup);
        log.info("metrics aggregation complete for date={} radars={}", date, rollup.getTotalRadars());
    }

    static long percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
