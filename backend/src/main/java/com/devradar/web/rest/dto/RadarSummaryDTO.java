package com.devradar.web.rest.dto;

import com.devradar.domain.RadarStatus;
import java.time.Instant;

public record RadarSummaryDTO(Long id, RadarStatus status, Instant periodStart, Instant periodEnd, Instant generatedAt, Long generationMs, Integer tokenCount, Integer themeCount) {}
