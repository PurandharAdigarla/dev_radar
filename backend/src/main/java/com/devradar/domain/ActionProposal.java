package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "action_proposals")
public class ActionProposal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "radar_id", nullable = false)
    private Long radarId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActionProposalKind kind;

    @Column(nullable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActionProposalStatus status;

    @Column(name = "pr_url", length = 1000)
    private String prUrl;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getRadarId() { return radarId; }
    public void setRadarId(Long v) { this.radarId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public ActionProposalKind getKind() { return kind; }
    public void setKind(ActionProposalKind v) { this.kind = v; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { this.payload = v; }
    public ActionProposalStatus getStatus() { return status; }
    public void setStatus(ActionProposalStatus v) { this.status = v; }
    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String v) { this.prUrl = v; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String v) { this.failureReason = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
