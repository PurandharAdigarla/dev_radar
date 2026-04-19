package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_interests")
@IdClass(UserInterestId.class)
public class UserInterest {
    @Id @Column(name = "user_id") private Long userId;
    @Id @Column(name = "interest_tag_id") private Long interestTagId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public UserInterest() {}
    public UserInterest(Long userId, Long interestTagId) {
        this.userId = userId; this.interestTagId = interestTagId;
    }
    public Long getUserId() { return userId; }
    public Long getInterestTagId() { return interestTagId; }
}
