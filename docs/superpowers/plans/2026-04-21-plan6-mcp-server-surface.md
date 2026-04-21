# Dev Radar — Plan 6: MCP Server Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose Dev Radar's core data (latest radar, user interests, recent items) plus one write tool (`propose_pr_for_cve`) over the Model Context Protocol so MCP clients (Claude Desktop, Cursor) can query Dev Radar using scoped per-user API keys.

**Architecture:** Add `spring-ai-starter-mcp-server-webmvc` which wraps the official `io.modelcontextprotocol:java-sdk`. Tool methods are annotated with `@Tool` on Spring beans under `com.devradar.mcp`; each is a thin delegate over an existing application service. Authentication is a new `ApiKeyAuthenticationFilter` that validates `devr_`-prefixed bearer tokens against SHA-256 hashes stored in a new `user_api_keys` table; scope (`READ`/`WRITE`) is enforced via a `@RequireScope` annotation + AOP aspect on mutation-capable tools.

**Tech Stack:** Spring Boot 3.5 + Java 21, Spring AI MCP starter, Liquibase, JPA, JUnit 5 + Testcontainers + MockMvc, Mockito.

**Spec reference:** `docs/superpowers/specs/2026-04-21-mcp-server-surface-design.md`.

---

## File Structure

```
backend/
├── pom.xml                                                            (modify: + spring-ai-starter-mcp-server-webmvc)
├── src/main/
│   ├── java/com/devradar/
│   │   ├── config/
│   │   │   ├── SecurityConfig.java                                    (modify)
│   │   │   └── McpConfig.java                                         (new)
│   │   ├── domain/
│   │   │   ├── UserApiKey.java                                        (new)
│   │   │   └── ApiKeyScope.java                                       (new)
│   │   ├── repository/
│   │   │   ├── UserApiKeyRepository.java                              (new)
│   │   │   └── SourceItemRepository.java                              (modify: + findRecentByUserInterests)
│   │   ├── security/
│   │   │   ├── ApiKeyAuthenticationFilter.java                        (new)
│   │   │   ├── ApiKeyAuthenticationToken.java                         (new)
│   │   │   ├── ApiKeyPrincipal.java                                   (new)
│   │   │   ├── ApiKeyHasher.java                                      (new)
│   │   │   ├── ApiKeyGenerator.java                                   (new)
│   │   │   └── SecurityUtils.java                                     (modify: + getCurrentApiKeyScope)
│   │   ├── apikey/
│   │   │   ├── ApiKeyService.java                                     (new)
│   │   │   ├── ApiKeyUsedEvent.java                                   (new)
│   │   │   ├── ApiKeyUsedListener.java                                (new)
│   │   │   └── application/
│   │   │       └── ApiKeyApplicationService.java                      (new)
│   │   ├── mcp/
│   │   │   ├── RequireScope.java                                      (new)
│   │   │   ├── RequireScopeAspect.java                                (new)
│   │   │   ├── McpScopeException.java                                 (new)
│   │   │   ├── RadarMcpTools.java                                     (new)
│   │   │   ├── InterestMcpTools.java                                  (new)
│   │   │   ├── RecentItemsMcpTools.java                               (new)
│   │   │   ├── ActionMcpTools.java                                    (new)
│   │   │   └── dto/
│   │   │       ├── RadarMcpDTO.java                                   (new)
│   │   │       ├── ThemeMcpDTO.java                                   (new)
│   │   │       ├── CitationMcpDTO.java                                (new)
│   │   │       ├── InterestMcpDTO.java                                (new)
│   │   │       └── RecentItemMcpDTO.java                              (new)
│   │   ├── radar/application/RadarApplicationService.java             (modify: + getLatestForUser)
│   │   └── web/rest/
│   │       ├── ApiKeyResource.java                                    (new)
│   │       └── dto/
│   │           ├── ApiKeyCreateRequest.java                           (new)
│   │           ├── ApiKeyCreateResponse.java                          (new)
│   │           └── ApiKeySummaryDTO.java                              (new)
│   └── resources/
│       ├── application.yml                                            (modify)
│       └── db/changelog/
│           ├── db.changelog-master.xml                                (modify)
│           └── 012-api-keys-schema.xml                                (new)
└── src/test/java/com/devradar/
    ├── apikey/ApiKeyServiceTest.java                                  (new)
    ├── security/ApiKeyAuthenticationFilterTest.java                   (new)
    ├── mcp/
    │   ├── RadarMcpToolsIT.java                                       (new)
    │   ├── ActionMcpToolsScopeIT.java                                 (new)
    │   └── RecentItemsMcpToolsIT.java                                 (new)
    └── web/rest/ApiKeyResourceIT.java                                 (new)
```

**Implementation order:** dependency + migration → domain + repo → security filter + principal → api-key service + REST endpoints → MCP tools (one at a time, read tools first) → scope enforcement + write tool → end-to-end MCP IT.

---

## Task 1: Add Spring AI MCP Starter Dependency + YAML Config

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`

- [ ] **Step 1: Add Spring AI BOM and MCP server starter to pom.xml**

In `backend/pom.xml`, add the Spring AI BOM in a new `<dependencyManagement>` block after `</properties>`:

```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>1.0.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

Then add the starter dependency inside the `<dependencies>` block, after the actuator dependency:

```xml
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
        </dependency>
```

- [ ] **Step 2: Verify build still compiles**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Configure MCP server in application.yml**

In `backend/src/main/resources/application.yml`, add a new block at the top level (after the existing `github:` block):

```yaml
spring:
  ai:
    mcp:
      server:
        name: devradar
        version: 1.0.0
        type: SYNC
        sse-message-endpoint: /mcp/message
        sse-endpoint: /mcp/sse
```

Leave `spring.application.name: devradar-backend` at the top alone — note that the `spring:` key will now merge; you must add the `ai:` block under the existing top-level `spring:` key, not a duplicate.

- [ ] **Step 4: Mirror MCP config in application-test.yml**

In `backend/src/test/resources/application-test.yml`, add under the existing top-level `spring:` key:

```yaml
  ai:
    mcp:
      server:
        name: devradar
        version: 1.0.0
        type: SYNC
        sse-message-endpoint: /mcp/message
        sse-endpoint: /mcp/sse
```

- [ ] **Step 5: Verify tests still pass**

Run: `cd backend && mvn test`
Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/test/resources/application-test.yml
git commit -m "chore(mcp): add spring-ai-starter-mcp-server-webmvc and server config"
```

---

## Task 2: Liquibase Migration — user_api_keys Table

**Files:**
- Create: `backend/src/main/resources/db/changelog/012-api-keys-schema.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create the migration**

Create `backend/src/main/resources/db/changelog/012-api-keys-schema.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="012-01" author="devradar">
        <createTable tableName="user_api_keys">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="user_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_user_api_keys_user"
                             references="users(id)"/>
            </column>
            <column name="name" type="VARCHAR(100)">
                <constraints nullable="false"/>
            </column>
            <column name="key_hash" type="VARCHAR(128)">
                <constraints nullable="false" unique="true" uniqueConstraintName="uk_user_api_keys_key_hash"/>
            </column>
            <column name="key_prefix" type="VARCHAR(16)">
                <constraints nullable="false"/>
            </column>
            <column name="scope" type="VARCHAR(10)">
                <constraints nullable="false"/>
            </column>
            <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
            <column name="last_used_at" type="TIMESTAMP"/>
            <column name="revoked_at" type="TIMESTAMP"/>
        </createTable>
        <createIndex tableName="user_api_keys" indexName="idx_user_api_keys_user_active">
            <column name="user_id"/>
            <column name="revoked_at"/>
        </createIndex>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Register the changelog in the master**

In `backend/src/main/resources/db/changelog/db.changelog-master.xml`, add the include after the existing `011` line:

```xml
    <include file="db/changelog/012-api-keys-schema.xml"/>
```

- [ ] **Step 3: Verify migration runs**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS (Liquibase runs on next app start or test).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/012-api-keys-schema.xml backend/src/main/resources/db/changelog/db.changelog-master.xml
git commit -m "feat(apikey): add user_api_keys Liquibase migration"
```

---

## Task 3: Domain Entity + Enum + Repository (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/domain/ApiKeyScope.java`
- Create: `backend/src/main/java/com/devradar/domain/UserApiKey.java`
- Create: `backend/src/main/java/com/devradar/repository/UserApiKeyRepository.java`
- Create: `backend/src/test/java/com/devradar/repository/UserApiKeyRepositoryIT.java`

- [ ] **Step 1: Write the failing repository IT**

Create `backend/src/test/java/com/devradar/repository/UserApiKeyRepositoryIT.java`:

```java
package com.devradar.repository;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.User;
import com.devradar.domain.UserApiKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserApiKeyRepositoryIT extends AbstractIntegrationTest {

    @Autowired UserApiKeyRepository repo;
    @Autowired UserRepository userRepo;

    @Test
    void findsByHashOnlyWhenNotRevoked() {
        User u = new User();
        u.setEmail("apikey1@test.com");
        u.setDisplayName("Api Tester");
        u.setPasswordHash("hash");
        u.setActive(true);
        u = userRepo.save(u);

        UserApiKey active = new UserApiKey();
        active.setUserId(u.getId());
        active.setName("Cursor");
        active.setKeyHash("hash-active");
        active.setKeyPrefix("devr_aaa");
        active.setScope(ApiKeyScope.READ);
        repo.save(active);

        UserApiKey revoked = new UserApiKey();
        revoked.setUserId(u.getId());
        revoked.setName("Old");
        revoked.setKeyHash("hash-revoked");
        revoked.setKeyPrefix("devr_bbb");
        revoked.setScope(ApiKeyScope.READ);
        revoked.setRevokedAt(Instant.now());
        repo.save(revoked);

        assertThat(repo.findByKeyHashAndRevokedAtIsNull("hash-active")).isPresent();
        assertThat(repo.findByKeyHashAndRevokedAtIsNull("hash-revoked")).isEmpty();
    }

    @Test
    void listsActiveKeysForUserOrderedByCreatedAtDesc() {
        User u = new User();
        u.setEmail("apikey2@test.com");
        u.setDisplayName("Api Tester 2");
        u.setPasswordHash("hash");
        u.setActive(true);
        u = userRepo.save(u);

        UserApiKey k1 = new UserApiKey();
        k1.setUserId(u.getId());
        k1.setName("First");
        k1.setKeyHash("h1");
        k1.setKeyPrefix("devr_111");
        k1.setScope(ApiKeyScope.READ);
        repo.save(k1);

        UserApiKey k2 = new UserApiKey();
        k2.setUserId(u.getId());
        k2.setName("Second");
        k2.setKeyHash("h2");
        k2.setKeyPrefix("devr_222");
        k2.setScope(ApiKeyScope.WRITE);
        repo.save(k2);

        var active = repo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(u.getId());
        assertThat(active).hasSize(2);
        assertThat(active.get(0).getName()).isEqualTo("Second");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=UserApiKeyRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (`UserApiKey`, `ApiKeyScope`, `UserApiKeyRepository` do not exist).

- [ ] **Step 3: Create ApiKeyScope enum**

Create `backend/src/main/java/com/devradar/domain/ApiKeyScope.java`:

```java
package com.devradar.domain;

