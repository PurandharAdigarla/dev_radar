package com.devradar.web.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MetricsDayDTO(
        LocalDate date,
        int totalRadars,
        long totalTokensInput,
        long totalTokensOutput,
        int sonnetCalls,
        int haikuCalls,
        int cacheHits,
        int cacheMisses,
        long p50Ms,
        long p95Ms,
        long avgGenerationMs,
        int itemsIngested,
        int itemsDeduped,
        BigDecimal evalScoreRelevance,
        BigDecimal evalScoreCitations,
        BigDecimal evalScoreDistinctness
) {}
