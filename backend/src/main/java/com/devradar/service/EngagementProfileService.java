package com.devradar.service;

import com.devradar.domain.EngagementEvent;
import com.devradar.domain.EventType;
import com.devradar.domain.RadarTheme;
import com.devradar.repository.EngagementEventRepository;
import com.devradar.repository.RadarThemeRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EngagementProfileService {

    private final EngagementEventRepository engagementRepo;
    private final RadarThemeRepository themeRepo;

    public EngagementProfileService(EngagementEventRepository engagementRepo,
                                    RadarThemeRepository themeRepo) {
        this.engagementRepo = engagementRepo;
        this.themeRepo = themeRepo;
    }

    public UserEngagementProfile buildProfile(Long userId) {
        List<EngagementEvent> events = engagementRepo.findByUserIdOrderByCreatedAtDesc(userId);
        if (events.isEmpty()) {
            return new UserEngagementProfile(List.of(), List.of(), 0, Map.of());
        }

        List<String> thumbsUpThemes = new ArrayList<>();
        List<String> thumbsDownThemes = new ArrayList<>();
        Map<String, Long> eventTypeCounts = new LinkedHashMap<>();

        for (EngagementEvent event : events) {
            String typeName = event.getEventType().name();
            eventTypeCounts.merge(typeName, 1L, Long::sum);

            if (event.getEventType() == EventType.THUMBS_UP || event.getEventType() == EventType.THUMBS_DOWN) {
                String themeTitle = resolveThemeTitle(event.getRadarId(), event.getThemeIndex());
                if (themeTitle != null) {
                    if (event.getEventType() == EventType.THUMBS_UP) {
                        thumbsUpThemes.add(themeTitle);
                    } else {
                        thumbsDownThemes.add(themeTitle);
                    }
                }
            }
        }

        // Deduplicate theme lists
        List<String> uniqueUp = thumbsUpThemes.stream().distinct().collect(Collectors.toList());
        List<String> uniqueDown = thumbsDownThemes.stream().distinct().collect(Collectors.toList());

        // Sort event types by count descending
        Map<String, Long> topEventTypes = eventTypeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        return new UserEngagementProfile(uniqueUp, uniqueDown, events.size(), topEventTypes);
    }

    private String resolveThemeTitle(Long radarId, int themeIndex) {
        List<RadarTheme> themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(radarId);
        if (themeIndex >= 0 && themeIndex < themes.size()) {
            return themes.get(themeIndex).getTitle();
        }
        return null;
    }

    public record UserEngagementProfile(
            List<String> thumbsUpThemes,
            List<String> thumbsDownThemes,
            int totalInteractions,
            Map<String, Long> topEventTypes
    ) {}
}
