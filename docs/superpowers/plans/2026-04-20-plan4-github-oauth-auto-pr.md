# Dev Radar — Plan 4: GitHub OAuth + Auto-PR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users link their GitHub account (OAuth2 with `repo` scope), then have the radar agent scan their actual repos for CVE-affected dependencies. When the agent finds one it emits an `action_proposal`; the user reviews the proposed diff in the UI and one-clicks "Open PR" — a real PR with the version bump appears in their GitHub repo.

**Architecture:** A `com.devradar.github` module wraps the GitHub REST API (OAuth code-exchange, user info, repo contents, PR creation). A new `com.devradar.action` module persists `action_proposals` and executes them when approved. The agent gets a 4th tool, `checkRepoForVulnerability`, that scans the user's repo files for affected dependency versions; on hits it records a proposal as a side effect. Tool dispatch gets a `ToolContext` carrying the `userId` so per-user calls work without ThreadLocal hacks. Access tokens are AES-GCM encrypted at rest.

**Tech Stack:** Spring `RestClient` (already in use), AES-GCM via `javax.crypto`, WireMock for GitHub API stubs, JUnit 5 + Testcontainers, Mockito.

---

## File Structure

```
backend/
├── pom.xml                                          (no changes — RestClient + WireMock already present)
├── src/main/
│   ├── java/com/devradar/
│   │   ├── config/
│   │   │   └── EncryptionConfig.java               (AES-GCM key + Cipher provider bean)
│   │   ├── crypto/
│   │   │   └── TokenEncryptor.java                 (encrypt/decrypt strings via AES-GCM)
│   │   ├── domain/
│   │   │   ├── UserGithubIdentity.java
│   │   │   ├── ActionProposal.java
│   │   │   ├── ActionProposalKind.java             (enum)
│   │   │   └── ActionProposalStatus.java           (enum)
│   │   ├── repository/
│   │   │   ├── UserGithubIdentityRepository.java
│   │   │   └── ActionProposalRepository.java
│   │   ├── github/
│   │   │   ├── GitHubOAuthClient.java              (code → access_token)
│   │   │   ├── GitHubApiClient.java                (authenticated user, repos, file contents, PR creation)
│   │   │   ├── GitHubFileMutation.java             (DTO: path, oldContent, newContent, sha)
│   │   │   └── GitHubAffectedFile.java             (DTO: repo, file_path, current_version)
│   │   ├── ai/tools/
│   │   │   ├── ToolContext.java                    (record: userId, radarId)
│   │   │   ├── CheckRepoForVulnerabilityTool.java  (new tool — scans user repos)
│   │   │   ├── ToolRegistry.java                   (modified: dispatch(name, input, ctx))
│   │   │   ├── SearchItemsTool.java                (no change — ignores ctx)
│   │   │   ├── ScoreRelevanceTool.java             (no change)
│   │   │   └── GetItemDetailTool.java              (no change)
│   │   ├── ai/
│   │   │   └── RadarOrchestrator.java              (modified: thread ToolContext through dispatches)
│   │   ├── action/
│   │   │   ├── ActionProposalService.java          (persist, list, approve, dismiss)
│   │   │   ├── AutoPrExecutor.java                 (uses GitHubApiClient to create branch + PR)
│   │   │   └── application/
│   │   │       └── ActionApplicationService.java
│   │   ├── radar/
│   │   │   └── RadarGenerationService.java         (modified: pass ToolContext, fire action.proposed events)
│   │   ├── radar/event/
│   │   │   └── ActionProposedEvent.java            (new SSE event)
│   │   └── web/rest/
│   │       ├── AuthResource.java                   (modified: + /api/auth/github/start, /api/auth/github/callback)
│   │       ├── UserResource.java                   (modified: + DELETE /api/users/me/github-link)
│   │       ├── ActionResource.java                 (new: list/approve/dismiss proposals)
│   │       └── dto/
│   │           ├── ActionProposalDTO.java
│   │           └── GitHubLinkResponseDTO.java
│   └── resources/
│       └── db/changelog/
│           ├── db.changelog-master.xml             (modify: + 008)
│           └── 008-github-and-actions-schema.xml
└── src/test/
    └── java/com/devradar/
        ├── crypto/
        │   └── TokenEncryptorTest.java
        ├── github/
        │   ├── GitHubOAuthClientTest.java          (WireMock)
        │   └── GitHubApiClientTest.java            (WireMock — covers user, repos, content, PR creation)
        ├── ai/tools/
        │   └── CheckRepoForVulnerabilityToolTest.java (mocked GitHubApiClient + real DB)
        ├── action/
        │   └── AutoPrExecutorTest.java             (WireMock GitHub)
        └── web/rest/
            ├── AuthGithubResourceIT.java           (full OAuth callback flow with WireMock GitHub)
            └── ActionResourceIT.java               (full propose-then-approve flow with WireMock GitHub)
```

---

## Task 1: TokenEncryptor + EncryptionConfig (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/config/EncryptionConfig.java`
- Create: `backend/src/main/java/com/devradar/crypto/TokenEncryptor.java`
- Create: `backend/src/test/java/com/devradar/crypto/TokenEncryptorTest.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`

- [ ] **Step 1: Add encryption-key config**

Append to `backend/src/main/resources/application.yml`:

```yaml
encryption:
  github-token-key-base64: ${GITHUB_TOKEN_ENCRYPTION_KEY:UEjGRJjRBYGZQRdsB7Cln1mLG0qlxPEAU+Vq/Sx0iYE=}
```

(The default value is a dev-only AES-256 key in base64; production sets `GITHUB_TOKEN_ENCRYPTION_KEY` env var to a fresh 32-byte base64-encoded key. Generate one with `openssl rand -base64 32`.)

Append to `backend/src/test/resources/application-test.yml`:

```yaml
encryption:
  github-token-key-base64: UEjGRJjRBYGZQRdsB7Cln1mLG0qlxPEAU+Vq/Sx0iYE=
```

- [ ] **Step 2: Create `EncryptionConfig.java`**

```java
package com.devradar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class EncryptionConfig {

    @Bean(name = "githubTokenKey")
    public SecretKey githubTokenKey(@Value("${encryption.github-token-key-base64}") String base64) {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("github-token-key-base64 must decode to exactly 32 bytes (AES-256). Got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
```

- [ ] **Step 3: Write failing test**

```java
// backend/src/test/java/com/devradar/crypto/TokenEncryptorTest.java
package com.devradar.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEncryptorTest {

    private final TokenEncryptor enc = new TokenEncryptor(
        new SecretKeySpec(Base64.getDecoder().decode("UEjGRJjRBYGZQRdsB7Cln1mLG0qlxPEAU+Vq/Sx0iYE="), "AES")
    );

    @Test
    void encryptDecrypt_roundTrip() {
        String secret = "ghp_abcdef1234567890ABCDEF";
        String ct = enc.encrypt(secret);
        assertThat(ct).isNotEqualTo(secret);
        assertThat(enc.decrypt(ct)).isEqualTo(secret);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        // AES-GCM with random IV → ciphertexts differ even for the same plaintext
        String pt = "same-plaintext";
        String c1 = enc.encrypt(pt);
        String c2 = enc.encrypt(pt);
        assertThat(c1).isNotEqualTo(c2);
        assertThat(enc.decrypt(c1)).isEqualTo(pt);
        assertThat(enc.decrypt(c2)).isEqualTo(pt);
    }

    @Test
    void decrypt_tamperedCiphertext_throws() {
        String ct = enc.encrypt("secret");
        // Flip one byte in the body
        byte[] bytes = Base64.getDecoder().decode(ct);
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> enc.decrypt(tampered));
    }
}
```

- [ ] **Step 4: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=TokenEncryptorTest test
```

- [ ] **Step 5: Implement `TokenEncryptor.java`**

```java
package com.devradar.crypto;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryptor for short secrets (GitHub access tokens).
 * Output format (base64): [12-byte IV][ciphertext+16-byte GCM tag]
 */
@Component
public class TokenEncryptor {

    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    private final SecretKey key;

    public TokenEncryptor(@Qualifier("githubTokenKey") SecretKey key) {
        this.key = key;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(IV_BYTES + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new RuntimeException("encrypt failed", e);
        }
    }

