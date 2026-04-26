package com.devradar.web.rest;

import com.devradar.radar.application.RadarApplicationService;
import com.devradar.web.rest.dto.DependencySummaryDTO;
import com.devradar.web.rest.dto.UserStatsDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
public class UserDashboardResource {

    private final RadarApplicationService app;

    public UserDashboardResource(RadarApplicationService app) {
        this.app = app;
    }

    @GetMapping("/stats")
    public UserStatsDTO getStats() {
        return app.getUserStats();
    }

    @GetMapping("/dependency-summary")
    public DependencySummaryDTO getDependencySummary() {
        return app.getDependencySummary();
    }

    @GetMapping("/suggested-interests")
    public List<String> getSuggestedInterests() {
        return app.getSuggestedInterests();
    }
}
