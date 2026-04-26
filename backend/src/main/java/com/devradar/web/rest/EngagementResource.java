package com.devradar.web.rest;

import com.devradar.domain.EngagementEvent;
import com.devradar.domain.EventType;
import com.devradar.repository.EngagementEventRepository;
import com.devradar.security.SecurityUtils;
import com.devradar.service.EngagementProfileService;
import com.devradar.service.EngagementProfileService.UserEngagementProfile;
import com.devradar.web.rest.dto.EngagementEventDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engagement")
public class EngagementResource {

    private final EngagementEventRepository engagementRepo;
    private final EngagementProfileService profileService;

    public EngagementResource(EngagementEventRepository engagementRepo,
                              EngagementProfileService profileService) {
        this.engagementRepo = engagementRepo;
        this.profileService = profileService;
    }

    @PostMapping
    public ResponseEntity<Void> record(@RequestBody EngagementEventDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        EventType eventType;
        try {
            eventType = EventType.valueOf(dto.eventType());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        EngagementEvent event = new EngagementEvent();
        event.setUserId(userId);
        event.setRadarId(dto.radarId());
        event.setThemeIndex(dto.themeIndex());
        event.setEventType(eventType);
        engagementRepo.save(event);

        return ResponseEntity.status(201).build();
    }

    @GetMapping("/summary")
    public ResponseEntity<UserEngagementProfile> summary() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(profileService.buildProfile(userId));
    }
}
