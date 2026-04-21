package com.devradar.web.rest;

import com.devradar.apikey.application.ApiKeyApplicationService;
import com.devradar.web.rest.dto.ApiKeyCreateRequest;
import com.devradar.web.rest.dto.ApiKeyCreateResponse;
import com.devradar.web.rest.dto.ApiKeySummaryDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/api-keys")
public class ApiKeyResource {

    private final ApiKeyApplicationService service;

    public ApiKeyResource(ApiKeyApplicationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiKeyCreateResponse> create(@Valid @RequestBody ApiKeyCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @GetMapping
    public ResponseEntity<List<ApiKeySummaryDTO>> list() {
        return ResponseEntity.ok(service.list());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable("id") Long id) {
        service.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
