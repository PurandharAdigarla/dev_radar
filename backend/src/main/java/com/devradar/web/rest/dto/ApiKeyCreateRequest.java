package com.devradar.web.rest.dto;

import com.devradar.domain.ApiKeyScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApiKeyCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull ApiKeyScope scope
) {}
