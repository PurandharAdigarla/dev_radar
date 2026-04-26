package com.devradar.web.rest;

import com.devradar.radar.application.RadarApplicationService;
import com.devradar.web.rest.dto.RadarDetailDTO;
import com.devradar.web.rest.dto.RadarSummaryDTO;
import com.devradar.web.rest.dto.ShareRadarResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
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

    @PostMapping("/{id}/share")
    public ShareRadarResponseDTO share(@PathVariable Long id, HttpServletRequest request) {
        String baseUrl = request.getScheme() + "://" + request.getServerName();
        int port = request.getServerPort();
        if ((request.getScheme().equals("http") && port != 80) || (request.getScheme().equals("https") && port != 443)) {
            baseUrl += ":" + port;
        }
        return app.shareRadar(id, baseUrl);
    }

    @GetMapping("/shared/{shareToken}")
    public ResponseEntity<RadarDetailDTO> getShared(@PathVariable String shareToken) {
        return app.getByShareToken(shareToken)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
