package com.devradar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "radar_theme_items")
public class RadarThemeItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theme_id", nullable = false)
    private Long themeId;

    @Column(name = "source_item_id", nullable = false)
    private Long sourceItemId;

    @Column(name = "ai_commentary", length = 1000)
    private String aiCommentary;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public Long getId() { return id; }
    public Long getThemeId() { return themeId; }
    public void setThemeId(Long v) { this.themeId = v; }
    public Long getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(Long v) { this.sourceItemId = v; }
    public String getAiCommentary() { return aiCommentary; }
    public void setAiCommentary(String v) { this.aiCommentary = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
}
