package com.devradar.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class GitHubApiClient {

    private final RestClient http;
    private final ObjectMapper json = new ObjectMapper();

    public GitHubApiClient(RestClient.Builder builder, @Value("${github.api.base-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public AuthedUser getAuthenticatedUser(String token) {
        JsonNode n = http.get().uri("/user")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve().body(JsonNode.class);
        return new AuthedUser(n.path("login").asText(), n.path("id").asLong());
    }

    public List<RepoInfo> listRepos(String token) {
        JsonNode arr = http.get()
                .uri(uri -> uri.path("/user/repos").queryParam("per_page", "100").build())
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve().body(JsonNode.class);
        List<RepoInfo> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode r : arr) {
                out.add(new RepoInfo(r.path("full_name").asText(), r.path("default_branch").asText("main")));
            }
        }
        return out;
    }

    public FileContent getFileContent(String token, String repoFullName, String path, String ref) {
        JsonNode n = http.get().uri(uri -> {
                    var b = uri.path("/repos/" + repoFullName + "/contents/" + path);
                    if (ref != null) b.queryParam("ref", ref);
                    return b.build();
                })
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve().body(JsonNode.class);
        String b64 = n.path("content").asText().replace("\n", "");
        String text = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        return new FileContent(text, n.path("sha").asText(), b64);
    }

    public void createBranch(String token, String repoFullName, String newBranch, String fromBranch) {
        JsonNode ref = http.get().uri("/repos/" + repoFullName + "/git/ref/heads/" + fromBranch)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve().body(JsonNode.class);
        String fromSha = ref.path("object").path("sha").asText();

        ObjectNode body = json.createObjectNode();
        body.put("ref", "refs/heads/" + newBranch);
        body.put("sha", fromSha);

        http.post().uri("/repos/" + repoFullName + "/git/refs")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve().toBodilessEntity();
    }

    public void putFileContent(String token, GitHubFileMutation mut) {
        ObjectNode body = json.createObjectNode();
        body.put("message", mut.commitMessage());
        body.put("content", mut.newContentBase64());
        body.put("sha", mut.fileSha());
        body.put("branch", mut.branchName());

        http.put().uri("/repos/" + mut.repoFullName() + "/contents/" + mut.filePath())
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve().toBodilessEntity();
    }

    public String createPullRequest(String token, String repoFullName, String title, String body,
                                    String head, String base) {
        ObjectNode req = json.createObjectNode();
        req.put("title", title);
        req.put("body", body);
        req.put("head", head);
        req.put("base", base);

        JsonNode resp = http.post().uri("/repos/" + repoFullName + "/pulls")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req.toString())
                .retrieve().body(JsonNode.class);

        return resp.path("html_url").asText();
    }

    public record AuthedUser(String login, long id) {}
    public record RepoInfo(String fullName, String defaultBranch) {}
    public record FileContent(String text, String sha, String base64) {}
}
