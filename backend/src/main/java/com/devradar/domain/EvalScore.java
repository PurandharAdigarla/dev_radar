package com.devradar.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "eval_scores")
public class EvalScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "eval_run_id", nullable = false)
    private Long evalRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EvalScoreCategory category;

    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal score;

    @Column(columnDefinition = "JSON")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEvalRunId() { return evalRunId; }
    public void setEvalRunId(Long evalRunId) { this.evalRunId = evalRunId; }
    public EvalScoreCategory getCategory() { return category; }
    public void setCategory(EvalScoreCategory category) { this.category = category; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public Instant getCreatedAt() { return createdAt; }
}
