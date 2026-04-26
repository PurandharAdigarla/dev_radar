package com.devradar.onboarding;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.repository.UserGithubIdentityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GitHubRepoScanService {

    private static final Map<String, List<String>> LANGUAGE_TO_SLUGS = Map.ofEntries(
        Map.entry("java", List.of("java", "spring_boot")),
        Map.entry("javascript", List.of("javascript", "react", "frontend")),
        Map.entry("typescript", List.of("typescript", "react", "frontend")),
        Map.entry("python", List.of("python", "django", "fastapi")),
        Map.entry("go", List.of("go")),
        Map.entry("rust", List.of("rust")),
        Map.entry("ruby", List.of("rails")),
        Map.entry("kotlin", List.of("kotlin")),
        Map.entry("swift", List.of("swift")),
        Map.entry("c#", List.of("csharp")),
        Map.entry("c++", List.of("cpp")),
        Map.entry("php", List.of("php"))
    );

    private static final Map<String, String> TOPIC_TO_SLUG = Map.ofEntries(
        Map.entry("react", "react"),
        Map.entry("nextjs", "next_js"),
        Map.entry("next-js", "next_js"),
        Map.entry("vue", "vue"),
        Map.entry("angular", "angular"),
        Map.entry("svelte", "svelte"),
        Map.entry("spring", "spring_boot"),
        Map.entry("spring-boot", "spring_boot"),
        Map.entry("django", "django"),
        Map.entry("fastapi", "fastapi"),
        Map.entry("docker", "docker"),
        Map.entry("kubernetes", "kubernetes"),
        Map.entry("terraform", "terraform"),
        Map.entry("redis", "redis"),
        Map.entry("tailwindcss", "frontend"),
        Map.entry("machine-learning", "ai_tooling"),
        Map.entry("llm", "llm")
    );

    private final UserGithubIdentityRepository identityRepo;
    private final TokenEncryptor encryptor;
    private final RestClient http;

    public GitHubRepoScanService(UserGithubIdentityRepository identityRepo,
                                  TokenEncryptor encryptor,
                                  RestClient.Builder builder,
                                  @Value("${github.api.base-url}") String baseUrl) {
        this.identityRepo = identityRepo;
        this.encryptor = encryptor;
        this.http = builder.baseUrl(baseUrl).build();
    }

    public ScanResult scanUserRepos(Long userId) {
        UserGithubIdentity identity = identityRepo.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "GitHub account not linked. Please connect your GitHub account first."));

        String token = encryptor.decrypt(identity.getAccessTokenEncrypted());

        JsonNode arr = http.get()
            .uri(uri -> uri.path("/user/repos")
                .queryParam("sort", "pushed")
                .queryParam("per_page", "30")
                .queryParam("type", "owner")
                .build())
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .retrieve().body(JsonNode.class);

        Set<String> detectedSlugs = new LinkedHashSet<>();
        List<RepoInfo> repos = new ArrayList<>();

        if (arr != null && arr.isArray()) {
            for (JsonNode r : arr) {
                String name = r.path("full_name").asText();
                String language = r.path("language").asText(null);
                List<String> topics = new ArrayList<>();
                if (r.has("topics") && r.get("topics").isArray()) {
                    for (JsonNode t : r.get("topics")) {
                        topics.add(t.asText());
                    }
                }

                if (language != null) {
                    List<String> slugs = LANGUAGE_TO_SLUGS.get(language.toLowerCase());
                    if (slugs != null) detectedSlugs.addAll(slugs);
                }

                for (String topic : topics) {
                    String slug = TOPIC_TO_SLUG.get(topic.toLowerCase());
                    if (slug != null) detectedSlugs.add(slug);
                }

                repos.add(new RepoInfo(name, language, topics));
            }
        }

        return new ScanResult(new ArrayList<>(detectedSlugs), repos.size(), repos.stream().limit(10).toList());
    }

    public record ScanResult(List<String> detectedInterests, int repoCount, List<RepoInfo> topRepos) {}
    public record RepoInfo(String name, String language, List<String> topics) {}
}
