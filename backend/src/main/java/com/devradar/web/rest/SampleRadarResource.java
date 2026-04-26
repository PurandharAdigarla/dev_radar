package com.devradar.web.rest;

import com.devradar.radar.application.RadarSharingService;
import com.devradar.web.rest.dto.RadarDetailDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sample-radar")
public class SampleRadarResource {

    private final RadarSharingService sharing;

    public SampleRadarResource(RadarSharingService sharing) {
        this.sharing = sharing;
    }

    @GetMapping
    public ResponseEntity<RadarDetailDTO> getSampleRadar() {
        return sharing.getLatestPublicRadar()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