public enum ApiKeyScope {
    READ,
    WRITE
}
```

- [ ] **Step 4: Create UserApiKey entity**

Create `backend/src/main/java/com/devradar/domain/UserApiKey.java`:

```java
package com.devradar.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_api_keys")
public class UserApiKey {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "key_hash", nullable = false, length = 128, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ApiKeyScope scope;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public ApiKeyScope getScope() { return scope; }
    public void setScope(ApiKeyScope scope) { this.scope = scope; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
```

- [ ] **Step 5: Create UserApiKeyRepository**

Create `backend/src/main/java/com/devradar/repository/UserApiKeyRepository.java`:

```java
package com.devradar.repository;

import com.devradar.domain.UserApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserApiKeyRepository extends JpaRepository<UserApiKey, Long> {
    Optional<UserApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);
    List<UserApiKey> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=UserApiKeyRepositoryIT`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/devradar/domain/ApiKeyScope.java backend/src/main/java/com/devradar/domain/UserApiKey.java backend/src/main/java/com/devradar/repository/UserApiKeyRepository.java backend/src/test/java/com/devradar/repository/UserApiKeyRepositoryIT.java
git commit -m "feat(apikey): add UserApiKey entity, ApiKeyScope enum, and repository"
```

---

## Task 4: ApiKeyGenerator + ApiKeyHasher (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/security/ApiKeyGenerator.java`
- Create: `backend/src/main/java/com/devradar/security/ApiKeyHasher.java`
- Create: `backend/src/test/java/com/devradar/security/ApiKeyGeneratorTest.java`
- Create: `backend/src/test/java/com/devradar/security/ApiKeyHasherTest.java`

- [ ] **Step 1: Write the failing ApiKeyGenerator test**

Create `backend/src/test/java/com/devradar/security/ApiKeyGeneratorTest.java`:

```java
package com.devradar.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator gen = new ApiKeyGenerator();

    @Test
    void generatesKeyWithDevrPrefixAnd32BodyChars() {
        String key = gen.generate();
        assertThat(key).startsWith("devr_");
        assertThat(key.length()).isEqualTo(37); // "devr_" (5) + 32
    }

    @Test
    void generatesDistinctKeys() {
        String a = gen.generate();
        String b = gen.generate();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void prefixReturnsFirst8Characters() {
        String key = "devr_abcdefghijklmnopqrstuvwxyz012345";
        assertThat(gen.prefix(key)).isEqualTo("devr_abc");
    }
}
```

- [ ] **Step 2: Write the failing ApiKeyHasher test**

Create `backend/src/test/java/com/devradar/security/ApiKeyHasherTest.java`:

```java
package com.devradar.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    private final ApiKeyHasher hasher = new ApiKeyHasher();

    @Test
    void hashesToSha256Hex() {
        String out = hasher.hash("devr_test");
        // sha256("devr_test") hex
        assertThat(out).isEqualTo("1b3caa571c2b4b22c9ef56e1aa5ab4a5e4f2f43a0c77e65a8a2d4d4eee84bfc2");
    }

    @Test
    void hashIsDeterministic() {
        assertThat(hasher.hash("same")).isEqualTo(hasher.hash("same"));
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        assertThat(hasher.hash("a")).isNotEqualTo(hasher.hash("b"));
    }
}
```

Note: if the expected hash value in `hashesToSha256Hex` differs from your SHA-256 implementation, recompute with `echo -n "devr_test" | shasum -a 256` on the command line and paste the output into the assertion.

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest="ApiKeyGeneratorTest,ApiKeyHasherTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (classes not found).

- [ ] **Step 4: Implement ApiKeyGenerator**

Create `backend/src/main/java/com/devradar/security/ApiKeyGenerator.java`:

```java
package com.devradar.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ApiKeyGenerator {

    public static final String PREFIX = "devr_";
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int BODY_LENGTH = 32;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(PREFIX.length() + BODY_LENGTH);
        sb.append(PREFIX);
        for (int i = 0; i < BODY_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public String prefix(String rawKey) {
        if (rawKey == null || rawKey.length() < 8) return rawKey;
        return rawKey.substring(0, 8);
    }
}
```

- [ ] **Step 5: Implement ApiKeyHasher**

Create `backend/src/main/java/com/devradar/security/ApiKeyHasher.java`:

```java
package com.devradar.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class ApiKeyHasher {

    public String hash(String rawKey) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] bytes = d.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest="ApiKeyGeneratorTest,ApiKeyHasherTest"`
Expected: PASS. If `ApiKeyHasherTest.hashesToSha256Hex` fails due to a different SHA-256 hex, update the assertion to match the actual output (the hash is deterministic, so the value is whatever the JVM produces).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/devradar/security/ApiKeyGenerator.java backend/src/main/java/com/devradar/security/ApiKeyHasher.java backend/src/test/java/com/devradar/security/ApiKeyGeneratorTest.java backend/src/test/java/com/devradar/security/ApiKeyHasherTest.java
git commit -m "feat(apikey): add ApiKeyGenerator and ApiKeyHasher"
```

---

## Task 5: ApiKeyPrincipal + ApiKeyAuthenticationToken + SecurityUtils extension

**Files:**
- Create: `backend/src/main/java/com/devradar/security/ApiKeyPrincipal.java`
- Create: `backend/src/main/java/com/devradar/security/ApiKeyAuthenticationToken.java`
- Modify: `backend/src/main/java/com/devradar/security/SecurityUtils.java`

- [ ] **Step 1: Create ApiKeyPrincipal record**

Create `backend/src/main/java/com/devradar/security/ApiKeyPrincipal.java`:

```java
package com.devradar.security;

import com.devradar.domain.ApiKeyScope;

public record ApiKeyPrincipal(Long userId, Long keyId, ApiKeyScope scope) {}
```

- [ ] **Step 2: Create ApiKeyAuthenticationToken**

Create `backend/src/main/java/com/devradar/security/ApiKeyAuthenticationToken.java`:

```java
package com.devradar.security;

import com.devradar.domain.ApiKeyScope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final ApiKeyPrincipal principal;

    public ApiKeyAuthenticationToken(ApiKeyPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("SCOPE_" + principal.scope().name())));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return principal; }

    public ApiKeyScope getScope() { return principal.scope(); }
    public Long getUserId() { return principal.userId(); }
    public Long getKeyId() { return principal.keyId(); }
}
```

- [ ] **Step 3: Modify SecurityUtils to support API key auth**

Replace the body of `backend/src/main/java/com/devradar/security/SecurityUtils.java` with:

```java
package com.devradar.security;

import com.devradar.domain.ApiKeyScope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth instanceof ApiKeyAuthenticationToken api) return api.getUserId();
        if (auth.getDetails() instanceof JwtUserDetails d) return d.userId();
        return null;
    }

    public static ApiKeyScope getCurrentApiKeyScope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyAuthenticationToken api) return api.getScope();
        return null;
    }
}
```

- [ ] **Step 4: Verify existing tests still pass**

Run: `cd backend && mvn test`
Expected: all tests PASS (no regressions from the SecurityUtils change).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/security/ApiKeyPrincipal.java backend/src/main/java/com/devradar/security/ApiKeyAuthenticationToken.java backend/src/main/java/com/devradar/security/SecurityUtils.java
git commit -m "feat(apikey): add ApiKeyPrincipal, ApiKeyAuthenticationToken, and extend SecurityUtils"
```

---

## Task 6: ApiKeyAuthenticationFilter (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/security/ApiKeyAuthenticationFilter.java`
- Create: `backend/src/test/java/com/devradar/security/ApiKeyAuthenticationFilterTest.java`
- Create: `backend/src/main/java/com/devradar/apikey/ApiKeyUsedEvent.java`

- [ ] **Step 1: Create ApiKeyUsedEvent (used by filter)**

Create `backend/src/main/java/com/devradar/apikey/ApiKeyUsedEvent.java`:

```java
package com.devradar.apikey;

public record ApiKeyUsedEvent(Long keyId) {}
```

- [ ] **Step 2: Write the failing filter test**

Create `backend/src/test/java/com/devradar/security/ApiKeyAuthenticationFilterTest.java`:

