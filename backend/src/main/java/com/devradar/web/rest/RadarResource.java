package com.devradar.web.rest;

import com.devradar.radar.application.RadarApplicationService;
import com.devradar.web.rest.dto.RadarDetailDTO;
import com.devradar.web.rest.dto.RadarSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/radars")
public class RadarResource {

    private final RadarApplicationService app;
    public RadarResource(RadarApplicationService app) { this.app = app; }

    @PostMapping
    public ResponseEntity<RadarSummaryDTO> create() {
        RadarSummaryDTO created = app.createForCurrentUser();
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/{id}")
    public RadarDetailDTO get(@PathVariable Long id) {
        return app.get(id);
    }

    @GetMapping
    public Page<RadarSummaryDTO> list(Pageable pageable) {
        return app.list(pageable);
    }
}
