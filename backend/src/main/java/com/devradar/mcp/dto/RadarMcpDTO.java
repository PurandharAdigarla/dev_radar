package com.devradar.mcp.dto;

import java.time.Instant;
import java.util.List;

public record RadarMcpDTO(
    Long radarId,
    Instant generatedAt,
    Instant periodStart,
    Instant periodEnd,
    List<ThemeMcpDTO> themes
) {}
