package com.devradar.web.rest.dto;

import com.devradar.domain.InterestCategory;

public record InterestTagResponseDTO(Long id, String slug, String displayName, InterestCategory category) {}
