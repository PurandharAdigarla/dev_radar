package com.devradar.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "metrics_daily_rollup")
public class MetricsDailyRollup {

    @Id
    private LocalDate date;

    @Column(name = "total_radars", nullable = false)
    private int totalRadars;

    @Column(name = "total_tokens_input", nullable = false)
    private long totalTokensInput;

    @Column(name = "total_tokens_output", nullable = false)
    private long totalTokensOutput;

    @Column(name = "sonnet_calls", nullable = false)
    private int sonnetCalls;

    @Column(name = "haiku_calls", nullable = false)
    private int haikuCalls;

    @Column(name = "cache_hits", nullable = false)
    private int cacheHits;

    @Column(name = "cache_misses", nullable = false)
    private int cacheMisses;

    @Column(name = "p50_ms", nullable = false)
    private long p50Ms;

    @Column(name = "p95_ms", nullable = false)
    private long p95Ms;

    @Column(name = "avg_generation_ms", nullable = false)
    private long avgGenerationMs;

    @Column(name = "items_ingested", nullable = false)
    private int itemsIngested;

    @Column(name = "items_deduped", nullable = false)
    private int itemsDeduped;

    @Column(name = "eval_score_relevance", precision = 5, scale = 3)
    private BigDecimal evalScoreRelevance;

    @Column(name = "eval_score_citations", precision = 5, scale = 3)
    private BigDecimal evalScoreCitations;

    @Column(name = "eval_score_distinctness", precision = 5, scale = 3)
    private BigDecimal evalScoreDistinctness;

    @Column(name = "created_at", nullable = false, updatable = false,
            insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false,
            insertable = false, updatable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant updatedAt;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getTotalRadars() { return totalRadars; }
    public void setTotalRadars(int totalRadars) { this.totalRadars = totalRadars; }

    public long getTotalTokensInput() { return totalTokensInput; }
    public void setTotalTokensInput(long totalTokensInput) { this.totalTokensInput = totalTokensInput; }

    public long getTotalTokensOutput() { return totalTokensOutput; }
    public void setTotalTokensOutput(long totalTokensOutput) { this.totalTokensOutput = totalTokensOutput; }

    public int getSonnetCalls() { return sonnetCalls; }
    public void setSonnetCalls(int sonnetCalls) { this.sonnetCalls = sonnetCalls; }

    public int getHaikuCalls() { return haikuCalls; }
    public void setHaikuCalls(int haikuCalls) { this.haikuCalls = haikuCalls; }

    public int getCacheHits() { return cacheHits; }
    public void setCacheHits(int cacheHits) { this.cacheHits = cacheHits; }

    public int getCacheMisses() { return cacheMisses; }
    public void setCacheMisses(int cacheMisses) { this.cacheMisses = cacheMisses; }

    public long getP50Ms() { return p50Ms; }
    public void setP50Ms(long p50Ms) { this.p50Ms = p50Ms; }

    public long getP95Ms() { return p95Ms; }
    public void setP95Ms(long p95Ms) { this.p95Ms = p95Ms; }

    public long getAvgGenerationMs() { return avgGenerationMs; }
    public void setAvgGenerationMs(long avgGenerationMs) { this.avgGenerationMs = avgGenerationMs; }

    public int getItemsIngested() { return itemsIngested; }
    public void setItemsIngested(int itemsIngested) { this.itemsIngested = itemsIngested; }

    public int getItemsDeduped() { return itemsDeduped; }
    public void setItemsDeduped(int itemsDeduped) { this.itemsDeduped = itemsDeduped; }

    public BigDecimal getEvalScoreRelevance() { return evalScoreRelevance; }
    public void setEvalScoreRelevance(BigDecimal evalScoreRelevance) { this.evalScoreRelevance = evalScoreRelevance; }

    public BigDecimal getEvalScoreCitations() { return evalScoreCitations; }
    public void setEvalScoreCitations(BigDecimal evalScoreCitations) { this.evalScoreCitations = evalScoreCitations; }

    public BigDecimal getEvalScoreDistinctness() { return evalScoreDistinctness; }
    public void setEvalScoreDistinctness(BigDecimal evalScoreDistinctness) { this.evalScoreDistinctness = evalScoreDistinctness; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
