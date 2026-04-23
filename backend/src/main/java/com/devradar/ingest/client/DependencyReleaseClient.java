package com.devradar.ingest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class DependencyReleaseClient {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyReleaseClient.class);

    private final RestClient mavenHttp;
    private final RestClient npmHttp;

    public DependencyReleaseClient(
        RestClient.Builder builder,
        @Value("${devradar.ingest.dep-release.maven-url:https://search.maven.org}") String mavenUrl,
        @Value("${devradar.ingest.dep-release.npm-url:https://registry.npmjs.org}") String npmUrl
    ) {
        this.mavenHttp = builder.clone().baseUrl(mavenUrl).build();
        this.npmHttp = builder.clone().baseUrl(npmUrl).build();
    }

    public Optional<FetchedItem> checkForNewerVersion(String ecosystem, String packageName, String currentVersion) {
        return switch (ecosystem) {
            case "MAVEN", "GRADLE" -> checkMaven(packageName, currentVersion, ecosystem);
            case "NPM" -> checkNpm(packageName, currentVersion);
            default -> Optional.empty();
        };
    }

    private Optional<FetchedItem> checkMaven(String packageName, String currentVersion, String ecosystem) {
        String[] parts = packageName.split(":");
        if (parts.length != 2) return Optional.empty();

        try {
            JsonNode body = mavenHttp.get()
                .uri(uri -> uri.path("/solrsearch/select")
                    .queryParam("q", "g:\"" + parts[0] + "\" AND a:\"" + parts[1] + "\"")
                    .queryParam("rows", "1")
                    .queryParam("wt", "json")
                    .build())
                .retrieve().body(JsonNode.class);

            JsonNode docs = body.path("response").path("docs");
            if (!docs.isArray() || docs.isEmpty()) return Optional.empty();

            String latestVersion = docs.get(0).path("latestVersion").asText(null);
            if (latestVersion == null || latestVersion.equals(currentVersion)) return Optional.empty();

            String artifactId = parts[1];
            String title = artifactId + " " + latestVersion + " released";
            String url = "https://central.sonatype.com/artifact/" + parts[0] + "/" + artifactId + "/" + latestVersion;
            String externalId = ecosystem + ":" + packageName + ":" + latestVersion;

            return Optional.of(new FetchedItem(
                externalId, url, title, null, null, Instant.now(), null, List.of()
            ));
        } catch (Exception e) {
            LOG.debug("maven check failed pkg={}: {}", packageName, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<FetchedItem> checkNpm(String packageName, String currentVersion) {
        try {
            JsonNode body = npmHttp.get()
                .uri("/" + packageName + "/latest")
                .retrieve().body(JsonNode.class);

            String latestVersion = body.path("version").asText(null);
            if (latestVersion == null || latestVersion.equals(currentVersion)) return Optional.empty();

            String description = body.path("description").asText(null);
            String title = packageName + " " + latestVersion + " released";
            String url = "https://www.npmjs.com/package/" + packageName + "/v/" + latestVersion;
            String externalId = "NPM:" + packageName + ":" + latestVersion;

            return Optional.of(new FetchedItem(
                externalId, url, title, description, null, Instant.now(), null, List.of()
            ));
        } catch (Exception e) {
            LOG.debug("npm check failed pkg={}: {}", packageName, e.getMessage());
            return Optional.empty();
        }
    }
}
