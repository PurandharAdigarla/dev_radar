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
        var today = LocalDate.now();
        var todayRollup = observabilityService.getForDate(today).orElse(emptyRollup(today));
        var yesterday = today.minusDays(1);
        var yesterdayRollup = observabilityService.getForDate(yesterday).orElse(emptyRollup(yesterday));
        return toSummary(merge(todayRollup, yesterdayRollup));
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

    private MetricsDailyRollup merge(MetricsDailyRollup a, MetricsDailyRollup b) {
        var r = new MetricsDailyRollup();
        r.setDate(a.getDate());
        r.setTotalRadars(a.getTotalRadars() + b.getTotalRadars());
        r.setTotalTokensInput(a.getTotalTokensInput() + b.getTotalTokensInput());
        r.setTotalTokensOutput(a.getTotalTokensOutput() + b.getTotalTokensOutput());
        r.setSonnetCalls(a.getSonnetCalls() + b.getSonnetCalls());
        r.setHaikuCalls(a.getHaikuCalls() + b.getHaikuCalls());
        r.setCacheHits(a.getCacheHits() + b.getCacheHits());
        r.setCacheMisses(a.getCacheMisses() + b.getCacheMisses());
        r.setP50Ms(Math.max(a.getP50Ms(), b.getP50Ms()));
        r.setP95Ms(Math.max(a.getP95Ms(), b.getP95Ms()));
        long totalMs = (long) a.getAvgGenerationMs() * a.getTotalRadars() + (long) b.getAvgGenerationMs() * b.getTotalRadars();
        int totalRadars = a.getTotalRadars() + b.getTotalRadars();
        r.setAvgGenerationMs(totalRadars > 0 ? (int) (totalMs / totalRadars) : 0);
        r.setItemsIngested(a.getItemsIngested() + b.getItemsIngested());
        r.setItemsDeduped(a.getItemsDeduped() + b.getItemsDeduped());
        r.setEvalScoreRelevance(a.getEvalScoreRelevance() != null ? a.getEvalScoreRelevance() : b.getEvalScoreRelevance());
        r.setEvalScoreCitations(a.getEvalScoreCitations() != null ? a.getEvalScoreCitations() : b.getEvalScoreCitations());
        r.setEvalScoreDistinctness(a.getEvalScoreDistinctness() != null ? a.getEvalScoreDistinctness() : b.getEvalScoreDistinctness());
        return r;
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
