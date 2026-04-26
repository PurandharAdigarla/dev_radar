package com.devradar.web.rest.dto;

public record NotificationPreferenceDTO(boolean emailEnabled, String emailAddress, int digestDayOfWeek, int digestHourUtc) {}
