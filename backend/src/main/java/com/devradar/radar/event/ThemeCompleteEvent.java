package com.devradar.radar.event;
import java.util.List;
public record ThemeCompleteEvent(Long radarId, Long themeId, String title, String summary, List<Long> itemIds, int displayOrder) {}
