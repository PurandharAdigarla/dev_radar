package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "radar_theme_sources")
public class RadarThemeSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theme_id", nullable = false)
    private Long themeId;

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(length = 500)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getThemeId() { return themeId; }
    public void setThemeId(Long v) { this.themeId = v; }
    public String getUrl() { return url; }
    public void setUrl(String v) { this.url = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public Instant getCreatedAt() { return createdAt; }
}
