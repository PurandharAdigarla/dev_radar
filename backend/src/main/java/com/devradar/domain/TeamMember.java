package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "team_members")
@IdClass(TeamMemberId.class)
public class TeamMember {
    @Id
    @Column(name = "team_id")
    private Long teamId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TeamRole role = TeamRole.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    void onCreate() { joinedAt = Instant.now(); }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public TeamRole getRole() { return role; }
    public void setRole(TeamRole role) { this.role = role; }
    public Instant getJoinedAt() { return joinedAt; }
}
