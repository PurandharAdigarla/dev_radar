package com.devradar.web.rest.dto;

import jakarta.validation.constraints.*;

public record LoginRequestDTO(
    @Email @NotBlank String email,
    @NotBlank String password
) {}
