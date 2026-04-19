package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sources")
public class Source {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean active = true;

    @Column(name = "fetch_interval_minutes", nullable = false)
    private int fetchIntervalMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getFetchIntervalMinutes() { return fetchIntervalMinutes; }
    public void setFetchIntervalMinutes(int fetchIntervalMinutes) { this.fetchIntervalMinutes = fetchIntervalMinutes; }
}
