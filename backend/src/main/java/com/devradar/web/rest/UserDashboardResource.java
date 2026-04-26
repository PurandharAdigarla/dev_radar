package com.devradar.web.rest;

import com.devradar.radar.application.RadarStatsService;
import com.devradar.web.rest.dto.DependencySummaryDTO;
import com.devradar.web.rest.dto.UserStatsDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
public class UserDashboardResource {

    private final RadarStatsService statsService;

    public UserDashboardResource(RadarStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public UserStatsDTO getStats() {
        return statsService.getUserStats();
    }

    @GetMapping("/dependency-summary")
    public DependencySummaryDTO getDependencySummary() {
        return statsService.getDependencySummary();
    }

    @GetMapping("/suggested-interests")
    public List<String> getSuggestedInterests() {
        return statsService.getSuggestedInterests();
    }
}