    public String decrypt(String ciphertextB64) {
        try {
            byte[] all = Base64.getDecoder().decode(ciphertextB64);
            byte[] iv = new byte[IV_BYTES];
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("decrypt failed", e);
        }
    }
}
```

- [ ] **Step 6: Run — expect 3/3 PASS**

```bash
cd backend && mvn -Dtest=TokenEncryptorTest test
```

- [ ] **Step 7: Commit (no Co-Authored-By trailer)**

```bash
git add backend/src/main/java/com/devradar/crypto/TokenEncryptor.java backend/src/main/java/com/devradar/config/EncryptionConfig.java backend/src/test/java/com/devradar/crypto/TokenEncryptorTest.java backend/src/main/resources/application.yml backend/src/test/resources/application-test.yml
git commit -m "feat(crypto): add AES-256-GCM TokenEncryptor + key config"
```

---

## Task 2: Liquibase schema for GitHub identity + action proposals

**Files:**
- Create: `backend/src/main/resources/db/changelog/008-github-and-actions-schema.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create `008-github-and-actions-schema.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="008-create-user-github-identity" author="devradar">
        <createTable tableName="user_github_identity">
            <column name="user_id" type="BIGINT"><constraints primaryKey="true" nullable="false"/></column>
            <column name="github_user_id" type="BIGINT"><constraints nullable="false" unique="true" uniqueConstraintName="uk_user_github_identity_github_user_id"/></column>
            <column name="github_login" type="VARCHAR(120)"><constraints nullable="false"/></column>
            <column name="access_token_encrypted" type="VARCHAR(2048)"><constraints nullable="false"/></column>
            <column name="granted_scopes" type="VARCHAR(255)"/>
            <column name="linked_at" type="TIMESTAMP"><constraints nullable="false"/></column>
        </createTable>
        <addForeignKeyConstraint baseTableName="user_github_identity" baseColumnNames="user_id"
            constraintName="fk_user_github_identity_user" referencedTableName="users" referencedColumnNames="id" onDelete="CASCADE"/>
    </changeSet>

    <changeSet id="008-create-action-proposals" author="devradar">
        <createTable tableName="action_proposals">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="radar_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="user_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="kind" type="VARCHAR(30)"><constraints nullable="false"/></column>
            <column name="payload" type="JSON"><constraints nullable="false"/></column>
            <column name="status" type="VARCHAR(20)"><constraints nullable="false"/></column>
            <column name="pr_url" type="VARCHAR(1000)"/>
            <column name="failure_reason" type="VARCHAR(1000)"/>
            <column name="created_at" type="TIMESTAMP"><constraints nullable="false"/></column>
            <column name="updated_at" type="TIMESTAMP"/>
        </createTable>
        <addForeignKeyConstraint baseTableName="action_proposals" baseColumnNames="radar_id"
            constraintName="fk_action_proposals_radar" referencedTableName="radars" referencedColumnNames="id" onDelete="CASCADE"/>
        <addForeignKeyConstraint baseTableName="action_proposals" baseColumnNames="user_id"
            constraintName="fk_action_proposals_user" referencedTableName="users" referencedColumnNames="id" onDelete="CASCADE"/>
        <createIndex tableName="action_proposals" indexName="ix_action_proposals_user_status">
            <column name="user_id"/>
            <column name="status"/>
        </createIndex>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: Append include to master changelog**

In `backend/src/main/resources/db/changelog/db.changelog-master.xml`, after the existing 7 includes, add:

```xml
    <include file="db/changelog/008-github-and-actions-schema.xml"/>
```

- [ ] **Step 3: Verify migration**

```bash
cd backend
DB_HOST_PORT=${DB_HOST_PORT:-3307} docker compose up -d mysql
sleep 8
DB_HOST_PORT=${DB_HOST_PORT:-3307} mvn spring-boot:run -Dspring-boot.run.profiles=demo &
APP=$!
sleep 25
DB_HOST_PORT=${DB_HOST_PORT:-3307} docker compose exec mysql mysql -udevradar -pdevradar devradar -e "SHOW TABLES;" | grep -iE "(github|action)"
kill $APP
```

Expected: `user_github_identity`, `action_proposals`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/
git commit -m "feat(db): add user_github_identity + action_proposals schema"
```

---

## Task 3: Domain entities + enums + repositories

**Files:**
- Create: `backend/src/main/java/com/devradar/domain/UserGithubIdentity.java`
- Create: `backend/src/main/java/com/devradar/domain/ActionProposal.java`
- Create: `backend/src/main/java/com/devradar/domain/ActionProposalKind.java`
- Create: `backend/src/main/java/com/devradar/domain/ActionProposalStatus.java`
- Create: `backend/src/main/java/com/devradar/repository/UserGithubIdentityRepository.java`
- Create: `backend/src/main/java/com/devradar/repository/ActionProposalRepository.java`

- [ ] **Step 1: Create enums**

```java
// backend/src/main/java/com/devradar/domain/ActionProposalKind.java
package com.devradar.domain;
public enum ActionProposalKind { auto_pr_cve }
```

```java
// backend/src/main/java/com/devradar/domain/ActionProposalStatus.java
package com.devradar.domain;
public enum ActionProposalStatus { PROPOSED, EXECUTED, DISMISSED, FAILED }
```

- [ ] **Step 2: Create `UserGithubIdentity.java`**

```java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_github_identity")
public class UserGithubIdentity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "github_user_id", nullable = false, unique = true)
    private Long githubUserId;

    @Column(name = "github_login", nullable = false, length = 120)
    private String githubLogin;

    @Column(name = "access_token_encrypted", nullable = false, length = 2048)
    private String accessTokenEncrypted;

    @Column(name = "granted_scopes", length = 255)
    private String grantedScopes;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @PrePersist
    void onCreate() { linkedAt = Instant.now(); }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getGithubUserId() { return githubUserId; }
    public void setGithubUserId(Long v) { this.githubUserId = v; }
    public String getGithubLogin() { return githubLogin; }
    public void setGithubLogin(String v) { this.githubLogin = v; }
    public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
    public void setAccessTokenEncrypted(String v) { this.accessTokenEncrypted = v; }
    public String getGrantedScopes() { return grantedScopes; }
    public void setGrantedScopes(String v) { this.grantedScopes = v; }
    public Instant getLinkedAt() { return linkedAt; }
}
```

- [ ] **Step 3: Create `ActionProposal.java`**

```java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "action_proposals")
public class ActionProposal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "radar_id", nullable = false)
    private Long radarId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActionProposalKind kind;

    @Column(nullable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActionProposalStatus status;

    @Column(name = "pr_url", length = 1000)
    private String prUrl;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getRadarId() { return radarId; }
    public void setRadarId(Long v) { this.radarId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public ActionProposalKind getKind() { return kind; }
    public void setKind(ActionProposalKind v) { this.kind = v; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { this.payload = v; }
    public ActionProposalStatus getStatus() { return status; }
    public void setStatus(ActionProposalStatus v) { this.status = v; }
    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String v) { this.prUrl = v; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String v) { this.failureReason = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 4: Create repositories**

```java
// backend/src/main/java/com/devradar/repository/UserGithubIdentityRepository.java
package com.devradar.repository;

import com.devradar.domain.UserGithubIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserGithubIdentityRepository extends JpaRepository<UserGithubIdentity, Long> {
    Optional<UserGithubIdentity> findByGithubUserId(Long githubUserId);
}
```

```java
// backend/src/main/java/com/devradar/repository/ActionProposalRepository.java
package com.devradar.repository;

