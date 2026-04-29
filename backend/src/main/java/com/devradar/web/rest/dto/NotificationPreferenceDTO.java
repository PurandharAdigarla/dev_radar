package com.devradar.web.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record NotificationPreferenceDTO(
    boolean emailEnabled,
    @Email String emailAddress,
    @Min(1) @Max(7) int digestDayOfWeek,
    @Min(0) @Max(23) int digestHourUtc
) {}
