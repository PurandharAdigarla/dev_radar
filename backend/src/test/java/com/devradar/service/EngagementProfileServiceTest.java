package com.devradar.service;

import com.devradar.domain.EngagementEvent;
import com.devradar.domain.EventType;
import com.devradar.domain.RadarTheme;
import com.devradar.repository.EngagementEventRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.service.EngagementProfileService.UserEngagementProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EngagementProfileServiceTest {

    EngagementEventRepository engagementRepo;
    RadarThemeRepository themeRepo;
    EngagementProfileService service;

    @BeforeEach
    void setup() {
        engagementRepo = mock(EngagementEventRepository.class);
        themeRepo = mock(RadarThemeRepository.class);
        service = new EngagementProfileService(engagementRepo, themeRepo);
    }

    @Test
    void emptyProfileWhenNoEvents() {
        when(engagementRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        UserEngagementProfile profile = service.buildProfile(1L);

        assertThat(profile.totalInteractions()).isZero();
        assertThat(profile.thumbsUpThemes()).isEmpty();
        assertThat(profile.thumbsDownThemes()).isEmpty();
        assertThat(profile.topEventTypes()).isEmpty();
    }

    @Test
    void profileAggregatesThumbsUpAndDown() {
        RadarTheme theme0 = new RadarTheme();
        theme0.setTitle("Spring Boot 3.5");
        theme0.setDisplayOrder(0);

        RadarTheme theme1 = new RadarTheme();
        theme1.setTitle("Kubernetes Security");
        theme1.setDisplayOrder(1);

        when(themeRepo.findByRadarIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(theme0, theme1));

        EngagementEvent upEvent = makeEvent(1L, 10L, 0, EventType.THUMBS_UP);
        EngagementEvent downEvent = makeEvent(1L, 10L, 1, EventType.THUMBS_DOWN);
        EngagementEvent shareEvent = makeEvent(1L, 10L, 0, EventType.SHARE);

        when(engagementRepo.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(upEvent, downEvent, shareEvent));

        UserEngagementProfile profile = service.buildProfile(1L);

        assertThat(profile.totalInteractions()).isEqualTo(3);
        assertThat(profile.thumbsUpThemes()).containsExactly("Spring Boot 3.5");
        assertThat(profile.thumbsDownThemes()).containsExactly("Kubernetes Security");
        assertThat(profile.topEventTypes()).containsKeys("THUMBS_UP", "THUMBS_DOWN", "SHARE");
    }

    @Test
    void profileDeduplicatesThemeTitles() {
        RadarTheme theme0 = new RadarTheme();
        theme0.setTitle("React 19");
        theme0.setDisplayOrder(0);

        when(themeRepo.findByRadarIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(theme0));

        EngagementEvent up1 = makeEvent(1L, 10L, 0, EventType.THUMBS_UP);
        EngagementEvent up2 = makeEvent(1L, 10L, 0, EventType.THUMBS_UP);

        when(engagementRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(up1, up2));

        UserEngagementProfile profile = service.buildProfile(1L);

        assertThat(profile.thumbsUpThemes()).containsExactly("React 19");
        assertThat(profile.totalInteractions()).isEqualTo(2);
    }

    @Test
    void outOfBoundsThemeIndexIsIgnored() {
        RadarTheme theme0 = new RadarTheme();
        theme0.setTitle("Valid Theme");
        theme0.setDisplayOrder(0);

        when(themeRepo.findByRadarIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(theme0));

        EngagementEvent outOfBounds = makeEvent(1L, 10L, 5, EventType.THUMBS_UP);

        when(engagementRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(outOfBounds));

        UserEngagementProfile profile = service.buildProfile(1L);

        assertThat(profile.thumbsUpThemes()).isEmpty();
        assertThat(profile.totalInteractions()).isEqualTo(1);
    }

    private EngagementEvent makeEvent(Long userId, Long radarId, int themeIndex, EventType type) {
        EngagementEvent e = new EngagementEvent();
        e.setUserId(userId);
        e.setRadarId(radarId);
        e.setThemeIndex(themeIndex);
        e.setEventType(type);
        return e;
    }
}