import com.devradar.domain.ActionProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActionProposalRepository extends JpaRepository<ActionProposal, Long> {
    List<ActionProposal> findByRadarIdOrderByCreatedAtAsc(Long radarId);
    List<ActionProposal> findByUserIdOrderByCreatedAtDesc(Long userId);
}
```

- [ ] **Step 5: Verify compile + commit**

```bash
cd backend && mvn -DskipTests compile
git add backend/src/main/java/com/devradar/domain/UserGithubIdentity.java backend/src/main/java/com/devradar/domain/ActionProposal.java backend/src/main/java/com/devradar/domain/ActionProposalKind.java backend/src/main/java/com/devradar/domain/ActionProposalStatus.java backend/src/main/java/com/devradar/repository/UserGithubIdentityRepository.java backend/src/main/java/com/devradar/repository/ActionProposalRepository.java
git commit -m "feat(domain): add UserGithubIdentity + ActionProposal entities + repos"
```

---

## Task 4: GitHubOAuthClient — TDD with WireMock

**Files:**
- Create: `backend/src/main/java/com/devradar/github/GitHubOAuthClient.java`
- Create: `backend/src/test/java/com/devradar/github/GitHubOAuthClientTest.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`

- [ ] **Step 1: Add GitHub config**

Append to `backend/src/main/resources/application.yml`:

```yaml
github:
  oauth:
    client-id: ${GITHUB_OAUTH_CLIENT_ID:}
    client-secret: ${GITHUB_OAUTH_CLIENT_SECRET:}
    redirect-uri: ${GITHUB_OAUTH_REDIRECT_URI:http://localhost:8080/api/auth/github/callback}
    authorize-url: https://github.com/login/oauth/authorize
    token-url: https://github.com/login/oauth/access_token
  api:
    base-url: https://api.github.com
```

Append to `backend/src/test/resources/application-test.yml`:

```yaml
github:
  oauth:
    client-id: test-client-id
    client-secret: test-client-secret
    redirect-uri: http://localhost:8080/api/auth/github/callback
    authorize-url: http://localhost:0/login/oauth/authorize
    token-url: http://localhost:0/login/oauth/access_token
  api:
    base-url: http://localhost:0
```

- [ ] **Step 2: Write failing test**

```java
// backend/src/test/java/com/devradar/github/GitHubOAuthClientTest.java
package com.devradar.github;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubOAuthClientTest {

    WireMockServer wm;
    GitHubOAuthClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        client = new GitHubOAuthClient(
            RestClient.builder(),
            "test-cid",
            "test-secret",
            "http://localhost:8080/api/auth/github/callback",
            "http://localhost:" + wm.port() + "/login/oauth/access_token"
        );
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void exchangeCode_returnsTokenAndScopes() {
        wm.stubFor(WireMock.post(WireMock.urlPathEqualTo("/login/oauth/access_token"))
            .willReturn(WireMock.okJson("""
                {"access_token":"gho_abc123","token_type":"bearer","scope":"read:user,repo"}
                """)));

        GitHubOAuthClient.AccessTokenResponse r = client.exchangeCode("the-code-from-callback");

        assertThat(r.accessToken()).isEqualTo("gho_abc123");
        assertThat(r.grantedScopes()).contains("repo");
    }
}
```

- [ ] **Step 3: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=GitHubOAuthClientTest test
```

- [ ] **Step 4: Implement `GitHubOAuthClient.java`**

```java
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

    /** Build the URL to redirect the user to for consent. State is opaque CSRF token to round-trip. */
    public String buildAuthorizeUrl(String authorizeBaseUrl, String state, String scopes) {
        return UriComponentsBuilder.fromUriString(authorizeBaseUrl)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", scopes)
            .queryParam("state", state)
            .build(true).toUriString();
    }

    /** Exchange the OAuth `code` for an access token. */
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
```

- [ ] **Step 5: Run — expect 1/1 PASS**

```bash
cd backend && mvn -Dtest=GitHubOAuthClientTest test
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/github/GitHubOAuthClient.java backend/src/test/java/com/devradar/github/GitHubOAuthClientTest.java backend/src/main/resources/application.yml backend/src/test/resources/application-test.yml
git commit -m "feat(github): add GitHubOAuthClient (code → token exchange) + WireMock test"
```

---

## Task 5: GitHubApiClient — TDD with WireMock

**Files:**
- Create: `backend/src/main/java/com/devradar/github/GitHubApiClient.java`
- Create: `backend/src/main/java/com/devradar/github/GitHubFileMutation.java`
- Create: `backend/src/main/java/com/devradar/github/GitHubAffectedFile.java`
- Create: `backend/src/test/java/com/devradar/github/GitHubApiClientTest.java`

This client wraps four GitHub REST endpoints needed for Auto-PR:
- `GET /user` — get authenticated user (login + id)
- `GET /user/repos` — list the user's repos
- `GET /repos/{owner}/{repo}/contents/{path}?ref={branch}` — fetch file content + SHA
- `POST /repos/{owner}/{repo}/git/refs` — create branch
- `PUT /repos/{owner}/{repo}/contents/{path}` — update file in a branch
- `POST /repos/{owner}/{repo}/pulls` — create PR

- [ ] **Step 1: Create DTOs**

```java
// backend/src/main/java/com/devradar/github/GitHubFileMutation.java
package com.devradar.github;
public record GitHubFileMutation(String repoFullName, String filePath, String newContentBase64, String fileSha, String commitMessage, String branchName) {}
```

```java
// backend/src/main/java/com/devradar/github/GitHubAffectedFile.java
package com.devradar.github;
public record GitHubAffectedFile(String repoFullName, String filePath, String currentVersion, String fileSha) {}
```

- [ ] **Step 2: Write failing tests (covers user, repos, content fetch, branch creation, content update, PR creation)**

```java
// backend/src/test/java/com/devradar/github/GitHubApiClientTest.java
package com.devradar.github;

import com.fasterxml.jackson.databind.JsonNode;
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
}
```

- [ ] **Step 3: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=GitHubApiClientTest test
```

- [ ] **Step 4: Implement `GitHubApiClient.java`**

```java
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
        JsonNode arr = http.get().uri(uri -> uri.path("/user/repos").queryParam("per_page", "100").build())
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
        // Get the SHA of the source branch
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

    public String createPullRequest(String token, String repoFullName, String title, String body, String head, String base) {
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
```

- [ ] **Step 5: Run — expect 6/6 PASS**

```bash
cd backend && mvn -Dtest=GitHubApiClientTest test
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/github/GitHubApiClient.java backend/src/main/java/com/devradar/github/GitHubFileMutation.java backend/src/main/java/com/devradar/github/GitHubAffectedFile.java backend/src/test/java/com/devradar/github/GitHubApiClientTest.java
git commit -m "feat(github): add GitHubApiClient (user, repos, contents, branches, PRs) + WireMock tests"
```

---

## Task 6: GitHub OAuth REST endpoints

**Files:**
- Modify: `backend/src/main/java/com/devradar/web/rest/AuthResource.java`
- Modify: `backend/src/main/java/com/devradar/config/SecurityConfig.java` (permit `/api/auth/github/**`)

The flow:
1. `GET /api/auth/github/start` — generates a state token, redirects (302) to GitHub consent
2. `GET /api/auth/github/callback?code=...&state=...` — exchanges code, fetches GitHub user, finds-or-creates a Dev Radar user, persists encrypted token, issues a Dev Radar JWT, redirects to frontend with `?token=...` (or returns JSON for now)

For MVP simplicity, state is stored in-memory in a `ConcurrentHashMap<String, Instant>` with 10-min TTL. Production would use a server-side session or signed cookie.

- [ ] **Step 1: Replace `AuthResource.java` with the version including GitHub endpoints**

```java
package com.devradar.web.rest;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.User;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.github.GitHubOAuthClient;
import com.devradar.repository.UserGithubIdentityRepository;
import com.devradar.repository.UserRepository;
import com.devradar.security.JwtTokenProvider;
import com.devradar.service.AuthService;
import com.devradar.web.rest.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuthResource.class);
    private static final java.time.Duration STATE_TTL = java.time.Duration.ofMinutes(10);

    private final AuthService auth;
    private final GitHubOAuthClient ghOauth;
    private final GitHubApiClient ghApi;
    private final UserRepository userRepo;
    private final UserGithubIdentityRepository identityRepo;
    private final TokenEncryptor encryptor;
    private final JwtTokenProvider jwt;
    private final String authorizeUrl;
    private final String defaultScopes;

    private final ConcurrentHashMap<String, Instant> issuedStates = new ConcurrentHashMap<>();

    public AuthResource(
        AuthService auth,
        GitHubOAuthClient ghOauth,
        GitHubApiClient ghApi,
        UserRepository userRepo,
        UserGithubIdentityRepository identityRepo,
        TokenEncryptor encryptor,
        JwtTokenProvider jwt,
        @Value("${github.oauth.authorize-url}") String authorizeUrl,
        @Value("${github.oauth.default-scopes:read:user,public_repo,repo}") String defaultScopes
    ) {
        this.auth = auth;
        this.ghOauth = ghOauth;
        this.ghApi = ghApi;
        this.userRepo = userRepo;
        this.identityRepo = identityRepo;
        this.encryptor = encryptor;
        this.jwt = jwt;
        this.authorizeUrl = authorizeUrl;
        this.defaultScopes = defaultScopes;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequestDTO body) {
        auth.register(body.email(), body.password(), body.displayName());
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO body) {
        var r = auth.login(body.email(), body.password());
        return new LoginResponseDTO(r.accessToken(), r.refreshToken());
    }

    @PostMapping("/refresh")
    public LoginResponseDTO refresh(@Valid @RequestBody RefreshRequestDTO body) {
        var r = auth.refresh(body.refreshToken());
        return new LoginResponseDTO(r.accessToken(), r.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequestDTO body) {
        auth.logout(body.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Step 1 of GitHub OAuth: redirect user to GitHub consent. */
    @GetMapping("/github/start")
    public ResponseEntity<Void> githubStart() {
        purgeExpiredStates();
        String state = GitHubOAuthClient.generateState();
        issuedStates.put(state, Instant.now().plus(STATE_TTL));
        String url = ghOauth.buildAuthorizeUrl(authorizeUrl, state, defaultScopes);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /** Step 2 of GitHub OAuth: GitHub redirects back with code+state. We exchange code, persist identity, issue JWT. */
    @GetMapping("/github/callback")
    @Transactional
    public LoginResponseDTO githubCallback(@RequestParam String code, @RequestParam String state) {
        Instant expires = issuedStates.remove(state);
        if (expires == null || expires.isBefore(Instant.now())) {
            throw new RuntimeException("invalid or expired state");
        }

        var tokenResp = ghOauth.exchangeCode(code);
        var ghUser = ghApi.getAuthenticatedUser(tokenResp.accessToken());

        // Find existing identity OR create new user
        Optional<UserGithubIdentity> existing = identityRepo.findByGithubUserId(ghUser.id());
        User u;
        UserGithubIdentity identity;
        if (existing.isPresent()) {
            identity = existing.get();
            u = userRepo.findById(identity.getUserId()).orElseThrow();
            // Refresh the encrypted token + scopes
            identity.setAccessTokenEncrypted(encryptor.encrypt(tokenResp.accessToken()));
            identity.setGrantedScopes(tokenResp.grantedScopes());
            identityRepo.save(identity);
        } else {
            // Create a fresh user with a placeholder email derived from the GH login
            String email = ghUser.login() + "@github.users.noreply.devradar";
            u = new User();
            u.setEmail(email);
            u.setDisplayName(ghUser.login());
            u.setActive(true);
            u = userRepo.save(u);

            identity = new UserGithubIdentity();
            identity.setUserId(u.getId());
            identity.setGithubUserId(ghUser.id());
            identity.setGithubLogin(ghUser.login());
            identity.setAccessTokenEncrypted(encryptor.encrypt(tokenResp.accessToken()));
            identity.setGrantedScopes(tokenResp.grantedScopes());
            identityRepo.save(identity);
        }

        String jwtToken = jwt.generateAccessToken(u.getId(), u.getEmail());
        return new LoginResponseDTO(jwtToken, "");
    }

    private void purgeExpiredStates() {
        Instant now = Instant.now();
        issuedStates.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
```

- [ ] **Step 2: Update `SecurityConfig.java` to permit github callback**

The existing `permitAll()` for `/api/auth/**` already covers it — confirm by reading `backend/src/main/java/com/devradar/config/SecurityConfig.java`. If it already has `requestMatchers("/api/auth/**").permitAll()`, no change needed.

- [ ] **Step 3: Verify compile**

```bash
cd backend && mvn -DskipTests compile
```

- [ ] **Step 4: Run all existing tests to make sure nothing regressed**

```bash
cd backend && mvn test
```

Expected: still 35+ passing (the new code is wired but not tested here — IT comes in Task 12).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/web/rest/AuthResource.java
git commit -m "feat(auth): add /api/auth/github/start + /github/callback OAuth endpoints"
```

---

## Task 7: ToolContext + ToolRegistry signature change

**Files:**
- Create: `backend/src/main/java/com/devradar/ai/tools/ToolContext.java`
- Modify: `backend/src/main/java/com/devradar/ai/tools/ToolRegistry.java`
- Modify: `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java`
- Modify: `backend/src/main/java/com/devradar/radar/RadarGenerationService.java`
- Modify: `backend/src/test/java/com/devradar/ai/RadarOrchestratorTest.java`

The existing 3 tools don't need user context. The new CheckRepoForVulnerabilityTool will. Add `ToolContext` to the dispatch signature so per-user calls work.

- [ ] **Step 1: Create `ToolContext.java`**

```java
package com.devradar.ai.tools;
public record ToolContext(Long userId, Long radarId) {}
```

- [ ] **Step 2: Replace `ToolRegistry.java` with the new dispatch signature**

```java
package com.devradar.ai.tools;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolRegistry {

    private final SearchItemsTool search;
    private final ScoreRelevanceTool score;
    private final GetItemDetailTool detail;
    private final CheckRepoForVulnerabilityTool repoCheck;

    public ToolRegistry(SearchItemsTool search, ScoreRelevanceTool score, GetItemDetailTool detail, CheckRepoForVulnerabilityTool repoCheck) {
        this.search = search; this.score = score; this.detail = detail; this.repoCheck = repoCheck;
    }

    public List<ToolDefinition> definitions() {
        return List.of(search.definition(), score.definition(), detail.definition(), repoCheck.definition());
    }

    public String dispatch(String name, String inputJson, ToolContext ctx) {
        return switch (name) {
            case SearchItemsTool.NAME -> search.execute(inputJson);
            case ScoreRelevanceTool.NAME -> score.execute(inputJson);
            case GetItemDetailTool.NAME -> detail.execute(inputJson);
            case CheckRepoForVulnerabilityTool.NAME -> repoCheck.execute(inputJson, ctx);
            default -> "{\"error\":\"unknown tool: " + name + "\"}";
        };
    }
}
```

`CheckRepoForVulnerabilityTool` doesn't exist yet; it's created in Task 8. The compile will fail until Task 8 lands. That's intentional — Task 7 prepares the registry signature.

- [ ] **Step 3: Replace `RadarOrchestrator.java` to thread ToolContext**

In `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java`, change two things:
1. The `generate(...)` method signature: add `ToolContext ctx` parameter
2. The dispatch call: `tools.dispatch(call.name(), call.inputJson(), ctx)`

```java
package com.devradar.ai;

import com.devradar.ai.tools.ToolContext;
import com.devradar.ai.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RadarOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(RadarOrchestrator.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are a tech radar analyst. Given a user's interest tags and a pool of recently ingested items,
        identify 3-5 themes that genuinely matter to this user. Use the provided tools to refine your candidate
        set. When you encounter a CVE-related item, you may call checkRepoForVulnerability to see if the user's
        repos are affected — if so, that automatically creates an action proposal the user can approve.
        When you are done investigating, output a single JSON object with NO PROSE around it:

        {"themes": [
          {"title": "...", "summary": "...", "item_ids": [<source_item ids cited>]},
          ...
        ]}

        Each theme should:
        - Have a title under 100 chars.
        - Have a summary 1-3 sentences citing why it matters to this user specifically.
        - Reference 1-5 source_item ids from your search results.
        Do not invent ids — only cite ids you've seen in tool results.
        """;

    private final AiClient ai;
    private final ToolRegistry tools;
    private final String model;
    private final int maxIterations;
    private final int maxTokens;

    public RadarOrchestrator(AiClient ai, ToolRegistry tools,
                             @Value("${anthropic.orchestrator-model}") String model,
                             @Value("${anthropic.max-tool-iterations}") int maxIterations,
                             @Value("${anthropic.max-tokens-per-call}") int maxTokens) {
        this.ai = ai; this.tools = tools; this.model = model;
        this.maxIterations = maxIterations; this.maxTokens = maxTokens;
    }

    public RadarOrchestrationResult generate(List<String> userInterests, List<Long> candidateItemIds, ToolContext ctx) {
        String userMsg = """
            User interests: %s
            Candidate item ids (from last 7 days, pre-filtered to user's tags): %s

            Use the tools to look up titles, score relevance, fetch full details, check the user's repos for vulnerabilities, and produce the final themes JSON.
            """.formatted(userInterests, candidateItemIds);

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.userText(userMsg));

        int totalIn = 0, totalOut = 0;
        String lastText = "";

        for (int iter = 0; iter < maxIterations; iter++) {
            AiResponse resp = ai.generate(model, SYSTEM_PROMPT, messages, tools.definitions(), maxTokens);
            totalIn += resp.inputTokens();
            totalOut += resp.outputTokens();
            if (resp.text() != null && !resp.text().isBlank()) lastText = resp.text();

            if (resp.toolCalls().isEmpty() || "end_turn".equals(resp.stopReason())) break;

            List<AiToolResult> results = new ArrayList<>();
            for (AiToolCall call : resp.toolCalls()) {
                String out = tools.dispatch(call.name(), call.inputJson(), ctx);
                boolean isError = out != null && out.contains("\"error\"");
                results.add(new AiToolResult(call.id(), out, isError));
                LOG.debug("tool dispatched name={} resultLen={}", call.name(), out == null ? 0 : out.length());
            }
            messages.add(AiMessage.userToolResults(results));
        }

        List<RadarOrchestrationTheme> themes = new ArrayList<>();
        try {
            JsonNode root = JSON.readTree(extractJsonObject(lastText));
            JsonNode arr = root.get("themes");
            if (arr != null && arr.isArray()) {
                int order = 0;
                for (JsonNode t : arr) {
                    String title = t.path("title").asText();
                    String summary = t.path("summary").asText();
                    List<Long> ids = new ArrayList<>();
                    for (JsonNode i : t.path("item_ids")) ids.add(i.asLong());
                    themes.add(new RadarOrchestrationTheme(title, summary, ids, order++));
                }
            }
        } catch (Exception e) {
            LOG.warn("failed to parse final radar JSON; returning empty themes. err={}", e.toString());
        }

        return new RadarOrchestrationResult(themes, totalIn, totalOut);
    }

    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) return "{}";
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return text.substring(start, i + 1); }
        }
        return "{}";
    }

    public record RadarOrchestrationResult(List<RadarOrchestrationTheme> themes, int totalInputTokens, int totalOutputTokens) {}
    public record RadarOrchestrationTheme(String title, String summary, List<Long> itemIds, int displayOrder) {}
}
```

- [ ] **Step 4: Update `RadarGenerationService.java` to pass ToolContext**

In `backend/src/main/java/com/devradar/radar/RadarGenerationService.java`, update the orchestrator call inside `runGeneration`:

```java
// OLD:
// var result = orchestrator.generate(userInterests, candidateIds);
// NEW:
var result = orchestrator.generate(userInterests, candidateIds, new com.devradar.ai.tools.ToolContext(userId, radarId));
```

- [ ] **Step 5: Update `RadarOrchestratorTest.java` to pass null context**

In both tests, change the orchestrator call to pass a null context (or a fresh empty one):

```java
// OLD:
// var result = orch.generate(List.of("spring_boot"), List.of(1L, 2L, 3L));
// NEW:
var result = orch.generate(List.of("spring_boot"), List.of(1L, 2L, 3L), new com.devradar.ai.tools.ToolContext(null, null));
```

(Apply same edit in the second test.)

- [ ] **Step 6: Verify compile (will fail until Task 8 lands)**

```bash
cd backend && mvn -DskipTests compile
```

Expected: compile error because `CheckRepoForVulnerabilityTool` doesn't exist yet. That's OK — proceed to Task 8 to fix it.

**DO NOT COMMIT YET** — Task 8 lands the missing class. Wait until Task 8 step 5, then commit Tasks 7+8 together.

---

## Task 8: CheckRepoForVulnerabilityTool — TDD with mocked GitHub client

**Files:**
- Create: `backend/src/main/java/com/devradar/ai/tools/CheckRepoForVulnerabilityTool.java`
- Create: `backend/src/test/java/com/devradar/ai/tools/CheckRepoForVulnerabilityToolTest.java`

This tool:
- Takes `{package: "...", version_pattern: "...", ghsa_id: "..."}` as input
- Looks up the user's GitHub identity (from `ToolContext.userId()`)
- Decrypts their access token
- Calls `GitHubApiClient.listRepos` then for each repo, fetches `pom.xml` or `package.json` (best-effort; ignore repos without these)
- Greps the file contents for the package name + version match
- Returns a JSON array `[{"repo":"alice/r1","file":"pom.xml","current_version":"2.16.2"}, ...]`
- Side effect: persists an `ActionProposal` row for each affected file (so the user can approve later)

For MVP simplicity, the version match is a simple substring check on the dependency line. A production implementation would use proper Maven/npm version-range semantics.

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/devradar/ai/tools/CheckRepoForVulnerabilityToolTest.java
package com.devradar.ai.tools;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.*;
import com.devradar.github.GitHubApiClient;
import com.devradar.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class CheckRepoForVulnerabilityToolTest extends AbstractIntegrationTest {

    @MockBean GitHubApiClient gh;

    @Autowired CheckRepoForVulnerabilityTool tool;
    @Autowired UserRepository userRepo;
    @Autowired UserGithubIdentityRepository identityRepo;
    @Autowired RadarRepository radarRepo;
    @Autowired ActionProposalRepository proposalRepo;
    @Autowired TokenEncryptor encryptor;
    @Autowired ObjectMapper json;

    @Test
    void execute_findsAffectedRepo_andRecordsProposal() throws Exception {
        // Given a user with a linked GitHub identity (encrypted token) and a pending radar
        User u = new User();
        u.setEmail("vuln-test@example.com");
        u.setDisplayName("V");
        u.setPasswordHash(null);
        u.setActive(true);
        u = userRepo.save(u);

        UserGithubIdentity gid = new UserGithubIdentity();
        gid.setUserId(u.getId());
        gid.setGithubUserId(99999L);
        gid.setGithubLogin("alice");
        gid.setAccessTokenEncrypted(encryptor.encrypt("gho_test_token"));
        gid.setGrantedScopes("repo");
        identityRepo.save(gid);

        Radar r = new Radar();
        r.setUserId(u.getId());
        r.setStatus(RadarStatus.GENERATING);
        r.setPeriodStart(Instant.now().minusSeconds(7 * 86400));
        r.setPeriodEnd(Instant.now());
        r = radarRepo.save(r);

        // When the tool fetches repos and pom.xml content
        when(gh.listRepos(anyString())).thenReturn(List.of(
            new GitHubApiClient.RepoInfo("alice/creeno-backend", "main")
        ));
        String pomContent = """
            <project>
              <dependencies>
                <dependency>
                  <groupId>com.fasterxml.jackson.core</groupId>
                  <artifactId>jackson-databind</artifactId>
                  <version>2.16.2</version>
                </dependency>
              </dependencies>
            </project>
            """;
        when(gh.getFileContent(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new GitHubApiClient.FileContent(pomContent, "file-sha-abc", "base64-here"));

        String input = """
            {"package": "jackson-databind", "version_pattern": "2.16.2", "ghsa_id": "GHSA-test-1234"}
            """;
        String result = tool.execute(input, new ToolContext(u.getId(), r.getId()));

        JsonNode arr = json.readTree(result);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isEqualTo(1);
        assertThat(arr.get(0).get("repo").asText()).isEqualTo("alice/creeno-backend");

        // Side effect: an ActionProposal was recorded
        var proposals = proposalRepo.findByRadarIdOrderByCreatedAtAsc(r.getId());
        assertThat(proposals).hasSize(1);
        assertThat(proposals.get(0).getKind()).isEqualTo(ActionProposalKind.auto_pr_cve);
        assertThat(proposals.get(0).getStatus()).isEqualTo(ActionProposalStatus.PROPOSED);
    }

    @Test
    void execute_userWithoutGithub_returnsEmpty() throws Exception {
        User u = new User();
        u.setEmail("nogithub@example.com");
        u.setDisplayName("N");
        u.setPasswordHash("$2a$12$abcdefghijklmnopqrstuv");
        u.setActive(true);
        u = userRepo.save(u);

        Radar r = new Radar();
        r.setUserId(u.getId());
        r.setStatus(RadarStatus.GENERATING);
        r.setPeriodStart(Instant.now().minusSeconds(86400));
        r.setPeriodEnd(Instant.now());
        r = radarRepo.save(r);

        String result = tool.execute("{\"package\":\"x\",\"version_pattern\":\"1.0\",\"ghsa_id\":\"GHSA-1\"}",
            new ToolContext(u.getId(), r.getId()));
        assertThat(json.readTree(result).isArray()).isTrue();
        assertThat(json.readTree(result).size()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=CheckRepoForVulnerabilityToolTest test
```

- [ ] **Step 3: Implement `CheckRepoForVulnerabilityTool.java`**

```java
package com.devradar.ai.tools;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.ActionProposal;
import com.devradar.domain.ActionProposalKind;
import com.devradar.domain.ActionProposalStatus;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.repository.ActionProposalRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class CheckRepoForVulnerabilityTool {

    public static final String NAME = "checkRepoForVulnerability";
    public static final String DESCRIPTION = "For the current user, scan their linked GitHub repositories for files (pom.xml, package.json) that reference the given package at the given version. Returns affected repos and records action proposals (Auto-PR opportunities) the user can approve.";
    public static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "package":          { "type": "string", "description": "Package name, e.g. jackson-databind" },
            "version_pattern":  { "type": "string", "description": "Substring/version to match in dependency declarations, e.g. 2.16.2" },
            "ghsa_id":          { "type": "string", "description": "GitHub Security Advisory ID, e.g. GHSA-xxxx-yyyy-zzzz" }
          },
          "required": ["package", "version_pattern", "ghsa_id"]
        }
        """;

    private static final Logger LOG = LoggerFactory.getLogger(CheckRepoForVulnerabilityTool.class);
    private static final List<String> CANDIDATE_FILES = List.of("pom.xml", "package.json", "build.gradle", "build.gradle.kts");

    private final GitHubApiClient gh;
    private final UserGithubIdentityRepository identityRepo;
    private final ActionProposalRepository proposalRepo;
    private final TokenEncryptor encryptor;
    private final ObjectMapper json = new ObjectMapper();

    public CheckRepoForVulnerabilityTool(
        GitHubApiClient gh,
        UserGithubIdentityRepository identityRepo,
        ActionProposalRepository proposalRepo,
        TokenEncryptor encryptor
    ) {
        this.gh = gh;
        this.identityRepo = identityRepo;
        this.proposalRepo = proposalRepo;
        this.encryptor = encryptor;
    }

    public ToolDefinition definition() {
        return new ToolDefinition(NAME, DESCRIPTION, INPUT_SCHEMA);
    }

    @Transactional
    public String execute(String inputJson, ToolContext ctx) {
        if (ctx == null || ctx.userId() == null) {
            return "[]";
        }
        try {
            JsonNode in = json.readTree(inputJson);
            String pkg = in.path("package").asText();
            String versionPattern = in.path("version_pattern").asText();
            String ghsaId = in.path("ghsa_id").asText();

            Optional<UserGithubIdentity> maybeIdentity = identityRepo.findById(ctx.userId());
            if (maybeIdentity.isEmpty()) {
                LOG.debug("user {} has no GitHub identity; checkRepoForVulnerability returns empty", ctx.userId());
                return "[]";
            }
            UserGithubIdentity identity = maybeIdentity.get();
            String token = encryptor.decrypt(identity.getAccessTokenEncrypted());

            ArrayNode out = json.createArrayNode();
            for (GitHubApiClient.RepoInfo repo : gh.listRepos(token)) {
                for (String fileName : CANDIDATE_FILES) {
                    try {
                        GitHubApiClient.FileContent fc = gh.getFileContent(token, repo.fullName(), fileName, repo.defaultBranch());
                        if (fc.text().contains(pkg) && fc.text().contains(versionPattern)) {
                            ObjectNode hit = json.createObjectNode();
                            hit.put("repo", repo.fullName());
                            hit.put("file", fileName);
                            hit.put("current_version", versionPattern);
                            hit.put("file_sha", fc.sha());
                            out.add(hit);
                            recordProposal(ctx, repo.fullName(), fileName, fc.sha(), pkg, versionPattern, ghsaId);
                        }
                    } catch (Exception e) {
                        // file probably doesn't exist — ignore and continue
                    }
                }
            }
            return json.writeValueAsString(out);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private void recordProposal(ToolContext ctx, String repo, String filePath, String fileSha,
                                String pkg, String versionPattern, String ghsaId) {
        try {
            ObjectNode payload = json.createObjectNode();
            payload.put("repo", repo);
            payload.put("file_path", filePath);
            payload.put("file_sha", fileSha);
            payload.put("package", pkg);
            payload.put("current_version", versionPattern);
            payload.put("ghsa_id", ghsaId);

            ActionProposal p = new ActionProposal();
            p.setRadarId(ctx.radarId());
            p.setUserId(ctx.userId());
            p.setKind(ActionProposalKind.auto_pr_cve);
            p.setStatus(ActionProposalStatus.PROPOSED);
            p.setPayload(json.writeValueAsString(payload));
            proposalRepo.save(p);
        } catch (Exception e) {
            LOG.warn("failed to record action proposal: {}", e.toString());
        }
    }
}
```

- [ ] **Step 4: Run — expect 2/2 PASS**

```bash
cd backend && mvn -Dtest=CheckRepoForVulnerabilityToolTest test
```

- [ ] **Step 5: Run full mvn compile + test (Tasks 7+8 together)**

```bash
cd backend && mvn -DskipTests compile
cd backend && mvn test
```
Expected: BUILD SUCCESS, all prior tests still passing.

- [ ] **Step 6: Commit Tasks 7+8 together**

```bash
git add backend/src/main/java/com/devradar/ai/tools/ToolContext.java backend/src/main/java/com/devradar/ai/tools/ToolRegistry.java backend/src/main/java/com/devradar/ai/RadarOrchestrator.java backend/src/main/java/com/devradar/radar/RadarGenerationService.java backend/src/test/java/com/devradar/ai/RadarOrchestratorTest.java backend/src/main/java/com/devradar/ai/tools/CheckRepoForVulnerabilityTool.java backend/src/test/java/com/devradar/ai/tools/CheckRepoForVulnerabilityToolTest.java
git commit -m "feat(ai): add CheckRepoForVulnerabilityTool + ToolContext for per-user dispatch"
```

---

## Task 9: AutoPrExecutor — TDD with WireMock GitHub

**Files:**
- Create: `backend/src/main/java/com/devradar/action/AutoPrExecutor.java`
- Create: `backend/src/test/java/com/devradar/action/AutoPrExecutorTest.java`

This service:
- Takes an approved `ActionProposal` (kind=auto_pr_cve)
- Decrypts the user's GitHub token
- Reads the file at the recorded SHA
- Generates a new version of the file with the version bump (simple string replacement for MVP)
- Creates a branch (`dev-radar/cve-{ghsa_id}`)
- PUTs the new file content
- Opens a PR
- Updates the proposal row with `status=EXECUTED` + `pr_url`

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/devradar/action/AutoPrExecutorTest.java
package com.devradar.action;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.*;
import com.devradar.github.GitHubApiClient;
import com.devradar.repository.*;
import com.devradar.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutoPrExecutorTest extends AbstractIntegrationTest {

    @MockBean GitHubApiClient gh;

    @Autowired AutoPrExecutor executor;
    @Autowired UserRepository userRepo;
    @Autowired UserGithubIdentityRepository identityRepo;
    @Autowired RadarRepository radarRepo;
    @Autowired ActionProposalRepository proposalRepo;
    @Autowired TokenEncryptor encryptor;
    @Autowired ObjectMapper json;

    @Test
    void execute_createsBranch_putsFile_opensPR_updatesProposal() throws Exception {
        // Given a user + identity + radar + a PROPOSED proposal pointing to alice/repo1 pom.xml
        User u = new User();
        u.setEmail("autopr@example.com");
        u.setDisplayName("A");
        u.setActive(true);
        u = userRepo.save(u);

        UserGithubIdentity gid = new UserGithubIdentity();
        gid.setUserId(u.getId());
        gid.setGithubUserId(11111L);
        gid.setGithubLogin("alice");
        gid.setAccessTokenEncrypted(encryptor.encrypt("gho_pr_token"));
        gid.setGrantedScopes("repo");
        identityRepo.save(gid);

        Radar r = new Radar();
        r.setUserId(u.getId());
        r.setStatus(RadarStatus.READY);
        r.setPeriodStart(Instant.now().minusSeconds(86400));
        r.setPeriodEnd(Instant.now());
        r = radarRepo.save(r);

        ObjectNode payload = json.createObjectNode();
        payload.put("repo", "alice/repo1");
        payload.put("file_path", "pom.xml");
        payload.put("file_sha", "old-sha");
        payload.put("package", "jackson-databind");
        payload.put("current_version", "2.16.2");
        payload.put("ghsa_id", "GHSA-test-9876");

        ActionProposal p = new ActionProposal();
        p.setRadarId(r.getId());
        p.setUserId(u.getId());
        p.setKind(ActionProposalKind.auto_pr_cve);
        p.setStatus(ActionProposalStatus.PROPOSED);
        p.setPayload(json.writeValueAsString(payload));
        p = proposalRepo.save(p);

        // GitHub API responses
        String pomBefore = "<dependency><artifactId>jackson-databind</artifactId><version>2.16.2</version></dependency>";
        when(gh.getFileContent(anyString(), eq("alice/repo1"), eq("pom.xml"), isNull()))
            .thenReturn(new GitHubApiClient.FileContent(pomBefore, "old-sha", java.util.Base64.getEncoder().encodeToString(pomBefore.getBytes())));
        when(gh.listRepos(anyString())).thenReturn(List.of(new GitHubApiClient.RepoInfo("alice/repo1", "main")));
        when(gh.createPullRequest(anyString(), eq("alice/repo1"), anyString(), anyString(), anyString(), anyString()))
            .thenReturn("https://github.com/alice/repo1/pull/7");

        executor.execute(p.getId(), "2.16.3");

        var updated = proposalRepo.findById(p.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ActionProposalStatus.EXECUTED);
        assertThat(updated.getPrUrl()).isEqualTo("https://github.com/alice/repo1/pull/7");
        verify(gh).createBranch(anyString(), eq("alice/repo1"), contains("dev-radar/cve-"), eq("main"));
        verify(gh).putFileContent(anyString(), any());
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && mvn -Dtest=AutoPrExecutorTest test
```

- [ ] **Step 3: Implement `AutoPrExecutor.java`**

```java
package com.devradar.action;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.ActionProposal;
import com.devradar.domain.ActionProposalStatus;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.github.GitHubFileMutation;
import com.devradar.repository.ActionProposalRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
public class AutoPrExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AutoPrExecutor.class);
    private final ObjectMapper json = new ObjectMapper();

    private final GitHubApiClient gh;
    private final ActionProposalRepository proposalRepo;
    private final UserGithubIdentityRepository identityRepo;
    private final TokenEncryptor encryptor;

    public AutoPrExecutor(GitHubApiClient gh, ActionProposalRepository proposalRepo,
                          UserGithubIdentityRepository identityRepo, TokenEncryptor encryptor) {
        this.gh = gh;
        this.proposalRepo = proposalRepo;
        this.identityRepo = identityRepo;
        this.encryptor = encryptor;
    }

    @Transactional
    public void execute(Long proposalId, String fixVersion) {
        ActionProposal p = proposalRepo.findById(proposalId).orElseThrow();
        if (p.getStatus() != ActionProposalStatus.PROPOSED) {
            throw new IllegalStateException("proposal not in PROPOSED state: " + p.getStatus());
        }
        try {
            JsonNode payload = json.readTree(p.getPayload());
            String repo = payload.get("repo").asText();
            String filePath = payload.get("file_path").asText();
            String pkg = payload.get("package").asText();
            String currentVersion = payload.get("current_version").asText();
            String ghsaId = payload.get("ghsa_id").asText();

            UserGithubIdentity identity = identityRepo.findById(p.getUserId()).orElseThrow();
            String token = encryptor.decrypt(identity.getAccessTokenEncrypted());

            // Pick the default branch from the repo info
            String defaultBranch = gh.listRepos(token).stream()
                .filter(r -> r.fullName().equals(repo))
                .findFirst()
                .map(GitHubApiClient.RepoInfo::defaultBranch)
                .orElse("main");

            // Fetch current file content + sha (sha may have moved since the proposal was recorded)
            GitHubApiClient.FileContent current = gh.getFileContent(token, repo, filePath, null);
            String newText = current.text().replace(currentVersion, fixVersion);
            if (newText.equals(current.text())) {
                throw new RuntimeException("no occurrence of version " + currentVersion + " in " + filePath);
            }

            String branch = "dev-radar/cve-" + ghsaId.toLowerCase().replaceAll("[^a-z0-9-]", "-");
            gh.createBranch(token, repo, branch, defaultBranch);

            gh.putFileContent(token, new GitHubFileMutation(
                repo, filePath,
                Base64.getEncoder().encodeToString(newText.getBytes(StandardCharsets.UTF_8)),
                current.sha(),
                "chore(security): bump " + pkg + " to " + fixVersion + " (fixes " + ghsaId + ")",
                branch
            ));

            String prTitle = "chore(security): bump " + pkg + " to " + fixVersion + " (fixes " + ghsaId + ")";
            String prBody = "Automated PR proposed by Dev Radar.\n\nFixes [" + ghsaId + "](https://github.com/advisories/" + ghsaId + ").\n\nDiff: bumps `" + pkg + "` from `" + currentVersion + "` to `" + fixVersion + "` in `" + filePath + "`.";
            String prUrl = gh.createPullRequest(token, repo, prTitle, prBody, branch, defaultBranch);

            p.setStatus(ActionProposalStatus.EXECUTED);
            p.setPrUrl(prUrl);
            proposalRepo.save(p);
            LOG.info("auto-pr executed proposal={} pr_url={}", proposalId, prUrl);
        } catch (Exception e) {
            LOG.error("auto-pr failed proposal={}: {}", proposalId, e.toString(), e);
            p.setStatus(ActionProposalStatus.FAILED);
            p.setFailureReason(e.getMessage());
            proposalRepo.save(p);
            throw new RuntimeException("auto-pr failed: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Run — expect 1/1 PASS**

```bash
cd backend && mvn -Dtest=AutoPrExecutorTest test
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/action/AutoPrExecutor.java backend/src/test/java/com/devradar/action/AutoPrExecutorTest.java
git commit -m "feat(action): add AutoPrExecutor (branch + put file + create PR + update proposal)"
```

---

## Task 10: ActionApplicationService + REST endpoints

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/dto/ActionProposalDTO.java`
- Create: `backend/src/main/java/com/devradar/action/application/ActionApplicationService.java`
- Create: `backend/src/main/java/com/devradar/web/rest/ActionResource.java`

- [ ] **Step 1: Create DTO**

```java
// backend/src/main/java/com/devradar/web/rest/dto/ActionProposalDTO.java
package com.devradar.web.rest.dto;
import com.devradar.domain.ActionProposalKind;
import com.devradar.domain.ActionProposalStatus;
import java.time.Instant;
public record ActionProposalDTO(
    Long id, Long radarId, ActionProposalKind kind, String payloadJson,
    ActionProposalStatus status, String prUrl, String failureReason,
    Instant createdAt, Instant updatedAt
) {}
```

- [ ] **Step 2: Create ActionApplicationService**

```java
// backend/src/main/java/com/devradar/action/application/ActionApplicationService.java
package com.devradar.action.application;

import com.devradar.action.AutoPrExecutor;
import com.devradar.domain.ActionProposal;
import com.devradar.domain.ActionProposalStatus;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.repository.ActionProposalRepository;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.ActionProposalDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActionApplicationService {

    private final ActionProposalRepository repo;
    private final AutoPrExecutor executor;

    public ActionApplicationService(ActionProposalRepository repo, AutoPrExecutor executor) {
        this.repo = repo; this.executor = executor;
    }

    public List<ActionProposalDTO> listForRadar(Long radarId) {
        Long uid = currentUserId();
        return repo.findByRadarIdOrderByCreatedAtAsc(radarId).stream()
            .filter(p -> p.getUserId().equals(uid))
            .map(this::toDto)
            .toList();
    }

    public ActionProposalDTO approve(Long proposalId, String fixVersion) {
        Long uid = currentUserId();
        ActionProposal p = repo.findById(proposalId).orElseThrow();
        if (!p.getUserId().equals(uid)) throw new RuntimeException("forbidden");
        executor.execute(proposalId, fixVersion);
        return toDto(repo.findById(proposalId).orElseThrow());
    }

    @Transactional
    public ActionProposalDTO dismiss(Long proposalId) {
        Long uid = currentUserId();
        ActionProposal p = repo.findById(proposalId).orElseThrow();
        if (!p.getUserId().equals(uid)) throw new RuntimeException("forbidden");
        p.setStatus(ActionProposalStatus.DISMISSED);
        return toDto(repo.save(p));
    }

    private Long currentUserId() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return uid;
    }

    private ActionProposalDTO toDto(ActionProposal p) {
        return new ActionProposalDTO(p.getId(), p.getRadarId(), p.getKind(), p.getPayload(),
            p.getStatus(), p.getPrUrl(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
```

- [ ] **Step 3: Create ActionResource**

```java
// backend/src/main/java/com/devradar/web/rest/ActionResource.java
package com.devradar.web.rest;

import com.devradar.action.application.ActionApplicationService;
import com.devradar.web.rest.dto.ActionProposalDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/actions")
public class ActionResource {

    private final ActionApplicationService app;
    public ActionResource(ActionApplicationService app) { this.app = app; }

    @GetMapping("/proposals")
    public List<ActionProposalDTO> proposalsForRadar(@RequestParam("radar_id") Long radarId) {
        return app.listForRadar(radarId);
    }

    @PostMapping("/{id}/approve")
    public ActionProposalDTO approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return app.approve(id, body.getOrDefault("fix_version", "latest"));
    }

    @DeleteMapping("/{id}")
    public ActionProposalDTO dismiss(@PathVariable Long id) {
        return app.dismiss(id);
    }
}
```

- [ ] **Step 4: Verify compile + commit**

```bash
cd backend && mvn -DskipTests compile
git add backend/src/main/java/com/devradar/web/rest/dto/ActionProposalDTO.java backend/src/main/java/com/devradar/action/application/ActionApplicationService.java backend/src/main/java/com/devradar/web/rest/ActionResource.java
git commit -m "feat(action): add ActionApplicationService + ActionResource REST endpoints"
```

---

## Task 11: SSE event for action proposed

**Files:**
- Create: `backend/src/main/java/com/devradar/radar/event/ActionProposedEvent.java`
- Modify: `backend/src/main/java/com/devradar/radar/RadarEventBus.java`
- Modify: `backend/src/main/java/com/devradar/radar/RadarGenerationService.java`

The current pipeline records `ActionProposal` rows in `CheckRepoForVulnerabilityTool` as a side effect, but doesn't fire an SSE event. After the agent loop ends and themes are persisted, query for any proposals on this radar and fire an SSE event for each one.

- [ ] **Step 1: Create event record**

```java
// backend/src/main/java/com/devradar/radar/event/ActionProposedEvent.java
package com.devradar.radar.event;
public record ActionProposedEvent(Long radarId, Long proposalId, String kind, String payloadJson) {}
```

- [ ] **Step 2: Add publish method to `RadarEventBus.java`**

In `backend/src/main/java/com/devradar/radar/RadarEventBus.java`, add:

```java
public void publishActionProposed(ActionProposedEvent event) { send(event.radarId(), "action.proposed", event); }
```

(Add the import `import com.devradar.radar.event.ActionProposedEvent;` if not auto-imported.)

- [ ] **Step 3: Update `RadarGenerationService.java` to fire ActionProposedEvent**

In `runGeneration`, after `persistAndStream(...)` and before `markReady`, add:

```java
// After agent loop completes, broadcast any action proposals the agent recorded
for (var prop : actionProposalRepo.findByRadarIdOrderByCreatedAtAsc(radarId)) {
    events.publishActionProposed(new com.devradar.radar.event.ActionProposedEvent(
        radarId, prop.getId(), prop.getKind().name(), prop.getPayload()));
}
```

You'll need to inject `ActionProposalRepository` into `RadarGenerationService`'s constructor:

```java
private final com.devradar.repository.ActionProposalRepository actionProposalRepo;

public RadarGenerationService(
    RadarOrchestrator orchestrator,
    RadarService radarService,
    RadarThemeRepository themeRepo,
    RadarThemeItemRepository themeItemRepo,
    AiSummaryCache cache,
    RadarEventBus events,
    com.devradar.repository.ActionProposalRepository actionProposalRepo
) {
    // ... existing assignments ...
    this.actionProposalRepo = actionProposalRepo;
}
```

- [ ] **Step 4: Verify compile + run all tests**

```bash
cd backend && mvn -DskipTests compile
cd backend && mvn test
```
Expected: BUILD SUCCESS, no regressions.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/radar/event/ActionProposedEvent.java backend/src/main/java/com/devradar/radar/RadarEventBus.java backend/src/main/java/com/devradar/radar/RadarGenerationService.java
git commit -m "feat(radar): broadcast action.proposed SSE event after agent loop completes"
```

---

## Task 12: GitHub OAuth callback IT

**File:** `backend/src/test/java/com/devradar/web/rest/AuthGithubResourceIT.java`

This test uses WireMock to stub GitHub's OAuth + user endpoints, then calls the real `/api/auth/github/callback` to verify the full flow: user is created, identity is persisted with encrypted token, JWT is issued.

- [ ] **Step 1: Write the test**

```java
// backend/src/test/java/com/devradar/web/rest/AuthGithubResourceIT.java
package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.devradar.crypto.TokenEncryptor;
import com.devradar.repository.UserGithubIdentityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthGithubResourceIT extends AbstractIntegrationTest {

    static WireMockServer wm;

    @BeforeAll
    static void startWm() {
        wm = new WireMockServer(0);
        wm.start();
    }

    @AfterAll
    static void stopWm() {
        if (wm != null) wm.stop();
    }

    @DynamicPropertySource
    static void overrideGithubUrls(DynamicPropertyRegistry r) {
        r.add("github.oauth.token-url", () -> "http://localhost:" + wm.port() + "/login/oauth/access_token");
        r.add("github.api.base-url", () -> "http://localhost:" + wm.port());
    }

    @Autowired MockMvc mvc;
    @Autowired UserGithubIdentityRepository identityRepo;
    @Autowired TokenEncryptor encryptor;
    @Autowired ObjectMapper json;

    @Test
    void githubStart_redirectsToGithub() throws Exception {
        mvc.perform(get("/api/auth/github/start"))
            .andExpect(status().is3xxRedirection())
            .andExpect(result -> {
                String loc = result.getResponse().getHeader("Location");
                assertThat(loc).contains("client_id=").contains("scope=").contains("state=");
            });
    }

    @Test
    void githubCallback_exchangesCode_createsUserAndIdentity_returnsToken() throws Exception {
        // 1. First hit /start to register a state token
        var startResp = mvc.perform(get("/api/auth/github/start")).andReturn().getResponse();
        String location = startResp.getHeader("Location");
        String state = location.substring(location.indexOf("state=") + 6);

        // 2. Stub GitHub's token + user endpoints
        wm.stubFor(WireMock.post(WireMock.urlPathEqualTo("/login/oauth/access_token"))
            .willReturn(WireMock.okJson("""
                {"access_token":"gho_callback_test","token_type":"bearer","scope":"read:user,repo"}
                """)));
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/user"))
            .willReturn(WireMock.okJson("""
                {"login":"alice","id":7777}
                """)));

        // 3. Hit the callback
        var callbackResp = mvc.perform(get("/api/auth/github/callback")
                .param("code", "the-code")
                .param("state", state))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        // 4. Verify response
        JsonNode body = json.readTree(callbackResp.getContentAsString());
        assertThat(body.get("accessToken").asText()).isNotBlank();

        // 5. Verify identity persisted with ENCRYPTED token (not the raw one)
        var identity = identityRepo.findByGithubUserId(7777L).orElseThrow();
        assertThat(identity.getGithubLogin()).isEqualTo("alice");
        assertThat(identity.getAccessTokenEncrypted()).isNotEqualTo("gho_callback_test");
        assertThat(encryptor.decrypt(identity.getAccessTokenEncrypted())).isEqualTo("gho_callback_test");
        assertThat(identity.getGrantedScopes()).contains("repo");
    }
}
```

- [ ] **Step 2: Run — expect 2/2 PASS**

```bash
cd backend && mvn -Dtest=AuthGithubResourceIT test
```

- [ ] **Step 3: Run full suite to confirm no regressions**

```bash
cd backend && mvn test
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/devradar/web/rest/AuthGithubResourceIT.java
git commit -m "test(auth): IT for GitHub OAuth callback flow with WireMock"
```

---

## Task 13: README + manual smoke documentation

**File:** `backend/README.md`

- [ ] **Step 1: Append a "Plan 4 — GitHub OAuth + Auto-PR" section to `backend/README.md`**

```markdown

## Plan 4 — GitHub OAuth + Auto-PR

Lets users link their GitHub account so the radar agent can scan their repos for vulnerable dependencies and propose a PR with the fix.

### Architecture

| Component | Role |
|---|---|
| `TokenEncryptor` | AES-256-GCM encryption for GitHub access tokens at rest |
| `GitHubOAuthClient` | OAuth code → access_token exchange |
| `GitHubApiClient` | REST wrapper for `/user`, `/user/repos`, `/contents`, branch + PR creation |
| `CheckRepoForVulnerabilityTool` | New agent tool: scans user's repos, records `action_proposals` |
| `AutoPrExecutor` | Branches the user's repo, commits the version bump, opens a PR |
| `ActionApplicationService` | List / approve / dismiss proposals |
| `ActionResource` | REST: `GET /api/actions/proposals`, `POST /{id}/approve`, `DELETE /{id}` |

### New endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/auth/github/start` | 302 redirect to GitHub consent |
| GET | `/api/auth/github/callback` | OAuth callback; exchanges code, creates/links user, returns JWT |
| DELETE | `/api/users/me/github-link` | Unlink GitHub identity (deferred to a future patch) |
| GET | `/api/actions/proposals?radar_id=X` | List proposals for a radar |
| POST | `/api/actions/{id}/approve` | Body: `{"fix_version":"2.16.3"}` — opens the real PR via GitHub API |
| DELETE | `/api/actions/{id}` | Dismiss proposal |

### Configuration

| Property / env var | Default | Description |
|---|---|---|
| `GITHUB_OAUTH_CLIENT_ID` | empty | Required for OAuth flow |
| `GITHUB_OAUTH_CLIENT_SECRET` | empty | Required for OAuth flow |
| `GITHUB_OAUTH_REDIRECT_URI` | `http://localhost:8080/api/auth/github/callback` | Match the value registered in your GitHub OAuth App |
| `GITHUB_TOKEN_ENCRYPTION_KEY` | dev key in `application.yml` | Base64-encoded 32 bytes for AES-256. Generate with `openssl rand -base64 32`. |

### Setting up the OAuth app

1. Go to https://github.com/settings/developers → "New OAuth App"
2. Application name: `Dev Radar (local)`
3. Homepage URL: `http://localhost:8080`
4. Authorization callback URL: `http://localhost:8080/api/auth/github/callback`
5. After creating: copy Client ID + generate Client Secret
6. Set in your `.env`:
   ```bash
   GITHUB_OAUTH_CLIENT_ID=Iv1.abcdef...
   GITHUB_OAUTH_CLIENT_SECRET=...
   GITHUB_TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)
   ```

### Agent flow (when AI is configured)

1. Ingestion brings in a GHSA item (e.g., `jackson-databind` CVE)
2. User generates a radar
3. Agent calls `checkRepoForVulnerability(package="jackson-databind", version_pattern="2.16.2", ghsa_id="GHSA-...")`
4. Tool scans the user's repos for `pom.xml`/`package.json` containing the version → finds `creeno-backend/pom.xml` matches
5. Tool persists an `action_proposal` row + returns the affected file to the agent
6. After the agent loop completes, the SSE event `action.proposed` fires for each new proposal
7. User clicks "Open PR" in the UI → POST `/api/actions/{id}/approve` with `{"fix_version":"2.16.3"}`
8. `AutoPrExecutor` creates branch `dev-radar/cve-ghsa-...`, commits the file with the version bump, opens a PR; `pr_url` recorded on the proposal
```

- [ ] **Step 2: Final verification**

```bash
cd backend && mvn -B verify
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/README.md
git commit -m "docs(backend): document Plan 4 GitHub OAuth + Auto-PR architecture + flow"
```

---

## Plan 4 Done — End-to-End Verification

- [ ] **Step 1: Run the full test suite**

```bash
cd backend && mvn -B verify
```
Expected: BUILD SUCCESS, P1 + P2 + P3 + P4 tests all pass.

- [ ] **Step 2: Optional manual smoke (requires real GitHub OAuth app + AI key)**

The README's local OAuth-app setup walks you through creating the GitHub OAuth app. With it configured + an AI provider (Anthropic, Gemini, or demo), you can:

```bash
# 1. Start the app with Gemini
DB_HOST_PORT=3307 docker compose up -d
set -a; source .env; set +a
mvn spring-boot:run -Dspring-boot.run.profiles=gemini

# 2. Open this in a browser to start GitHub OAuth
http://localhost:8080/api/auth/github/start

# 3. After consent, GitHub redirects back; you'll get a JWT in JSON. Save it.

# 4. Pick interests including "security"
TOKEN=...
curl -X PUT localhost:8080/api/users/me/interests -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"tagSlugs":["spring_boot","security","mysql"]}'

# 5. Generate radar; if the agent finds a CVE matching your repos, watch the SSE stream for action.proposed events
RADAR_ID=$(curl -s -X POST localhost:8080/api/radars -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
curl -N -H "Authorization: Bearer $TOKEN" "localhost:8080/api/radars/$RADAR_ID/stream"

# 6. List proposals
curl -s "localhost:8080/api/actions/proposals?radar_id=$RADAR_ID" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 7. Approve one — opens a real PR in your repo
curl -X POST "localhost:8080/api/actions/PROPOSAL_ID/approve" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"fix_version":"2.16.3"}'
```

The PR URL appears in the proposal response. Open it in GitHub — that's the agent's actual side effect.

Plan 4 complete. Move to **Plan 5: MCP Server** (expose radar as MCP tools queryable from Claude Desktop / Cursor).
