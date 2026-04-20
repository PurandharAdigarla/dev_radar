package com.devradar.github;

public record GitHubFileMutation(
        String repoFullName,
        String filePath,
        String newContentBase64,
        String fileSha,
        String commitMessage,
        String branchName) {}