```java
package com.devradar.security;

import com.devradar.apikey.ApiKeyUsedEvent;
import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.UserApiKey;
import com.devradar.repository.UserApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyAuthenticationFilterTest {

    UserApiKeyRepository repo;
    ApiKeyHasher hasher;
    ApplicationEventPublisher events;
    FilterChain chain;
    ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        repo = mock(UserApiKeyRepository.class);
        hasher = mock(ApiKeyHasher.class);
        events = mock(ApplicationEventPublisher.class);
        chain = mock(FilterChain.class);
        filter = new ApiKeyAuthenticationFilter(repo, hasher, events);
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsWhenPathNotMcp() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verifyNoInteractions(repo);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsMcpRequestWithoutAuthorization() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/message");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsMcpRequestWithJwtStyleToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/message");
        req.addHeader("Authorization", "Bearer eyJabc.jwt.token");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rejectsMcpRequestWithUnknownKey() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/message");
        req.addHeader("Authorization", "Bearer devr_nope");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(hasher.hash("devr_nope")).thenReturn("hashval");
        when(repo.findByKeyHashAndRevokedAtIsNull("hashval")).thenReturn(Optional.empty());

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void setsAuthenticationAndPublishesEventOnValidKey() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/message");
        req.addHeader("Authorization", "Bearer devr_ok");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        UserApiKey entity = new UserApiKey();
        entity.setId(42L);
        entity.setUserId(7L);
        entity.setScope(ApiKeyScope.WRITE);
        entity.setKeyHash("hashok");

        when(hasher.hash("devr_ok")).thenReturn("hashok");
        when(repo.findByKeyHashAndRevokedAtIsNull("hashok")).thenReturn(Optional.of(entity));

        filter.doFilter(req, resp, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(ApiKeyAuthenticationToken.class);
        ApiKeyAuthenticationToken tok = (ApiKeyAuthenticationToken) auth;
        assertThat(tok.getUserId()).isEqualTo(7L);
        assertThat(tok.getKeyId()).isEqualTo(42L);
        assertThat(tok.getScope()).isEqualTo(ApiKeyScope.WRITE);

        ArgumentCaptor<ApiKeyUsedEvent> evt = ArgumentCaptor.forClass(ApiKeyUsedEvent.class);
        verify(events).publishEvent(evt.capture());
        assertThat(evt.getValue().keyId()).isEqualTo(42L);
        verify(chain).doFilter(req, resp);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=ApiKeyAuthenticationFilterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (`ApiKeyAuthenticationFilter` not found).

- [ ] **Step 4: Implement ApiKeyAuthenticationFilter**

Create `backend/src/main/java/com/devradar/security/ApiKeyAuthenticationFilter.java`:

```java
package com.devradar.security;

