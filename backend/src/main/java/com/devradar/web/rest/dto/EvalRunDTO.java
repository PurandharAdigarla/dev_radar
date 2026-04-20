package com.devradar.web.rest.dto;

import java.time.Instant;
import java.util.List;

public record EvalRunDTO(
        Long id,
        String status,
        int radarCount,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        List<EvalScoreDTO> scores
) {}
