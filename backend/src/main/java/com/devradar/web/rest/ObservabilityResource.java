package com.devradar.web.rest;

import com.devradar.observability.application.ObservabilityApplicationService;
import com.devradar.web.rest.dto.MetricsDayDTO;
import com.devradar.web.rest.dto.ObservabilitySummaryDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityResource {

    private final ObservabilityApplicationService service;

    public ObservabilityResource(ObservabilityApplicationService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ResponseEntity<ObservabilitySummaryDTO> summary() {
        return ResponseEntity.ok(service.getSummary());
    }

    @GetMapping("/timeseries")
    public ResponseEntity<List<MetricsDayDTO>> timeseries(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(service.getTimeseries(Math.min(days, 90)));
    }
}
