package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "radar_repo_recommendations")
public class RadarRepoRecommendation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "radar_id", nullable = false)
    private Long radarId;

    @Column(nullable = false, length = 80)
    private String topic;

    @Column(name = "repo_url", nullable = false, length = 500)
    private String repoUrl;

    @Column(name = "repo_name", nullable = false, length = 200)
    private String repoName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "why_notable", nullable = false, columnDefinition = "TEXT")
    private String whyNotable;

    @Column(length = 50)
    private String category;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRadarId() { return radarId; }
    public void setRadarId(Long v) { this.radarId = v; }
    public String getTopic() { return topic; }
    public void setTopic(String v) { this.topic = v; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String v) { this.repoUrl = v; }
    public String getRepoName() { return repoName; }
    public void setRepoName(String v) { this.repoName = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getWhyNotable() { return whyNotable; }
    public void setWhyNotable(String v) { this.whyNotable = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
    public Instant getCreatedAt() { return createdAt; }
}
