package com.devradar.web.rest.dto;

import java.util.List;

public record DependencySummaryDTO(int repoCount, int dependencyCount, int vulnerabilityCount, List<String> ecosystems) {}
