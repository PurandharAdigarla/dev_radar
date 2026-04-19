package com.devradar.web.rest.dto;

import com.devradar.domain.RadarStatus;
import java.time.Instant;
import java.util.List;

public record RadarDetailDTO(Long id, RadarStatus status, Instant periodStart, Instant periodEnd, Instant generatedAt, Long generationMs, Integer tokenCount, List<RadarThemeDTO> themes) {}
