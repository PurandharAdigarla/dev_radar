package com.devradar.web.rest.dto;

import jakarta.validation.constraints.*;

public record UserUpdateDTO(@NotBlank @Size(max = 100) String displayName) {}
