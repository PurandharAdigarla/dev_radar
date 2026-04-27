package com.devradar.web.rest.dto;

import java.time.Instant;
import java.util.List;

public record PublicWeeklyRadarDTO(
        String title,
        String tagSlug,
        String tagDisplayName,
        int weekNumber,
        int year,
        Instant periodStart,
        Instant periodEnd,
        List<RadarThemeDTO> themes
) {}
