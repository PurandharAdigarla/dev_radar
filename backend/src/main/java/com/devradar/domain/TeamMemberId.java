package com.devradar.domain;

import java.io.Serializable;
import java.util.Objects;

public class TeamMemberId implements Serializable {
    private Long teamId;
    private Long userId;

    public TeamMemberId() {}

    public TeamMemberId(Long teamId, Long userId) {
        this.teamId = teamId;
        this.userId = userId;
    }

    public Long getTeamId() { return teamId; }
    public Long getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamMemberId that)) return false;
        return Objects.equals(teamId, that.teamId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() { return Objects.hash(teamId, userId); }
}
