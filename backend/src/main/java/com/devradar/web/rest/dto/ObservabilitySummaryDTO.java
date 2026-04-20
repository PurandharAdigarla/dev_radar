package com.devradar.web.rest.dto;

import java.math.BigDecimal;

public record ObservabilitySummaryDTO(
        int totalRadars24h,
        long totalTokens24h,
        long totalTokensInput24h,
        long totalTokensOutput24h,
        int sonnetCalls24h,
        int haikuCalls24h,
        long p50Ms24h,
        long p95Ms24h,
        long avgGenerationMs24h,
        double cacheHitRate24h,
        int itemsIngested24h,
        BigDecimal evalScoreRelevance,
        BigDecimal evalScoreCitations,
        BigDecimal evalScoreDistinctness
) {}
