package com.devradar.web.rest.dto;

import java.math.BigDecimal;

public record EvalScoreDTO(
        String category,
        BigDecimal score
) {}
