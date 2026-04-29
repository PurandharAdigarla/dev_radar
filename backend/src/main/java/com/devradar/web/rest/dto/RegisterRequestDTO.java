package com.devradar.web.rest.dto;

import jakarta.validation.constraints.*;

public record RegisterRequestDTO(
    @Email @NotBlank @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "must not contain HTML tags") String displayName
) {}
