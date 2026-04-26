package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User extends BaseAuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 60)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private UserPlan plan = UserPlan.FREE;

    @Column(name = "plan_expires_at")
    private Instant planExpiresAt;

    @Column(name = "trial_started_at")
    private Instant trialStartedAt;

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public UserPlan getPlan() { return plan; }
    public void setPlan(UserPlan plan) { this.plan = plan; }
    public Instant getPlanExpiresAt() { return planExpiresAt; }
    public void setPlanExpiresAt(Instant planExpiresAt) { this.planExpiresAt = planExpiresAt; }
    public Instant getTrialStartedAt() { return trialStartedAt; }
    public void setTrialStartedAt(Instant trialStartedAt) { this.trialStartedAt = trialStartedAt; }
}
