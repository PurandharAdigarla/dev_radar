package com.devradar.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubApiClientTest {

    WireMockServer wm;
    GitHubApiClient client;
    ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new GitHubApiClient(RestClient.builder(), "http://localhost:" + wm.port());
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void getAuthenticatedUser_returnsLoginAndId() {
        wm.stubFor(WireMock.get("/user").willReturn(WireMock.okJson("""
            {"login":"alice","id":12345}
            """)));

        GitHubApiClient.AuthedUser u = client.getAuthenticatedUser("token");
        assertThat(u.login()).isEqualTo("alice");
        assertThat(u.id()).isEqualTo(12345L);
    }

    @Test
    void listRepos_returnsRepoFullNames() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/user/repos")).willReturn(WireMock.okJson("""
            [
              {"full_name":"alice/repo1","default_branch":"main"},
              {"full_name":"alice/repo2","default_branch":"master"}
            ]
            """)));

        List<GitHubApiClient.RepoInfo> repos = client.listRepos("token");
        assertThat(repos).hasSize(2);
        assertThat(repos.get(0).fullName()).isEqualTo("alice/repo1");
        assertThat(repos.get(0).defaultBranch()).isEqualTo("main");
    }

    @Test
    void getFileContent_returnsDecodedTextAndSha() throws Exception {
        String content = "<project>...</project>";
        String b64 = Base64.getEncoder().encodeToString(content.getBytes());
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/alice/repo1/contents/pom.xml")).willReturn(WireMock.okJson("""
            {"content":"%s","sha":"abc123sha","encoding":"base64"}
            """.formatted(b64))));

        GitHubApiClient.FileContent c = client.getFileContent("token", "alice/repo1", "pom.xml", null);
        assertThat(c.text()).isEqualTo(content);
        assertThat(c.sha()).isEqualTo("abc123sha");
    }

    @Test
    void createBranch_callsRefsEndpoint() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/alice/repo1/git/ref/heads/main"))
            .willReturn(WireMock.okJson("{\"object\":{\"sha\":\"main-sha\"}}")));
        wm.stubFor(WireMock.post(WireMock.urlPathEqualTo("/repos/alice/repo1/git/refs"))
            .willReturn(WireMock.aResponse().withStatus(201).withBody("{}")));

        client.createBranch("token", "alice/repo1", "dev-radar/cve-fix", "main");

        wm.verify(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/repos/alice/repo1/git/refs"))
            .withRequestBody(WireMock.containing("dev-radar/cve-fix"))
            .withRequestBody(WireMock.containing("main-sha")));
    }

    @Test
    void putFileContent_sendsBase64Body() {
        wm.stubFor(WireMock.put(WireMock.urlPathEqualTo("/repos/alice/repo1/contents/pom.xml"))
            .willReturn(WireMock.okJson("{}")));

        GitHubFileMutation mut = new GitHubFileMutation("alice/repo1", "pom.xml",
            Base64.getEncoder().encodeToString("new content".getBytes()),
            "old-sha", "fix vulnerability", "dev-radar/cve-fix");

        client.putFileContent("token", mut);

        wm.verify(WireMock.putRequestedFor(WireMock.urlPathEqualTo("/repos/alice/repo1/contents/pom.xml"))
            .withRequestBody(WireMock.containing("old-sha"))
            .withRequestBody(WireMock.containing("dev-radar/cve-fix")));
    }

    @Test
    void createPullRequest_returnsHtmlUrl() {
        wm.stubFor(WireMock.post(WireMock.urlPathEqualTo("/repos/alice/repo1/pulls"))
            .willReturn(WireMock.okJson("""
                {"html_url":"https://github.com/alice/repo1/pull/42","number":42}
                """)));

        String url = client.createPullRequest("token", "alice/repo1",
            "chore(security): bump", "fixes GHSA-xxxx", "dev-radar/cve-fix", "main");

        assertThat(url).isEqualTo("https://github.com/alice/repo1/pull/42");
    }

    @Test
    void listStarred_returnsRepoFullNames() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/user/starred"))
            .withQueryParam("per_page", WireMock.equalTo("100"))
            .withQueryParam("sort", WireMock.equalTo("updated"))
            .withQueryParam("direction", WireMock.equalTo("desc"))
            .willReturn(WireMock.okJson("""
                [
                  {"full_name":"alice/react-app"},
                  {"full_name":"bob/spring-starter"}
                ]
                """)));

        List<String> starred = client.listStarred("my-token");

        assertThat(starred).containsExactly("alice/react-app", "bob/spring-starter");
        wm.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/user/starred"))
            .withHeader("Authorization", WireMock.equalTo("Bearer my-token")));
    }

    @Test
    void listStarred_returnsEmptyListWhenNoStars() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/user/starred"))
            .willReturn(WireMock.okJson("[]")));

        List<String> starred = client.listStarred("my-token");

        assertThat(starred).isEmpty();
    }
}
