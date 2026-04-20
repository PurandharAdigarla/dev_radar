package com.devradar.github;

public record GitHubAffectedFile(
        String repoFullName,
        String filePath,
        String currentVersion,
        String fileSha) {}
