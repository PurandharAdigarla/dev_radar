package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "engagement_events")
public class EngagementEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "radar_id", nullable = false)
    private Long radarId;

    @Column(name = "theme_index", nullable = false)
    private int themeIndex;

    @Column(name = "theme_id")
    private Long themeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRadarId() { return radarId; }
    public void setRadarId(Long radarId) { this.radarId = radarId; }
    public int getThemeIndex() { return themeIndex; }
    public void setThemeIndex(int themeIndex) { this.themeIndex = themeIndex; }
    public Long getThemeId() { return themeId; }
    public void setThemeId(Long themeId) { this.themeId = themeId; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public Instant getCreatedAt() { return createdAt; }
}
