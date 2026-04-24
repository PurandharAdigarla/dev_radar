package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "oauth_state")
public class OAuthState {

    @Id
    @Column(name = "state", length = 64)
    private String state;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "link_user_id")
    private Long linkUserId;

    protected OAuthState() {}

    public OAuthState(String state, Instant expiresAt, Long linkUserId) {
        this.state = state;
        this.expiresAt = expiresAt;
        this.linkUserId = linkUserId;
    }

    public String getState() { return state; }
    public Instant getExpiresAt() { return expiresAt; }
    public Long getLinkUserId() { return linkUserId; }
}
