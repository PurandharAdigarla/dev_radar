package com.devradar.web.rest.dto;

public record UserStatsDTO(int radarCount, int themeCount, int engagementCount, String latestRadarDate, int newItemsSinceLastRadar) {}
