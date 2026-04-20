package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_github_identity")
public class UserGithubIdentity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "github_user_id", nullable = false, unique = true)
    private Long githubUserId;

    @Column(name = "github_login", nullable = false, length = 120)
    private String githubLogin;

    @Column(name = "access_token_encrypted", nullable = false, length = 2048)
    private String accessTokenEncrypted;

    @Column(name = "granted_scopes", length = 255)
    private String grantedScopes;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @PrePersist
    void onCreate() { linkedAt = Instant.now(); }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getGithubUserId() { return githubUserId; }
    public void setGithubUserId(Long v) { this.githubUserId = v; }
    public String getGithubLogin() { return githubLogin; }
    public void setGithubLogin(String v) { this.githubLogin = v; }
    public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
    public void setAccessTokenEncrypted(String v) { this.accessTokenEncrypted = v; }
    public String getGrantedScopes() { return grantedScopes; }
    public void setGrantedScopes(String v) { this.grantedScopes = v; }
    public Instant getLinkedAt() { return linkedAt; }
}
