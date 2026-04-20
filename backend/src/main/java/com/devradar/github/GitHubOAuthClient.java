package com.devradar.github;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class GitHubOAuthClient {

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String tokenUrl;

    public GitHubOAuthClient(
        RestClient.Builder builder,
        @Value("${github.oauth.client-id}") String clientId,
        @Value("${github.oauth.client-secret}") String clientSecret,
        @Value("${github.oauth.redirect-uri}") String redirectUri,
        @Value("${github.oauth.token-url}") String tokenUrl
    ) {
        this.http = builder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenUrl = tokenUrl;
    }

    public String buildAuthorizeUrl(String authorizeBaseUrl, String state, String scopes) {
        return UriComponentsBuilder.fromUriString(authorizeBaseUrl)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", scopes)
            .queryParam("state", state)
            .build(true).toUriString();
    }

    public AccessTokenResponse exchangeCode(String code) {
        String body = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
            + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
            + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        JsonNode resp = http.post()
            .uri(tokenUrl)
            .header("Accept", "application/json")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

        if (resp == null || !resp.has("access_token")) {
            throw new RuntimeException("github token response missing access_token");
        }
        return new AccessTokenResponse(
            resp.get("access_token").asText(),
            resp.path("scope").asText("")
        );
    }

    public static String generateState() { return UUID.randomUUID().toString(); }

    public record AccessTokenResponse(String accessToken, String grantedScopes) {}
}
