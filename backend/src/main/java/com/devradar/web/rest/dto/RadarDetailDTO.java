package com.devradar.web.rest.dto;

import com.devradar.domain.RadarStatus;
import java.time.Instant;
import java.util.List;

public record RadarDetailDTO(Long id, RadarStatus status, Instant periodStart, Instant periodEnd,
                             Instant generatedAt, Long generationMs, Integer tokenCount,
                             String errorCode, String errorMessage,
                             List<RadarThemeDTO> themes, List<WebSourceDTO> webSources,
                             List<RepoRecommendationDTO> repos) {

    public RadarDetailDTO(Long id, RadarStatus status, Instant periodStart, Instant periodEnd,
                          Instant generatedAt, Long generationMs, Integer tokenCount,
                          String errorCode, String errorMessage, List<RadarThemeDTO> themes) {
        this(id, status, periodStart, periodEnd, generatedAt, generationMs, tokenCount,
             errorCode, errorMessage, themes, List.of(), List.of());
    }

    public RadarDetailDTO(Long id, RadarStatus status, Instant periodStart, Instant periodEnd,
                          Instant generatedAt, Long generationMs, Integer tokenCount,
                          String errorCode, String errorMessage,
                          List<RadarThemeDTO> themes, List<WebSourceDTO> webSources) {
        this(id, status, periodStart, periodEnd, generatedAt, generationMs, tokenCount,
             errorCode, errorMessage, themes, webSources, List.of());
    }
}
