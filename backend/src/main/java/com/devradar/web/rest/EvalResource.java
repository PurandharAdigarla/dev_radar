package com.devradar.web.rest;

import com.devradar.eval.application.EvalApplicationService;
import com.devradar.web.rest.dto.EvalRunDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evals")
public class EvalResource {

    private final EvalApplicationService service;

    public EvalResource(EvalApplicationService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public ResponseEntity<EvalRunDTO> run(@RequestBody Map<String, Integer> body) {
        int radarCount = body.getOrDefault("radarCount", 10);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.triggerRun(radarCount));
    }

    @GetMapping("/runs")
    public ResponseEntity<List<EvalRunDTO>> runs() {
        return ResponseEntity.ok(service.listRuns());
    }
}