import com.devradar.apikey.ApiKeyUsedEvent;
import com.devradar.domain.UserApiKey;
import com.devradar.repository.UserApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final UserApiKeyRepository repo;
    private final ApiKeyHasher hasher;
    private final ApplicationEventPublisher events;

    public ApiKeyAuthenticationFilter(UserApiKeyRepository repo,
                                       ApiKeyHasher hasher,
                                       ApplicationEventPublisher events) {
        this.repo = repo;
        this.hasher = hasher;
        this.events = events;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        if (!req.getRequestURI().startsWith("/mcp/")) {
            chain.doFilter(req, resp);
            return;
        }

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String raw = header.substring(BEARER.length()).trim();
        if (!raw.startsWith(ApiKeyGenerator.PREFIX)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String hash = hasher.hash(raw);
        Optional<UserApiKey> keyOpt = repo.findByKeyHashAndRevokedAtIsNull(hash);
        if (keyOpt.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        UserApiKey key = keyOpt.get();
        ApiKeyPrincipal principal = new ApiKeyPrincipal(key.getUserId(), key.getId(), key.getScope());
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken(principal));
        events.publishEvent(new ApiKeyUsedEvent(key.getId()));

        chain.doFilter(req, resp);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=ApiKeyAuthenticationFilterTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/security/ApiKeyAuthenticationFilter.java backend/src/test/java/com/devradar/security/ApiKeyAuthenticationFilterTest.java backend/src/main/java/com/devradar/apikey/ApiKeyUsedEvent.java
git commit -m "feat(apikey): add ApiKeyAuthenticationFilter with event publishing"
```

---

## Task 7: Register ApiKeyAuthenticationFilter in SecurityConfig + ApiKeyUsedListener

**Files:**
- Modify: `backend/src/main/java/com/devradar/config/SecurityConfig.java`
- Create: `backend/src/main/java/com/devradar/apikey/ApiKeyUsedListener.java`

- [ ] **Step 1: Create ApiKeyUsedListener**

Create `backend/src/main/java/com/devradar/apikey/ApiKeyUsedListener.java`:

```java
package com.devradar.apikey;

import com.devradar.repository.UserApiKeyRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class ApiKeyUsedListener {

    private final UserApiKeyRepository repo;

    public ApiKeyUsedListener(UserApiKeyRepository repo) {
        this.repo = repo;
    }

    @Async
    @EventListener
    @Transactional
    public void onApiKeyUsed(ApiKeyUsedEvent event) {
        repo.findById(event.keyId()).ifPresent(k -> {
            k.setLastUsedAt(Instant.now());
            repo.save(k);
        });
    }
}
```

- [ ] **Step 2: Modify SecurityConfig to wire in ApiKeyAuthenticationFilter and permit /mcp/**

Replace `backend/src/main/java/com/devradar/config/SecurityConfig.java` entirely with:

```java
package com.devradar.config;

import com.devradar.security.ApiKeyAuthenticationFilter;
import com.devradar.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ApiKeyAuthenticationFilter apiKeyFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ApiKeyAuthenticationFilter apiKeyFilter) {
        this.jwtFilter = jwtFilter;
        this.apiKeyFilter = apiKeyFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(c -> c.disable())
            .httpBasic(h -> h.disable())
            .formLogin(f -> f.disable())
            .logout(l -> l.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/interest-tags/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/api/observability/**").permitAll()
                .requestMatchers("/mcp/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 3: Enable async support for the listener**

Check whether `@EnableAsync` is already declared in the app. Run:

```bash
grep -rn "@EnableAsync" backend/src/main/java
```

If it returns a result, skip this step. If it returns nothing, add `@EnableAsync` to the Spring Boot main application class (likely `backend/src/main/java/com/devradar/DevradarApplication.java`). Open that file and add:

```java
import org.springframework.scheduling.annotation.EnableAsync;
```

Then annotate the class with `@EnableAsync`.

- [ ] **Step 4: Run all tests**

Run: `cd backend && mvn test`
Expected: all tests PASS (no regressions).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/apikey/ApiKeyUsedListener.java backend/src/main/java/com/devradar/config/SecurityConfig.java
git commit -m "feat(apikey): register ApiKeyAuthenticationFilter and async last-used listener"
```

---

## Task 8: ApiKeyService (generate / list / revoke) (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/apikey/ApiKeyService.java`
- Create: `backend/src/test/java/com/devradar/apikey/ApiKeyServiceTest.java`

- [ ] **Step 1: Write the failing ApiKeyService test**

Create `backend/src/test/java/com/devradar/apikey/ApiKeyServiceTest.java`:

```java
package com.devradar.apikey;

import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.UserApiKey;
import com.devradar.repository.UserApiKeyRepository;
import com.devradar.security.ApiKeyGenerator;
import com.devradar.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {

    UserApiKeyRepository repo;
    ApiKeyGenerator gen;
    ApiKeyHasher hasher;
    ApiKeyService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserApiKeyRepository.class);
        gen = mock(ApiKeyGenerator.class);
        hasher = mock(ApiKeyHasher.class);
        service = new ApiKeyService(repo, gen, hasher);
    }

    @Test
    void generateReturnsRawKeyExactlyOnceAndPersistsHashAndPrefix() {
        when(gen.generate()).thenReturn("devr_rawkey12345");
        when(gen.prefix("devr_rawkey12345")).thenReturn("devr_raw");
        when(hasher.hash("devr_rawkey12345")).thenReturn("HASH");
        when(repo.save(any(UserApiKey.class))).thenAnswer(inv -> {
            UserApiKey k = inv.getArgument(0);
            k.setId(99L);
            return k;
        });

        ApiKeyService.GeneratedKey result = service.generate(42L, "Cursor", ApiKeyScope.READ);

        assertThat(result.rawKey()).isEqualTo("devr_rawkey12345");
        assertThat(result.keyPrefix()).isEqualTo("devr_raw");
        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.scope()).isEqualTo(ApiKeyScope.READ);

        verify(repo).save(argThat(k ->
            "HASH".equals(k.getKeyHash()) &&
            "devr_raw".equals(k.getKeyPrefix()) &&
            "Cursor".equals(k.getName()) &&
            42L == k.getUserId() &&
            ApiKeyScope.READ == k.getScope()
        ));
    }

    @Test
    void listReturnsActiveKeysForUser() {
        UserApiKey k = new UserApiKey();
        k.setId(1L);
        k.setUserId(7L);
        k.setName("Cursor");
        k.setKeyPrefix("devr_abc");
        k.setScope(ApiKeyScope.WRITE);
        when(repo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(7L)).thenReturn(List.of(k));

        List<UserApiKey> out = service.list(7L);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getName()).isEqualTo("Cursor");
    }

    @Test
    void revokeSetsRevokedAtWhenCallerOwnsKey() {
        UserApiKey k = new UserApiKey();
        k.setId(1L);
        k.setUserId(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(k));
        when(repo.save(any(UserApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service.revoke(7L, 1L);

        verify(repo).save(argThat(saved -> saved.getRevokedAt() != null));
    }

    @Test
    void revokeThrowsWhenCallerDoesNotOwnKey() {
        UserApiKey k = new UserApiKey();
        k.setId(1L);
        k.setUserId(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(k));

        assertThatThrownBy(() -> service.revoke(99L, 1L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("forbidden");
    }

    @Test
    void revokeThrowsWhenKeyNotFound() {
        when(repo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(7L, 1L))
            .isInstanceOf(RuntimeException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=ApiKeyServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (`ApiKeyService` not found).

- [ ] **Step 3: Implement ApiKeyService**

Create `backend/src/main/java/com/devradar/apikey/ApiKeyService.java`:

```java
package com.devradar.apikey;

import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.UserApiKey;
import com.devradar.repository.UserApiKeyRepository;
import com.devradar.security.ApiKeyGenerator;
import com.devradar.security.ApiKeyHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ApiKeyService {

    private final UserApiKeyRepository repo;
    private final ApiKeyGenerator generator;
    private final ApiKeyHasher hasher;

    public ApiKeyService(UserApiKeyRepository repo, ApiKeyGenerator generator, ApiKeyHasher hasher) {
        this.repo = repo;
        this.generator = generator;
        this.hasher = hasher;
    }

    public record GeneratedKey(Long id, String rawKey, String keyPrefix, ApiKeyScope scope, String name) {}

    @Transactional
    public GeneratedKey generate(Long userId, String name, ApiKeyScope scope) {
        String raw = generator.generate();
        String prefix = generator.prefix(raw);
        String hash = hasher.hash(raw);

        UserApiKey k = new UserApiKey();
        k.setUserId(userId);
        k.setName(name);
        k.setKeyHash(hash);
        k.setKeyPrefix(prefix);
        k.setScope(scope);
        UserApiKey saved = repo.save(k);

        return new GeneratedKey(saved.getId(), raw, prefix, scope, name);
    }

    public List<UserApiKey> list(Long userId) {
        return repo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void revoke(Long userId, Long keyId) {
        UserApiKey k = repo.findById(keyId).orElseThrow(() -> new RuntimeException("not found"));
        if (!k.getUserId().equals(userId)) throw new RuntimeException("forbidden");
        k.setRevokedAt(Instant.now());
        repo.save(k);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=ApiKeyServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devradar/apikey/ApiKeyService.java backend/src/test/java/com/devradar/apikey/ApiKeyServiceTest.java
git commit -m "feat(apikey): add ApiKeyService (generate/list/revoke)"
```

---

## Task 9: ApiKeyApplicationService + REST Endpoints + IT (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/dto/ApiKeyCreateRequest.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/ApiKeyCreateResponse.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/ApiKeySummaryDTO.java`
- Create: `backend/src/main/java/com/devradar/apikey/application/ApiKeyApplicationService.java`
- Create: `backend/src/main/java/com/devradar/web/rest/ApiKeyResource.java`
- Create: `backend/src/test/java/com/devradar/web/rest/ApiKeyResourceIT.java`

- [ ] **Step 1: Write the failing IT**

Create `backend/src/test/java/com/devradar/web/rest/ApiKeyResourceIT.java`:

```java
package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ApiKeyResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void createReturnsRawKeyOnceAndListReturnsSummariesWithoutIt() throws Exception {
        String token = registerAndLogin("apikey-rest@test.com");

        MvcResult create = mvc.perform(post("/api/users/me/api-keys")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cursor\",\"scope\":\"READ\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.startsWith("devr_")))
            .andExpect(jsonPath("$.keyPrefix").exists())
            .andExpect(jsonPath("$.scope").value("READ"))
            .andReturn();

        JsonNode created = json.readTree(create.getResponse().getContentAsString());
        Long keyId = created.get("id").asLong();

        MvcResult list = mvc.perform(get("/api/users/me/api-keys")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Cursor"))
            .andExpect(jsonPath("$[0].scope").value("READ"))
            .andReturn();

        assertThat(list.getResponse().getContentAsString()).doesNotContain("\"key\"");

        mvc.perform(delete("/api/users/me/api-keys/" + keyId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/users/me/api-keys")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createRequiresAuth() throws Exception {
        mvc.perform(post("/api/users/me/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"scope\":\"READ\"}"))
            .andExpect(status().isUnauthorized());
    }

    private String registerAndLogin(String email) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
            "email", email, "password", "Password1!", "displayName", "Api Rest"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        String resp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "Password1!"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("accessToken").asText();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=ApiKeyResourceIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (classes not found or 404s).

- [ ] **Step 3: Create ApiKeyCreateRequest DTO**

Create `backend/src/main/java/com/devradar/web/rest/dto/ApiKeyCreateRequest.java`:

```java
package com.devradar.web.rest.dto;

import com.devradar.domain.ApiKeyScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApiKeyCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull ApiKeyScope scope
) {}
```

- [ ] **Step 4: Create ApiKeyCreateResponse DTO**

Create `backend/src/main/java/com/devradar/web/rest/dto/ApiKeyCreateResponse.java`:

```java
package com.devradar.web.rest.dto;

import com.devradar.domain.ApiKeyScope;

import java.time.Instant;

public record ApiKeyCreateResponse(
    Long id,
    String name,
    ApiKeyScope scope,
    String key,
    String keyPrefix,
    Instant createdAt
) {}
```

- [ ] **Step 5: Create ApiKeySummaryDTO**

Create `backend/src/main/java/com/devradar/web/rest/dto/ApiKeySummaryDTO.java`:

```java
package com.devradar.web.rest.dto;

import com.devradar.domain.ApiKeyScope;

import java.time.Instant;

public record ApiKeySummaryDTO(
    Long id,
    String name,
    ApiKeyScope scope,
    String keyPrefix,
    Instant createdAt,
    Instant lastUsedAt
) {}
```

- [ ] **Step 6: Create ApiKeyApplicationService**

Create `backend/src/main/java/com/devradar/apikey/application/ApiKeyApplicationService.java`:

```java
package com.devradar.apikey.application;

import com.devradar.apikey.ApiKeyService;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.ApiKeyCreateRequest;
import com.devradar.web.rest.dto.ApiKeyCreateResponse;
import com.devradar.web.rest.dto.ApiKeySummaryDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ApiKeyApplicationService {

    private final ApiKeyService service;

    public ApiKeyApplicationService(ApiKeyService service) { this.service = service; }

    public ApiKeyCreateResponse create(ApiKeyCreateRequest req) {
        Long uid = currentUser();
        ApiKeyService.GeneratedKey g = service.generate(uid, req.name(), req.scope());
        return new ApiKeyCreateResponse(g.id(), g.name(), g.scope(), g.rawKey(), g.keyPrefix(), Instant.now());
    }

    public List<ApiKeySummaryDTO> list() {
        Long uid = currentUser();
        return service.list(uid).stream()
            .map(k -> new ApiKeySummaryDTO(k.getId(), k.getName(), k.getScope(),
                k.getKeyPrefix(), k.getCreatedAt(), k.getLastUsedAt()))
            .toList();
    }

    public void revoke(Long keyId) {
        service.revoke(currentUser(), keyId);
    }

    private Long currentUser() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return uid;
    }
}
```

- [ ] **Step 7: Create ApiKeyResource**

Create `backend/src/main/java/com/devradar/web/rest/ApiKeyResource.java`:

```java
package com.devradar.web.rest;

import com.devradar.apikey.application.ApiKeyApplicationService;
import com.devradar.web.rest.dto.ApiKeyCreateRequest;
import com.devradar.web.rest.dto.ApiKeyCreateResponse;
import com.devradar.web.rest.dto.ApiKeySummaryDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/api-keys")
public class ApiKeyResource {

    private final ApiKeyApplicationService service;

    public ApiKeyResource(ApiKeyApplicationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiKeyCreateResponse> create(@Valid @RequestBody ApiKeyCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @GetMapping
    public ResponseEntity<List<ApiKeySummaryDTO>> list() {
        return ResponseEntity.ok(service.list());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable("id") Long id) {
        service.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=ApiKeyResourceIT`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/devradar/apikey/application/ApiKeyApplicationService.java backend/src/main/java/com/devradar/web/rest/ApiKeyResource.java backend/src/main/java/com/devradar/web/rest/dto/ApiKeyCreateRequest.java backend/src/main/java/com/devradar/web/rest/dto/ApiKeyCreateResponse.java backend/src/main/java/com/devradar/web/rest/dto/ApiKeySummaryDTO.java backend/src/test/java/com/devradar/web/rest/ApiKeyResourceIT.java
git commit -m "feat(apikey): add REST endpoints for creating, listing, and revoking keys"
```

---

## Task 10: MCP DTOs + RadarApplicationService.getLatestForUser (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/mcp/dto/RadarMcpDTO.java`
- Create: `backend/src/main/java/com/devradar/mcp/dto/ThemeMcpDTO.java`
- Create: `backend/src/main/java/com/devradar/mcp/dto/CitationMcpDTO.java`
- Create: `backend/src/main/java/com/devradar/mcp/dto/InterestMcpDTO.java`
- Create: `backend/src/main/java/com/devradar/mcp/dto/RecentItemMcpDTO.java`
- Modify: `backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java`
- Create: `backend/src/test/java/com/devradar/radar/application/RadarApplicationServiceLatestIT.java`

- [ ] **Step 1: Create CitationMcpDTO**

Create `backend/src/main/java/com/devradar/mcp/dto/CitationMcpDTO.java`:

```java
package com.devradar.mcp.dto;

public record CitationMcpDTO(String title, String url) {}
```

- [ ] **Step 2: Create ThemeMcpDTO**

Create `backend/src/main/java/com/devradar/mcp/dto/ThemeMcpDTO.java`:

```java
package com.devradar.mcp.dto;

import java.util.List;

public record ThemeMcpDTO(String title, String summary, List<CitationMcpDTO> citations) {}
```

- [ ] **Step 3: Create RadarMcpDTO**

Create `backend/src/main/java/com/devradar/mcp/dto/RadarMcpDTO.java`:

```java
package com.devradar.mcp.dto;

import java.time.Instant;
import java.util.List;

public record RadarMcpDTO(
    Long radarId,
    Instant generatedAt,
    Instant periodStart,
    Instant periodEnd,
    List<ThemeMcpDTO> themes
) {}
```

- [ ] **Step 4: Create InterestMcpDTO**

Create `backend/src/main/java/com/devradar/mcp/dto/InterestMcpDTO.java`:

```java
package com.devradar.mcp.dto;

public record InterestMcpDTO(String slug, String displayName, String category) {}
```

- [ ] **Step 5: Create RecentItemMcpDTO**

Create `backend/src/main/java/com/devradar/mcp/dto/RecentItemMcpDTO.java`:

```java
package com.devradar.mcp.dto;

import java.time.Instant;
import java.util.List;

public record RecentItemMcpDTO(
    String title,
    String url,
    String source,
    Instant postedAt,
    List<String> tags
) {}
```

- [ ] **Step 6: Write the failing IT for getLatestForUser**

Create `backend/src/test/java/com/devradar/radar/application/RadarApplicationServiceLatestIT.java`:

```java
package com.devradar.radar.application;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.User;
import com.devradar.mcp.dto.RadarMcpDTO;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RadarApplicationServiceLatestIT extends AbstractIntegrationTest {

    @Autowired RadarApplicationService appService;
    @Autowired RadarRepository radarRepo;
    @Autowired UserRepository userRepo;

    @Test
    void returnsEmptyWhenUserHasNoReadyRadar() {
        User u = persistUser("latest-none@test.com");
        Optional<RadarMcpDTO> out = appService.getLatestForUser(u.getId());
        assertThat(out).isEmpty();
    }

    @Test
    void returnsLatestReadyRadar() {
        User u = persistUser("latest-some@test.com");
        Radar older = persistRadar(u.getId(), Instant.now().minusSeconds(3600));
        Radar newer = persistRadar(u.getId(), Instant.now());

        Optional<RadarMcpDTO> out = appService.getLatestForUser(u.getId());

        assertThat(out).isPresent();
        assertThat(out.get().radarId()).isEqualTo(newer.getId());
    }

    private User persistUser(String email) {
        User u = new User();
        u.setEmail(email);
        u.setDisplayName("Tester");
        u.setPasswordHash("h");
        u.setActive(true);
        return userRepo.save(u);
    }

    private Radar persistRadar(Long userId, Instant generatedAt) {
        Radar r = new Radar();
        r.setUserId(userId);
        r.setStatus(RadarStatus.READY);
        r.setPeriodStart(generatedAt.minusSeconds(604800));
        r.setPeriodEnd(generatedAt);
        r.setGeneratedAt(generatedAt);
        r.setGenerationMs(1000L);
        r.setTokenCount(100);
        return radarRepo.save(r);
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=RadarApplicationServiceLatestIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (`getLatestForUser` not defined on `RadarApplicationService`).

- [ ] **Step 8: Add getLatestForUser to RadarApplicationService**

In `backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java`, add these imports if not already present:

```java
import com.devradar.domain.RadarStatus;
import com.devradar.domain.RadarTheme;
import com.devradar.mcp.dto.CitationMcpDTO;
import com.devradar.mcp.dto.RadarMcpDTO;
import com.devradar.mcp.dto.ThemeMcpDTO;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
```

Then add this method to the class:

```java
    public Optional<RadarMcpDTO> getLatestForUser(Long userId) {
        var page = radarRepo.findByUserIdOrderByGeneratedAtDesc(
            userId, PageRequest.of(0, 10));
        return page.getContent().stream()
            .filter(r -> r.getStatus() == RadarStatus.READY)
            .findFirst()
            .map(this::toMcp);
    }

    private RadarMcpDTO toMcp(Radar r) {
        var themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(r.getId());
        var themeDtos = themes.stream().map(this::toThemeMcp).toList();
        return new RadarMcpDTO(r.getId(), r.getGeneratedAt(),
            r.getPeriodStart(), r.getPeriodEnd(), themeDtos);
    }

    private ThemeMcpDTO toThemeMcp(RadarTheme t) {
        var rtis = themeItemRepo.findByThemeIdOrderByDisplayOrderAsc(t.getId());
        var citations = rtis.stream()
            .limit(3)
            .map(rti -> sourceItemRepo.findById(rti.getSourceItemId()).orElse(null))
            .filter(java.util.Objects::nonNull)
            .map(si -> new CitationMcpDTO(si.getTitle(), si.getUrl()))
            .toList();
        return new ThemeMcpDTO(t.getTitle(), t.getSummary(), citations);
    }
```

- [ ] **Step 9: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=RadarApplicationServiceLatestIT`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/devradar/mcp/dto/ backend/src/main/java/com/devradar/radar/application/RadarApplicationService.java backend/src/test/java/com/devradar/radar/application/RadarApplicationServiceLatestIT.java
git commit -m "feat(mcp): add MCP DTO records and RadarApplicationService.getLatestForUser"
```

---

## Task 11: SourceItemRepository.findRecentByUserInterests (TDD)

**Files:**
- Modify: `backend/src/main/java/com/devradar/repository/SourceItemRepository.java`
- Create: `backend/src/test/java/com/devradar/repository/SourceItemRepositoryRecentIT.java`

- [ ] **Step 1: Read existing SourceItemRepository to preserve existing methods**

Run: `cat backend/src/main/java/com/devradar/repository/SourceItemRepository.java` and note the current contents. You will add new methods without removing existing ones.

- [ ] **Step 2: Write the failing repository IT**

Create `backend/src/test/java/com/devradar/repository/SourceItemRepositoryRecentIT.java`:

```java
package com.devradar.repository;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceItemRepositoryRecentIT extends AbstractIntegrationTest {

    @Autowired SourceItemRepository items;
    @Autowired SourceRepository sources;
    @Autowired InterestTagRepository tags;
    @Autowired SourceItemTagRepository sitRepo;
    @Autowired UserInterestRepository userInterests;
    @Autowired UserRepository users;

    @Test
    void filtersByUserInterestsAndOptionalTagSlugAndRecency() {
        User u = new User();
        u.setEmail("recent@test.com");
        u.setDisplayName("Recent");
        u.setPasswordHash("h");
        u.setActive(true);
        u = users.save(u);

        Source source = new Source();
        source.setCode("hn");
        source.setDisplayName("HN");
        source.setEnabled(true);
        source = sources.save(source);

        InterestTag java = new InterestTag();
        java.setSlug("java");
        java.setDisplayName("Java");
        java.setCategory(InterestCategory.LANGUAGE);
        java = tags.save(java);

        InterestTag python = new InterestTag();
        python.setSlug("python");
        python.setDisplayName("Python");
        python.setCategory(InterestCategory.LANGUAGE);
        python = tags.save(python);

        userInterests.save(new UserInterest(new UserInterestId(u.getId(), java.getId())));

        SourceItem matching = persistItem(source.getId(), "ext-1",
            "Spring Boot 3.5 released", Instant.now().minus(2, ChronoUnit.DAYS));
        sitRepo.save(new SourceItemTag(matching.getId(), java.getId()));

        SourceItem nonMatching = persistItem(source.getId(), "ext-2",
            "FastAPI release", Instant.now().minus(2, ChronoUnit.DAYS));
        sitRepo.save(new SourceItemTag(nonMatching.getId(), python.getId()));

        SourceItem stale = persistItem(source.getId(), "ext-3",
            "Spring Boot 2.x EOL", Instant.now().minus(60, ChronoUnit.DAYS));
        sitRepo.save(new SourceItemTag(stale.getId(), java.getId()));

        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<SourceItem> out = items.findRecentByUserInterests(u.getId(), since, null, 20);

        assertThat(out).extracting(SourceItem::getExternalId).containsExactly("ext-1");

        List<SourceItem> filtered = items.findRecentByUserInterests(u.getId(), since, "java", 20);
        assertThat(filtered).extracting(SourceItem::getExternalId).containsExactly("ext-1");

        List<SourceItem> noMatch = items.findRecentByUserInterests(u.getId(), since, "python", 20);
        assertThat(noMatch).isEmpty();
    }

    private SourceItem persistItem(Long sourceId, String extId, String title, Instant postedAt) {
        SourceItem si = new SourceItem();
        si.setSourceId(sourceId);
        si.setExternalId(extId);
        si.setUrl("https://example.com/" + extId);
        si.setTitle(title);
        si.setAuthor("tester");
        si.setPostedAt(postedAt);
        return items.save(si);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=SourceItemRepositoryRecentIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (`findRecentByUserInterests` not defined).

- [ ] **Step 4: Add findRecentByUserInterests method**

In `backend/src/main/java/com/devradar/repository/SourceItemRepository.java`, add these imports at the top (inside the `package` declaration):

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
```

Then add these method declarations to the interface, keeping any existing methods:

```java
    @Query(value = """
        SELECT DISTINCT si FROM SourceItem si
          JOIN SourceItemTag sit ON sit.sourceItemId = si.id
          JOIN InterestTag it ON it.id = sit.interestTagId
          JOIN UserInterest ui ON ui.id.interestTagId = it.id AND ui.id.userId = :userId
         WHERE si.postedAt >= :since
           AND (:tagSlug IS NULL OR it.slug = :tagSlug)
         ORDER BY si.postedAt DESC
        """)
    List<SourceItem> findRecentByUserInterestsPaged(
        @Param("userId") Long userId,
        @Param("since") Instant since,
        @Param("tagSlug") String tagSlug,
        org.springframework.data.domain.Pageable pageable);

    default List<SourceItem> findRecentByUserInterests(Long userId, Instant since, String tagSlug, int limit) {
        return findRecentByUserInterestsPaged(userId, since, tagSlug,
            org.springframework.data.domain.PageRequest.of(0, limit));
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=SourceItemRepositoryRecentIT`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/repository/SourceItemRepository.java backend/src/test/java/com/devradar/repository/SourceItemRepositoryRecentIT.java
git commit -m "feat(mcp): add SourceItemRepository.findRecentByUserInterests"
```

---

## Task 12: RequireScope + RequireScopeAspect + McpScopeException

**Files:**
- Create: `backend/src/main/java/com/devradar/mcp/RequireScope.java`
- Create: `backend/src/main/java/com/devradar/mcp/McpScopeException.java`
- Create: `backend/src/main/java/com/devradar/mcp/RequireScopeAspect.java`
- Modify: `backend/pom.xml` (add spring-boot-starter-aop)

- [ ] **Step 1: Add spring-boot-starter-aop to pom.xml**

In `backend/pom.xml`, inside `<dependencies>`, add after the actuator block (and before any test dependencies):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
```

- [ ] **Step 2: Create RequireScope annotation**

Create `backend/src/main/java/com/devradar/mcp/RequireScope.java`:

```java
package com.devradar.mcp;

import com.devradar.domain.ApiKeyScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequireScope {
    ApiKeyScope value();
}
```

- [ ] **Step 3: Create McpScopeException**

Create `backend/src/main/java/com/devradar/mcp/McpScopeException.java`:

```java
package com.devradar.mcp;

public class McpScopeException extends RuntimeException {
    public McpScopeException(String message) { super(message); }
}
```

- [ ] **Step 4: Create RequireScopeAspect**

Create `backend/src/main/java/com/devradar/mcp/RequireScopeAspect.java`:

```java
package com.devradar.mcp;

import com.devradar.domain.ApiKeyScope;
import com.devradar.security.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequireScopeAspect {

    @Around("@annotation(com.devradar.mcp.RequireScope)")
    public Object enforceScope(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        RequireScope anno = sig.getMethod().getAnnotation(RequireScope.class);
        ApiKeyScope required = anno.value();
        ApiKeyScope actual = SecurityUtils.getCurrentApiKeyScope();

        if (actual == null) {
            throw new McpScopeException("No API key scope on request");
        }
        if (required == ApiKeyScope.WRITE && actual != ApiKeyScope.WRITE) {
            throw new McpScopeException("Scope '" + required + "' required but got '" + actual + "'");
        }
        return pjp.proceed();
    }
}
```

- [ ] **Step 5: Verify build**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/devradar/mcp/RequireScope.java backend/src/main/java/com/devradar/mcp/McpScopeException.java backend/src/main/java/com/devradar/mcp/RequireScopeAspect.java
git commit -m "feat(mcp): add RequireScope annotation and enforcement aspect"
```

---

## Task 13: RadarMcpTools + InterestMcpTools + McpConfig (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/mcp/RadarMcpTools.java`
- Create: `backend/src/main/java/com/devradar/mcp/InterestMcpTools.java`
- Create: `backend/src/main/java/com/devradar/config/McpConfig.java`
- Create: `backend/src/test/java/com/devradar/mcp/RadarMcpToolsIT.java`

- [ ] **Step 1: Write the failing IT**

Create `backend/src/test/java/com/devradar/mcp/RadarMcpToolsIT.java`:

```java
package com.devradar.mcp;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.User;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.UserRepository;
import com.devradar.apikey.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class RadarMcpToolsIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RadarRepository radars;
    @Autowired ApiKeyService apiKeys;

    @Test
    void queryRadarToolRequiresApiKey() throws Exception {
        String envelope = json.writeValueAsString(java.util.Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "tools/call",
            "params", java.util.Map.of("name", "query_radar", "arguments", java.util.Map.of())
        ));

        mvc.perform(post("/mcp/message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void queryRadarReturnsLatestRadarWithValidKey() throws Exception {
        User u = new User();
        u.setEmail("mcp-query@test.com");
        u.setDisplayName("Mcp");
        u.setPasswordHash("h");
        u.setActive(true);
        u = users.save(u);

        Radar r = new Radar();
        r.setUserId(u.getId());
        r.setStatus(RadarStatus.READY);
        r.setPeriodStart(Instant.now().minusSeconds(604800));
        r.setPeriodEnd(Instant.now());
        r.setGeneratedAt(Instant.now());
        r.setGenerationMs(1000L);
        r.setTokenCount(100);
        radars.save(r);

        var key = apiKeys.generate(u.getId(), "test-key", ApiKeyScope.READ);

        String envelope = json.writeValueAsString(java.util.Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "tools/call",
            "params", java.util.Map.of("name", "query_radar", "arguments", java.util.Map.of())
        ));

        mvc.perform(post("/mcp/message")
                .header("Authorization", "Bearer " + key.rawKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("radarId")));
    }
}
```

Note: the Spring AI MCP server-webmvc starter registers a POST endpoint at `/mcp/message` for JSON-RPC method calls. If your starter version uses a different path, adjust the test and the YAML config to match (check the `sse-message-endpoint` you set in Task 1).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=RadarMcpToolsIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (tool classes not registered).

- [ ] **Step 3: Create RadarMcpTools**

Create `backend/src/main/java/com/devradar/mcp/RadarMcpTools.java`:

```java
package com.devradar.mcp;

import com.devradar.mcp.dto.RadarMcpDTO;
import com.devradar.radar.application.RadarApplicationService;
import com.devradar.security.SecurityUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class RadarMcpTools {

    private final RadarApplicationService radars;

    public RadarMcpTools(RadarApplicationService radars) { this.radars = radars; }

    @Tool(name = "query_radar",
          description = "Returns the latest READY radar for the authenticated user, or an empty payload if none exist.")
    public RadarMcpDTO queryRadar() {
        Long uid = SecurityUtils.getCurrentUserId();
        return radars.getLatestForUser(uid)
            .orElse(new RadarMcpDTO(null, null, null, null, java.util.List.of()));
    }
}
```

- [ ] **Step 4: Create InterestMcpTools**

Create `backend/src/main/java/com/devradar/mcp/InterestMcpTools.java`:

```java
package com.devradar.mcp;

import com.devradar.mcp.dto.InterestMcpDTO;
import com.devradar.service.application.InterestApplicationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterestMcpTools {

    private final InterestApplicationService interests;

    public InterestMcpTools(InterestApplicationService interests) { this.interests = interests; }

    @Tool(name = "get_user_interests",
          description = "Returns the authenticated user's interest tags.")
    public List<InterestMcpDTO> getUserInterests() {
        return interests.myInterests().stream()
            .map(t -> new InterestMcpDTO(t.slug(), t.displayName(),
                t.category() == null ? null : t.category().name()))
            .toList();
    }
}
```

Note: adjust the field access on the interest DTO to match the actual shape returned by `InterestApplicationService.myInterests()`. If it returns a record `InterestTagResponseDTO(String slug, String displayName, InterestCategory category)`, the above works. If it returns objects with getters (`getSlug()`, `getDisplayName()`), change accordingly. Check the existing `InterestTagResponseDTO.java` to confirm.

- [ ] **Step 5: Create McpConfig to register tools**

Create `backend/src/main/java/com/devradar/config/McpConfig.java`:

```java
package com.devradar.config;

import com.devradar.mcp.InterestMcpTools;
import com.devradar.mcp.RadarMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider devradarTools(RadarMcpTools radarTools,
                                              InterestMcpTools interestTools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(radarTools, interestTools)
            .build();
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=RadarMcpToolsIT`
Expected: PASS. If the test fails with a 404 on `/mcp/message`, check the starter's actual endpoint mapping — newer versions use `/sse` or other paths. Inspect the Spring AI MCP server-webmvc version's `McpServerAutoConfiguration` or run the app and `curl http://localhost:8080/` to see registered mappings.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/devradar/mcp/RadarMcpTools.java backend/src/main/java/com/devradar/mcp/InterestMcpTools.java backend/src/main/java/com/devradar/config/McpConfig.java backend/src/test/java/com/devradar/mcp/RadarMcpToolsIT.java
git commit -m "feat(mcp): add RadarMcpTools and InterestMcpTools with @Tool registration"
```

---

## Task 14: RecentItemsMcpTools (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/mcp/RecentItemsMcpTools.java`
- Create: `backend/src/test/java/com/devradar/mcp/RecentItemsMcpToolsIT.java`
- Modify: `backend/src/main/java/com/devradar/config/McpConfig.java`

- [ ] **Step 1: Write the failing IT**

Create `backend/src/test/java/com/devradar/mcp/RecentItemsMcpToolsIT.java`:

```java
package com.devradar.mcp;

import com.devradar.AbstractIntegrationTest;
import com.devradar.apikey.ApiKeyService;
import com.devradar.domain.*;
import com.devradar.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class RecentItemsMcpToolsIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired SourceRepository sources;
    @Autowired SourceItemRepository items;
    @Autowired SourceItemTagRepository itemTags;
    @Autowired InterestTagRepository tags;
    @Autowired UserInterestRepository userInterests;
    @Autowired ApiKeyService apiKeys;

    @Test
    void getRecentItemsReturnsMatchingItems() throws Exception {
        User u = new User();
        u.setEmail("recent-mcp@test.com");
        u.setDisplayName("Recent");
        u.setPasswordHash("h");
        u.setActive(true);
        u = users.save(u);

        Source src = new Source();
        src.setCode("hn");
        src.setDisplayName("HN");
        src.setEnabled(true);
        src = sources.save(src);

        InterestTag t = new InterestTag();
        t.setSlug("java");
        t.setDisplayName("Java");
        t.setCategory(InterestCategory.LANGUAGE);
        t = tags.save(t);

        userInterests.save(new UserInterest(new UserInterestId(u.getId(), t.getId())));

        SourceItem si = new SourceItem();
        si.setSourceId(src.getId());
        si.setExternalId("r-1");
        si.setUrl("https://example.com/a");
        si.setTitle("Spring Boot release");
        si.setAuthor("tester");
        si.setPostedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        si = items.save(si);
        itemTags.save(new SourceItemTag(si.getId(), t.getId()));

        var key = apiKeys.generate(u.getId(), "recent", ApiKeyScope.READ);

        String envelope = json.writeValueAsString(java.util.Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "tools/call",
            "params", java.util.Map.of("name", "get_recent_items",
                "arguments", java.util.Map.of("days", 7))
        ));

        mvc.perform(post("/mcp/message")
                .header("Authorization", "Bearer " + key.rawKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Spring Boot release")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=RecentItemsMcpToolsIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (tool not registered).

- [ ] **Step 3: Create RecentItemsMcpTools**

Create `backend/src/main/java/com/devradar/mcp/RecentItemsMcpTools.java`:

```java
package com.devradar.mcp;

import com.devradar.domain.SourceItem;
import com.devradar.mcp.dto.RecentItemMcpDTO;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceItemTagRepository;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.InterestTagRepository;
import com.devradar.security.SecurityUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class RecentItemsMcpTools {

    private static final int MAX_DAYS = 30;
    private static final int DEFAULT_DAYS = 7;
    private static final int ITEM_LIMIT = 20;

    private final SourceItemRepository items;
    private final SourceItemTagRepository itemTagRepo;
    private final SourceRepository sources;
    private final InterestTagRepository tags;

    public RecentItemsMcpTools(SourceItemRepository items,
                               SourceItemTagRepository itemTagRepo,
                               SourceRepository sources,
                               InterestTagRepository tags) {
        this.items = items;
        this.itemTagRepo = itemTagRepo;
        this.sources = sources;
        this.tags = tags;
    }

    @Tool(name = "get_recent_items",
          description = "Returns ingested items from the last N days that match the authenticated user's interests. Capped at 20 items.")
    public List<RecentItemMcpDTO> getRecentItems(
        @ToolParam(description = "Number of days to look back (1-30, default 7)", required = false) Integer days,
        @ToolParam(description = "Optional interest tag slug to filter by", required = false) String tagSlug) {

        int n = (days == null) ? DEFAULT_DAYS : Math.max(1, Math.min(days, MAX_DAYS));
        Long uid = SecurityUtils.getCurrentUserId();
        Instant since = Instant.now().minus(n, ChronoUnit.DAYS);

        List<SourceItem> hits = items.findRecentByUserInterests(uid, since,
            (tagSlug == null || tagSlug.isBlank()) ? null : tagSlug, ITEM_LIMIT);

        return hits.stream().map(this::toDto).toList();
    }

    private RecentItemMcpDTO toDto(SourceItem si) {
        String sourceCode = sources.findById(si.getSourceId())
            .map(s -> s.getCode()).orElse("unknown");
        List<String> tagSlugs = itemTagRepo.findBySourceItemId(si.getId()).stream()
            .map(sit -> tags.findById(sit.getInterestTagId())
                .map(it -> it.getSlug()).orElse(null))
            .filter(java.util.Objects::nonNull)
            .toList();
        return new RecentItemMcpDTO(si.getTitle(), si.getUrl(), sourceCode, si.getPostedAt(), tagSlugs);
    }
}
```

Note: if `SourceItemTagRepository` does not already expose `findBySourceItemId(Long)`, add that method to the interface:

```java
    List<SourceItemTag> findBySourceItemId(Long sourceItemId);
```

and add the appropriate import. Check before adding to avoid duplicates.

- [ ] **Step 4: Register RecentItemsMcpTools in McpConfig**

In `backend/src/main/java/com/devradar/config/McpConfig.java`, update the method to include the new tool:

```java
    @Bean
    public ToolCallbackProvider devradarTools(RadarMcpTools radarTools,
                                              InterestMcpTools interestTools,
                                              RecentItemsMcpTools recentTools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(radarTools, interestTools, recentTools)
            .build();
    }
```

Add the import:

```java
import com.devradar.mcp.RecentItemsMcpTools;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=RecentItemsMcpToolsIT`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devradar/mcp/RecentItemsMcpTools.java backend/src/main/java/com/devradar/config/McpConfig.java backend/src/test/java/com/devradar/mcp/RecentItemsMcpToolsIT.java backend/src/main/java/com/devradar/repository/SourceItemTagRepository.java
git commit -m "feat(mcp): add get_recent_items MCP tool with interest-aware filtering"
```

---

## Task 15: ActionMcpTools (WRITE scope) + scope enforcement IT (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/mcp/ActionMcpTools.java`
- Create: `backend/src/test/java/com/devradar/mcp/ActionMcpToolsScopeIT.java`
- Modify: `backend/src/main/java/com/devradar/config/McpConfig.java`
- Modify: `backend/src/main/java/com/devradar/action/application/ActionApplicationService.java`

- [ ] **Step 1: Write the failing scope-enforcement IT**

Create `backend/src/test/java/com/devradar/mcp/ActionMcpToolsScopeIT.java`:

```java
package com.devradar.mcp;

import com.devradar.AbstractIntegrationTest;
import com.devradar.apikey.ApiKeyService;
import com.devradar.domain.*;
import com.devradar.repository.ActionProposalRepository;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ActionMcpToolsScopeIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RadarRepository radars;
    @Autowired ActionProposalRepository proposals;
    @Autowired ApiKeyService apiKeys;

    @Test
    void readScopeKeyCannotCallProposePrForCve() throws Exception {
        User u = new User();
        u.setEmail("mcp-scope@test.com");
        u.setDisplayName("Scope");
        u.setPasswordHash("h");
        u.setActive(true);
        u = users.save(u);

        Radar r = new Radar();
        r.setUserId(u.getId());
        r.setStatus(RadarStatus.READY);
        r.setPeriodStart(Instant.now().minusSeconds(604800));
        r.setPeriodEnd(Instant.now());
        r.setGeneratedAt(Instant.now());
        r.setGenerationMs(1000L);
        r.setTokenCount(100);
        r = radars.save(r);

        ActionProposal p = new ActionProposal();
        p.setUserId(u.getId());
        p.setRadarId(r.getId());
        p.setKind(ActionProposalKind.CVE_FIX_PR);
        p.setPayload("{}");
        p.setStatus(ActionProposalStatus.PROPOSED);
        p = proposals.save(p);

        var readKey = apiKeys.generate(u.getId(), "read-only", ApiKeyScope.READ);

        String envelope = json.writeValueAsString(java.util.Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "tools/call",
            "params", java.util.Map.of("name", "propose_pr_for_cve",
                "arguments", java.util.Map.of("proposalId", p.getId(), "fixVersion", "2.17.0"))
        ));

        mvc.perform(post("/mcp/message")
                .header("Authorization", "Bearer " + readKey.rawKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(envelope))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("scope")));
    }
}
```

Note: Spring AI's MCP starter serializes exceptions thrown from `@Tool` methods into a JSON-RPC error envelope with HTTP 200. We assert the body contains the word "scope" — if the starter wraps errors differently, adjust the assertion to the actual envelope format.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=ActionMcpToolsScopeIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (tool not registered).

- [ ] **Step 3: Add approveForUser overload to ActionApplicationService**

In `backend/src/main/java/com/devradar/action/application/ActionApplicationService.java`, add a new method (keep the existing `approve(Long, String)`):

```java
    public ActionProposalDTO approveForUser(Long userId, Long proposalId, String fixVersion) {
        ActionProposal p = repo.findById(proposalId).orElseThrow();
        if (!p.getUserId().equals(userId)) throw new RuntimeException("forbidden");
        executor.execute(proposalId, fixVersion);
        return toDto(repo.findById(proposalId).orElseThrow());
    }
```

This lets the MCP tool pass an explicit userId (from the API key principal) rather than relying on `SecurityUtils` — which would also work, but an explicit signature is clearer at the boundary.

- [ ] **Step 4: Create ActionMcpTools**

Create `backend/src/main/java/com/devradar/mcp/ActionMcpTools.java`:

```java
package com.devradar.mcp;

import com.devradar.action.application.ActionApplicationService;
import com.devradar.domain.ApiKeyScope;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.ActionProposalDTO;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ActionMcpTools {

    private final ActionApplicationService actions;

    public ActionMcpTools(ActionApplicationService actions) { this.actions = actions; }

    public record ProposePrResult(String status, String prUrl) {}

    @Tool(name = "propose_pr_for_cve",
          description = "Execute a previously proposed CVE-fix PR on the user's GitHub repo. Requires WRITE scope.")
    @RequireScope(ApiKeyScope.WRITE)
    public ProposePrResult proposePrForCve(
        @ToolParam(description = "The ActionProposal ID to approve") Long proposalId,
        @ToolParam(description = "The target fix version to upgrade to") String fixVersion) {

        Long uid = SecurityUtils.getCurrentUserId();
        ActionProposalDTO out = actions.approveForUser(uid, proposalId, fixVersion);
        return new ProposePrResult(out.status().name(), out.prUrl());
    }
}
```

- [ ] **Step 5: Register ActionMcpTools in McpConfig**

In `backend/src/main/java/com/devradar/config/McpConfig.java`, update:

```java
    @Bean
    public ToolCallbackProvider devradarTools(RadarMcpTools radarTools,
                                              InterestMcpTools interestTools,
                                              RecentItemsMcpTools recentTools,
                                              ActionMcpTools actionTools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(radarTools, interestTools, recentTools, actionTools)
            .build();
    }
```

Add import:

```java
import com.devradar.mcp.ActionMcpTools;
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=ActionMcpToolsScopeIT`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/devradar/mcp/ActionMcpTools.java backend/src/main/java/com/devradar/config/McpConfig.java backend/src/main/java/com/devradar/action/application/ActionApplicationService.java backend/src/test/java/com/devradar/mcp/ActionMcpToolsScopeIT.java
git commit -m "feat(mcp): add propose_pr_for_cve WRITE-scoped MCP tool with scope enforcement"
```

---

## Task 16: Observability Counters for MCP + Full Test Suite Verification

**Files:**
- Modify: `backend/src/main/java/com/devradar/mcp/RadarMcpTools.java`
- Modify: `backend/src/main/java/com/devradar/mcp/InterestMcpTools.java`
- Modify: `backend/src/main/java/com/devradar/mcp/RecentItemsMcpTools.java`
- Modify: `backend/src/main/java/com/devradar/mcp/ActionMcpTools.java`
- Modify: `backend/src/main/java/com/devradar/security/ApiKeyAuthenticationFilter.java`

- [ ] **Step 1: Instrument RadarMcpTools with Micrometer**

In `backend/src/main/java/com/devradar/mcp/RadarMcpTools.java`, add these imports:

```java
import io.micrometer.core.instrument.MeterRegistry;
```

Change the constructor to accept `MeterRegistry` and store it, and update the tool body:

```java
    private final RadarApplicationService radars;
    private final MeterRegistry meters;

    public RadarMcpTools(RadarApplicationService radars, MeterRegistry meters) {
        this.radars = radars;
        this.meters = meters;
    }

    @Tool(name = "query_radar",
          description = "Returns the latest READY radar for the authenticated user, or an empty payload if none exist.")
    public RadarMcpDTO queryRadar() {
        try {
            Long uid = SecurityUtils.getCurrentUserId();
            RadarMcpDTO out = radars.getLatestForUser(uid)
                .orElse(new RadarMcpDTO(null, null, null, null, java.util.List.of()));
            meters.counter("mcp.tool.calls", "tool", "query_radar", "status", "success").increment();
            return out;
        } catch (RuntimeException e) {
            meters.counter("mcp.tool.calls", "tool", "query_radar", "status", "error").increment();
            throw e;
        }
    }
```

- [ ] **Step 2: Apply the same instrumentation pattern to InterestMcpTools**

In `backend/src/main/java/com/devradar/mcp/InterestMcpTools.java`:

```java
import io.micrometer.core.instrument.MeterRegistry;
```

```java
    private final InterestApplicationService interests;
    private final MeterRegistry meters;

    public InterestMcpTools(InterestApplicationService interests, MeterRegistry meters) {
        this.interests = interests;
        this.meters = meters;
    }

    @Tool(name = "get_user_interests",
          description = "Returns the authenticated user's interest tags.")
    public List<InterestMcpDTO> getUserInterests() {
        try {
            List<InterestMcpDTO> out = interests.myInterests().stream()
                .map(t -> new InterestMcpDTO(t.slug(), t.displayName(),
                    t.category() == null ? null : t.category().name()))
                .toList();
            meters.counter("mcp.tool.calls", "tool", "get_user_interests", "status", "success").increment();
            return out;
        } catch (RuntimeException e) {
            meters.counter("mcp.tool.calls", "tool", "get_user_interests", "status", "error").increment();
            throw e;
        }
    }
```

- [ ] **Step 3: Instrument RecentItemsMcpTools**

In `backend/src/main/java/com/devradar/mcp/RecentItemsMcpTools.java`, add the `MeterRegistry` field and wrap the method body in try/catch with the same pattern, tagging `tool=get_recent_items`.

```java
import io.micrometer.core.instrument.MeterRegistry;
```

Add to the class fields and constructor (adding `MeterRegistry meters` as the last parameter):

```java
    private final MeterRegistry meters;
```

Wrap `getRecentItems` body:

```java
        try {
            int n = (days == null) ? DEFAULT_DAYS : Math.max(1, Math.min(days, MAX_DAYS));
            Long uid = SecurityUtils.getCurrentUserId();
            Instant since = Instant.now().minus(n, ChronoUnit.DAYS);
            List<SourceItem> hits = items.findRecentByUserInterests(uid, since,
                (tagSlug == null || tagSlug.isBlank()) ? null : tagSlug, ITEM_LIMIT);
            List<RecentItemMcpDTO> out = hits.stream().map(this::toDto).toList();
            meters.counter("mcp.tool.calls", "tool", "get_recent_items", "status", "success").increment();
            return out;
        } catch (RuntimeException e) {
            meters.counter("mcp.tool.calls", "tool", "get_recent_items", "status", "error").increment();
            throw e;
        }
```

- [ ] **Step 4: Instrument ActionMcpTools with a scope-denied tag**

In `backend/src/main/java/com/devradar/mcp/ActionMcpTools.java`:

```java
import io.micrometer.core.instrument.MeterRegistry;
```

Add `MeterRegistry meters` to constructor; wrap method body:

```java
        try {
            Long uid = SecurityUtils.getCurrentUserId();
            ActionProposalDTO out = actions.approveForUser(uid, proposalId, fixVersion);
            meters.counter("mcp.tool.calls", "tool", "propose_pr_for_cve", "status", "success").increment();
            return new ProposePrResult(out.status().name(), out.prUrl());
        } catch (McpScopeException e) {
            meters.counter("mcp.tool.calls", "tool", "propose_pr_for_cve", "status", "denied_scope").increment();
            throw e;
        } catch (RuntimeException e) {
            meters.counter("mcp.tool.calls", "tool", "propose_pr_for_cve", "status", "error").increment();
            throw e;
        }
```

Note: the aspect runs before the method body, so `McpScopeException` is thrown outside the method — this catch is defensive. The `denied_scope` counter may need to be incremented inside the aspect instead. If the IT verifies the counter but the aspect throws outside the method, add the counter increment inside `RequireScopeAspect`:

In `backend/src/main/java/com/devradar/mcp/RequireScopeAspect.java`, add:

```java
    private final io.micrometer.core.instrument.MeterRegistry meters;

    public RequireScopeAspect(io.micrometer.core.instrument.MeterRegistry meters) {
        this.meters = meters;
    }
```

And in `enforceScope`, before throwing the exception, emit:

```java
            meters.counter("mcp.tool.calls", "tool", sig.getName(), "status", "denied_scope").increment();
```

- [ ] **Step 5: Instrument ApiKeyAuthenticationFilter with auth failure counter**

In `backend/src/main/java/com/devradar/security/ApiKeyAuthenticationFilter.java`, add field and constructor param for `MeterRegistry`, then increment `mcp.auth.failures` with a `reason` tag (`missing`, `malformed`, `unknown_key`) on each rejection branch. Add the import:

```java
import io.micrometer.core.instrument.MeterRegistry;
```

Add field and update constructor:

```java
    private final MeterRegistry meters;

    public ApiKeyAuthenticationFilter(UserApiKeyRepository repo,
                                       ApiKeyHasher hasher,
                                       ApplicationEventPublisher events,
                                       MeterRegistry meters) {
        this.repo = repo;
        this.hasher = hasher;
        this.events = events;
        this.meters = meters;
    }
```

Before each `resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); return;` line, add:

```java
            meters.counter("mcp.auth.failures", "reason", "missing").increment();
```

(or `"malformed"` / `"unknown_key"` appropriate to the branch).

- [ ] **Step 6: Update ApiKeyAuthenticationFilterTest to pass MeterRegistry**

Update `backend/src/test/java/com/devradar/security/ApiKeyAuthenticationFilterTest.java` `setUp()`:

```java
    MeterRegistry metersMock;

    @BeforeEach
    void setUp() {
        repo = mock(UserApiKeyRepository.class);
        hasher = mock(ApiKeyHasher.class);
        events = mock(ApplicationEventPublisher.class);
        chain = mock(FilterChain.class);
        metersMock = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        filter = new ApiKeyAuthenticationFilter(repo, hasher, events, metersMock);
        SecurityContextHolder.clearContext();
    }
```

Add the import:

```java
import io.micrometer.core.instrument.MeterRegistry;
```

- [ ] **Step 7: Run the full test suite**

Run: `cd backend && mvn test`
Expected: all tests PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/devradar/mcp/ backend/src/main/java/com/devradar/security/ApiKeyAuthenticationFilter.java backend/src/test/java/com/devradar/security/ApiKeyAuthenticationFilterTest.java
git commit -m "feat(mcp): instrument MCP tools and auth filter with Micrometer counters"
```

---

## Task 17: Documentation — README section on MCP usage

**Files:**
- Modify: `backend/README.md` (create if it doesn't exist)

- [ ] **Step 1: Check for existing README**

Run: `ls backend/README.md 2>&1 || echo "no README"`

- [ ] **Step 2: Add MCP section to README**

Open or create `backend/README.md`. Append (or set, if file is new) this section:

```markdown
## MCP Server Surface

Dev Radar exposes four tools via the Model Context Protocol:

| Tool | Scope | Description |
|---|---|---|
| `query_radar` | READ | Latest READY radar for the authenticated user |
| `get_user_interests` | READ | User's interest tags |
| `get_recent_items` | READ | Recent ingested items filtered by user's interests (args: `days`, `tagSlug`) |
| `propose_pr_for_cve` | WRITE | Approve a CVE-fix PR proposal (args: `proposalId`, `fixVersion`) |

### Generating an API key

```bash
curl -X POST http://localhost:8080/api/users/me/api-keys \
     -H "Authorization: Bearer <your-jwt>" \
     -H "Content-Type: application/json" \
     -d '{"name":"Cursor","scope":"READ"}'
```

The response includes `key` — copy it immediately; it is never shown again.

### Connecting Claude Desktop

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "devradar": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/mcp/sse",
               "--header", "Authorization: Bearer devr_<your-key>"]
    }
  }
}
```

Restart Claude Desktop. The four tools should appear in the MCP tool tray.

### Connecting Cursor

Add a custom MCP server in Cursor settings pointing to `http://localhost:8080/mcp/sse` with the same `Authorization: Bearer devr_<key>` header.
```

- [ ] **Step 3: Commit**

```bash
git add backend/README.md
git commit -m "docs(mcp): document MCP tool surface, key generation, and client setup"
```

---

## Self-Review Checklist (Run after all tasks)

Before declaring Plan 6 complete, verify:

1. **All 4 tools implemented and registered:** `query_radar`, `get_user_interests`, `get_recent_items`, `propose_pr_for_cve`. McpConfig registers all four.
2. **Scope enforcement:** `propose_pr_for_cve` returns a scope-denied error when called with a READ-scope key (covered by `ActionMcpToolsScopeIT`).
3. **API key auth:** Unauthenticated `/mcp/**` requests return 401 (covered by `RadarMcpToolsIT.queryRadarToolRequiresApiKey`).
4. **JWT auth for key management:** `/api/users/me/api-keys` endpoints require JWT; raw key returned exactly once on create (covered by `ApiKeyResourceIT`).
5. **Observability:** `mcp.tool.calls` and `mcp.auth.failures` counters emit on every call (verifiable via `/actuator/prometheus` after running integration tests).
6. **Migration:** `user_api_keys` table created with unique `key_hash` index and composite `(user_id, revoked_at)` index.
7. **Full suite passes:** `mvn test` completes with zero failures.
8. **No regression:** existing JWT-authenticated endpoints still work (covered by existing IT suite).

---

## Execution Notes

- **Framework version risk:** Spring AI MCP starter API has evolved rapidly. If endpoint paths (`/mcp/message`, `/mcp/sse`) or method-tool registration APIs differ from what this plan specifies, check `META-INF/spring.factories` or `@AutoConfiguration` classes in the resolved `spring-ai-starter-mcp-server-webmvc` jar and adjust Task 1 config and Task 13 IT assertions to match. The tool registration via `MethodToolCallbackProvider` is stable; only transport paths may change.
- **Anti-pattern to avoid:** Do NOT add business logic into MCP tool classes. Every tool must resolve to an application-service call. If you find yourself writing SQL or domain logic inside an MCP tool, stop and extract it to the appropriate application service or repository.
- **Order matters:** Tasks 1–9 must complete in order (dependency → migration → entity → auth filter → wiring → service → REST). Tasks 10–11 are repository-layer prep; Tasks 12–15 are the MCP tools (build read tools before write); Task 16 is cross-cutting instrumentation.
