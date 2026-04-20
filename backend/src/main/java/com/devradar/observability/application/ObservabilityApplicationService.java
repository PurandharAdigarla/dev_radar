package com.devradar.observability.application;

import com.devradar.domain.MetricsDailyRollup;
import com.devradar.observability.ObservabilityService;
import com.devradar.web.rest.dto.MetricsDayDTO;
import com.devradar.web.rest.dto.ObservabilitySummaryDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ObservabilityApplicationService {

    private final ObservabilityService observabilityService;

    public ObservabilityApplicationService(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    public ObservabilitySummaryDTO getSummary() {
        var yesterday = LocalDate.now().minusDays(1);
        var rollup = observabilityService.getForDate(yesterday).orElse(emptyRollup(yesterday));
        return toSummary(rollup);
    }

    public List<MetricsDayDTO> getTimeseries(int days) {
        return observabilityService.getTimeseries(days).stream()
                .map(this::toDay)
                .toList();
    }

    private ObservabilitySummaryDTO toSummary(MetricsDailyRollup r) {
        int totalCacheOps = r.getCacheHits() + r.getCacheMisses();
        double hitRate = totalCacheOps > 0 ? (double) r.getCacheHits() / totalCacheOps : 0.0;

        return new ObservabilitySummaryDTO(
                r.getTotalRadars(),
                r.getTotalTokensInput() + r.getTotalTokensOutput(),
                r.getTotalTokensInput(),
                r.getTotalTokensOutput(),
                r.getSonnetCalls(),
                r.getHaikuCalls(),
                r.getP50Ms(),
                r.getP95Ms(),
                r.getAvgGenerationMs(),
                Math.round(hitRate * 1000.0) / 1000.0,
                r.getItemsIngested(),
                r.getEvalScoreRelevance(),
                r.getEvalScoreCitations(),
                r.getEvalScoreDistinctness()
        );
    }

    private MetricsDayDTO toDay(MetricsDailyRollup r) {
        return new MetricsDayDTO(
                r.getDate(),
                r.getTotalRadars(),
                r.getTotalTokensInput(),
                r.getTotalTokensOutput(),
                r.getSonnetCalls(),
                r.getHaikuCalls(),
                r.getCacheHits(),
                r.getCacheMisses(),
                r.getP50Ms(),
                r.getP95Ms(),
                r.getAvgGenerationMs(),
                r.getItemsIngested(),
                r.getItemsDeduped(),
                r.getEvalScoreRelevance(),
                r.getEvalScoreCitations(),
                r.getEvalScoreDistinctness()
        );
    }

    private MetricsDailyRollup emptyRollup(LocalDate date) {
        var r = new MetricsDailyRollup();
        r.setDate(date);
        r.setTotalRadars(0);
        r.setTotalTokensInput(0L);
        r.setTotalTokensOutput(0L);
        return r;
    }
}
