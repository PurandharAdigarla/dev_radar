package com.devradar.web.rest.dto;

public record RepoRecommendationDTO(
    String repoUrl,
    String repoName,
    String description,
    String whyNotable,
    String category,
    String topic
) {}
