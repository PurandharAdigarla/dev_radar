package com.devradar.domain;

import java.io.Serializable;
import java.util.Objects;

public class UserInterestId implements Serializable {
    private Long userId;
    private Long interestTagId;

    public UserInterestId() {}
    public UserInterestId(Long userId, Long interestTagId) {
        this.userId = userId; this.interestTagId = interestTagId;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserInterestId other)) return false;
        return Objects.equals(userId, other.userId) && Objects.equals(interestTagId, other.interestTagId);
    }
    @Override public int hashCode() { return Objects.hash(userId, interestTagId); }
}
