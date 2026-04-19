package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "radars")
public class Radar {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RadarStatus status;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "generation_ms")
    private Long generationMs;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant v) { this.periodStart = v; }
    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant v) { this.periodEnd = v; }
    public RadarStatus getStatus() { return status; }
    public void setStatus(RadarStatus v) { this.status = v; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant v) { this.generatedAt = v; }
    public Long getGenerationMs() { return generationMs; }
    public void setGenerationMs(Long v) { this.generationMs = v; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer v) { this.tokenCount = v; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String v) { this.errorCode = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public Instant getCreatedAt() { return createdAt; }
}
