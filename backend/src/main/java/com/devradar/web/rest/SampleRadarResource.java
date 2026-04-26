package com.devradar.web.rest;

import com.devradar.radar.application.RadarApplicationService;
import com.devradar.web.rest.dto.RadarDetailDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sample-radar")
public class SampleRadarResource {

    private final RadarApplicationService app;

    public SampleRadarResource(RadarApplicationService app) {
        this.app = app;
    }

    @GetMapping
    public ResponseEntity<RadarDetailDTO> getSampleRadar() {
        return app.getLatestPublicRadar()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
