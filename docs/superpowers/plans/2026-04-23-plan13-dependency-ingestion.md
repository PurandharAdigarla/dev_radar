# Dependency-Aware Ingestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `DEP_RELEASE` ingestion source that scans users' GitHub repos for dependency files (`pom.xml`, `package.json`, `build.gradle`), discovers what packages they use, checks Maven Central and npm for newer versions, and ingests those releases so the AI orchestrator can build themes like "your jackson-databind has a new version."

**Architecture:** Two scheduled jobs: `DependencyScanJob` (daily) scans user repos for dependency files and upserts discovered packages into a `user_dependency` table. `DependencyReleaseIngestor` (daily, 2h after scan) queries distinct packages from that table, checks registries for newer versions, and produces `FetchedItem`s through the existing `IngestionService` pipeline. Three `DependencyFileParser` implementations handle pom.xml (XML), package.json (JSON), and build.gradle (regex).

**Tech Stack:** GitHub Contents API (existing `GitHubApiClient`), Maven Central Solr API, npm Registry API, Spring `@Scheduled`, WireMock for testing.

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `backend/src/main/java/com/devradar/domain/UserDependency.java` | JPA entity for `user_dependency` table |
| Create | `backend/src/main/java/com/devradar/repository/UserDependencyRepository.java` | Spring Data JPA repository |
| Create | `backend/src/main/java/com/devradar/ingest/deps/DependencyFileParser.java` | Interface: parses file text → list of `(ecosystem, packageName, version)` |
| Create | `backend/src/main/java/com/devradar/ingest/deps/PomParser.java` | XML parser for Maven pom.xml |
| Create | `backend/src/main/java/com/devradar/ingest/deps/PackageJsonParser.java` | JSON parser for npm package.json |
| Create | `backend/src/main/java/com/devradar/ingest/deps/GradleParser.java` | Regex parser for Gradle build files |
| Create | `backend/src/main/java/com/devradar/ingest/job/DependencyScanJob.java` | Scheduled daily job: scans user repos, upserts `user_dependency` |
| Create | `backend/src/main/java/com/devradar/ingest/client/DependencyReleaseClient.java` | Checks Maven Central + npm for new versions |
| Create | `backend/src/main/java/com/devradar/ingest/job/DependencyReleaseIngestor.java` | Scheduled daily: queries deps, checks registries, ingests releases |
| Create | `backend/src/main/resources/db/changelog/018-dependency-ingestion-schema.xml` | Creates `user_dependency` table and seeds DEP_RELEASE source |
| Create | `backend/src/test/java/com/devradar/ingest/deps/PomParserTest.java` | Unit tests for pom.xml parsing |
| Create | `backend/src/test/java/com/devradar/ingest/deps/PackageJsonParserTest.java` | Unit tests for package.json parsing |
| Create | `backend/src/test/java/com/devradar/ingest/deps/GradleParserTest.java` | Unit tests for build.gradle parsing |
| Create | `backend/src/test/java/com/devradar/ingest/client/DependencyReleaseClientTest.java` | WireMock tests for Maven Central + npm lookups |
| Create | `backend/src/test/java/com/devradar/ingest/job/DependencyScanJobTest.java` | Mockito tests for scan job |
| Create | `backend/src/test/java/com/devradar/ingest/job/DependencyReleaseIngestorTest.java` | Mockito tests for release ingestor |
| Modify | `backend/src/main/java/com/devradar/github/GitHubApiClient.java` | Add `listDirectoryEntries` method |
| Modify | `backend/src/main/resources/db/changelog/db.changelog-master.xml` | Include changeset 018 |
| Modify | `backend/src/main/resources/application-test.yml` | Disable dep-scan and dep-release during tests |
| Modify | `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java` | Add DEP_RELEASE source description to prompt |
| Modify | `frontend/src/components/SourceCard.tsx` | Add `DEP_RELEASE: "Dependency"` to SOURCE_LABELS |

---

### Task 1: Liquibase schema — user_dependency table and DEP_RELEASE source

**Files:**
- Create: `backend/src/main/resources/db/changelog/018-dependency-ingestion-schema.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create changeset file**

Create `backend/src/main/resources/db/changelog/018-dependency-ingestion-schema.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="018-create-user-dependency" author="devradar">
        <createTable tableName="user_dependency">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="user_id" type="BIGINT">
                <constraints nullable="false"/>
            </column>
            <column name="repo_full_name" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="file_path" type="VARCHAR(512)">
                <constraints nullable="false"/>
            </column>
            <column name="ecosystem" type="VARCHAR(20)">
                <constraints nullable="false"/>
            </column>
            <column name="package_name" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="current_version" type="VARCHAR(100)">
                <constraints nullable="false"/>
            </column>
            <column name="scanned_at" type="TIMESTAMP">
                <constraints nullable="false"/>
            </column>
        </createTable>
        <addUniqueConstraint tableName="user_dependency"
            columnNames="user_id, repo_full_name, file_path, package_name"
            constraintName="uk_user_dependency"/>
        <addForeignKeyConstraint
            baseTableName="user_dependency" baseColumnNames="user_id"
            referencedTableName="users" referencedColumnNames="id"
            constraintName="fk_user_dependency_user"/>
    </changeSet>

    <changeSet id="018-seed-dep-release-source" author="devradar">
        <sql>
INSERT INTO sources (code, display_name, active, fetch_interval_minutes, created_at) VALUES
  ('DEP_RELEASE', 'Dependency', true, 1440, NOW());
        </sql>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: Add include to master changelog**

Add to `backend/src/main/resources/db/changelog/db.changelog-master.xml`, after the line `<include file="db/changelog/017-article-ingestion-schema.xml"/>`:

```xml
    <include file="db/changelog/018-dependency-ingestion-schema.xml"/>
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/changelog/018-dependency-ingestion-schema.xml src/main/resources/db/changelog/db.changelog-master.xml
git commit -m "feat(backend): add user_dependency table and seed DEP_RELEASE source"
```

---

### Task 2: UserDependency entity and repository

**Files:**
- Create: `backend/src/main/java/com/devradar/domain/UserDependency.java`
- Create: `backend/src/main/java/com/devradar/repository/UserDependencyRepository.java`

- [ ] **Step 1: Create UserDependency entity**

Create `backend/src/main/java/com/devradar/domain/UserDependency.java`:

```java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_dependency",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_user_dependency",
           columnNames = {"user_id", "repo_full_name", "file_path", "package_name"}))
public class UserDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "repo_full_name", nullable = false, length = 255)
    private String repoFullName;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(nullable = false, length = 20)
    private String ecosystem;

    @Column(name = "package_name", nullable = false, length = 255)
    private String packageName;

    @Column(name = "current_version", nullable = false, length = 100)
    private String currentVersion;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRepoFullName() { return repoFullName; }
    public void setRepoFullName(String repoFullName) { this.repoFullName = repoFullName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getEcosystem() { return ecosystem; }
    public void setEcosystem(String ecosystem) { this.ecosystem = ecosystem; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
    public Instant getScannedAt() { return scannedAt; }
    public void setScannedAt(Instant scannedAt) { this.scannedAt = scannedAt; }
}
```

- [ ] **Step 2: Create UserDependencyRepository**

Create `backend/src/main/java/com/devradar/repository/UserDependencyRepository.java`:

```java
package com.devradar.repository;

import com.devradar.domain.UserDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserDependencyRepository extends JpaRepository<UserDependency, Long> {

    Optional<UserDependency> findByUserIdAndRepoFullNameAndFilePathAndPackageName(
        Long userId, String repoFullName, String filePath, String packageName);

    @Query("SELECT DISTINCT d.ecosystem, d.packageName, d.currentVersion FROM UserDependency d")
    List<Object[]> findDistinctPackages();
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/devradar/domain/UserDependency.java src/main/java/com/devradar/repository/UserDependencyRepository.java
git commit -m "feat(backend): add UserDependency entity and repository"
```

---

### Task 3: Dependency file parsers — PomParser, PackageJsonParser, GradleParser

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/deps/DependencyFileParser.java`
- Create: `backend/src/main/java/com/devradar/ingest/deps/ParsedDependency.java`
- Create: `backend/src/main/java/com/devradar/ingest/deps/PomParser.java`
- Create: `backend/src/main/java/com/devradar/ingest/deps/PackageJsonParser.java`
- Create: `backend/src/main/java/com/devradar/ingest/deps/GradleParser.java`
- Create: `backend/src/test/java/com/devradar/ingest/deps/PomParserTest.java`
- Create: `backend/src/test/java/com/devradar/ingest/deps/PackageJsonParserTest.java`
- Create: `backend/src/test/java/com/devradar/ingest/deps/GradleParserTest.java`

- [ ] **Step 1: Create the interface and record**

Create `backend/src/main/java/com/devradar/ingest/deps/ParsedDependency.java`:

```java
package com.devradar.ingest.deps;

public record ParsedDependency(String ecosystem, String packageName, String version) {}
```

Create `backend/src/main/java/com/devradar/ingest/deps/DependencyFileParser.java`:

```java
package com.devradar.ingest.deps;

import java.util.List;

public interface DependencyFileParser {
    List<ParsedDependency> parse(String fileContent);
}
```

- [ ] **Step 2: Write PomParser test**

Create `backend/src/test/java/com/devradar/ingest/deps/PomParserTest.java`:

```java
package com.devradar.ingest.deps;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PomParserTest {

    final PomParser parser = new PomParser();

    @Test
    void parse_extractsDependencies() {
        String pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <dependencies>
                    <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                        <version>2.16.1</version>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

        List<ParsedDependency> deps = parser.parse(pom);

        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).ecosystem()).isEqualTo("MAVEN");
        assertThat(deps.get(0).packageName()).isEqualTo("com.fasterxml.jackson.core:jackson-databind");
        assertThat(deps.get(0).version()).isEqualTo("2.16.1");
    }

    @Test
    void parse_skipsPropertyPlaceholders() {
        String pom = """
            <project>
                <dependencies>
                    <dependency>
                        <groupId>io.jsonwebtoken</groupId>
                        <artifactId>jjwt-api</artifactId>
                        <version>${jjwt.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """;

        assertThat(parser.parse(pom)).isEmpty();
    }

    @Test
    void parse_returnsEmpty_onMalformedXml() {
        assertThat(parser.parse("not xml")).isEmpty();
    }
}
```

- [ ] **Step 3: Implement PomParser**

Create `backend/src/main/java/com/devradar/ingest/deps/PomParser.java`:

```java
package com.devradar.ingest.deps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PomParser implements DependencyFileParser {

    private static final Logger LOG = LoggerFactory.getLogger(PomParser.class);

    @Override
    public List<ParsedDependency> parse(String fileContent) {
        List<ParsedDependency> out = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)));

            NodeList deps = doc.getElementsByTagName("dependency");
            for (int i = 0; i < deps.getLength(); i++) {
                Element el = (Element) deps.item(i);
                String groupId = textContent(el, "groupId");
                String artifactId = textContent(el, "artifactId");
                String version = textContent(el, "version");

                if (groupId == null || artifactId == null || version == null) continue;
                if (version.contains("${")) continue;

                out.add(new ParsedDependency("MAVEN", groupId + ":" + artifactId, version));
            }
        } catch (Exception e) {
            LOG.warn("failed to parse pom.xml: {}", e.toString());
        }
        return out;
    }

    private static String textContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent().trim();
        return text.isEmpty() ? null : text;
    }
}
```

- [ ] **Step 4: Run PomParser tests**

Run: `cd backend && mvn test -pl . -Dtest=PomParserTest`
Expected: All 3 tests PASS

- [ ] **Step 5: Write PackageJsonParser test**

Create `backend/src/test/java/com/devradar/ingest/deps/PackageJsonParserTest.java`:

```java
package com.devradar.ingest.deps;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PackageJsonParserTest {

    final PackageJsonParser parser = new PackageJsonParser();

    @Test
    void parse_extractsBothDepsAndDevDeps() {
        String json = """
            {
              "dependencies": {
                "react": "^18.2.0",
                "react-dom": "^18.2.0"
              },
              "devDependencies": {
                "vite": "^5.0.0"
              }
            }
            """;

        List<ParsedDependency> deps = parser.parse(json);

        assertThat(deps).hasSize(3);
        assertThat(deps).allMatch(d -> d.ecosystem().equals("NPM"));
        assertThat(deps.stream().map(ParsedDependency::packageName))
            .containsExactlyInAnyOrder("react", "react-dom", "vite");
    }

    @Test
    void parse_handlesWorkspaceProtocol() {
        String json = """
            {
              "dependencies": {
                "my-lib": "workspace:*",
                "express": "4.18.2"
              }
            }
            """;

        List<ParsedDependency> deps = parser.parse(json);

        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).packageName()).isEqualTo("express");
        assertThat(deps.get(0).version()).isEqualTo("4.18.2");
    }

    @Test
    void parse_returnsEmpty_onMalformedJson() {
        assertThat(parser.parse("not json")).isEmpty();
    }
}
```

- [ ] **Step 6: Implement PackageJsonParser**

Create `backend/src/main/java/com/devradar/ingest/deps/PackageJsonParser.java`:

```java
package com.devradar.ingest.deps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PackageJsonParser implements DependencyFileParser {

    private static final Logger LOG = LoggerFactory.getLogger(PackageJsonParser.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public List<ParsedDependency> parse(String fileContent) {
        List<ParsedDependency> out = new ArrayList<>();
        try {
            JsonNode root = JSON.readTree(fileContent);
            extractDeps(root.get("dependencies"), out);
            extractDeps(root.get("devDependencies"), out);
        } catch (Exception e) {
            LOG.warn("failed to parse package.json: {}", e.toString());
        }
        return out;
    }

    private static void extractDeps(JsonNode node, List<ParsedDependency> out) {
        if (node == null || !node.isObject()) return;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String version = entry.getValue().asText();
            if (version.startsWith("workspace:") || version.startsWith("file:")
                    || version.startsWith("link:")) continue;
            out.add(new ParsedDependency("NPM", entry.getKey(), version));
        }
    }
}
```

- [ ] **Step 7: Run PackageJsonParser tests**

Run: `cd backend && mvn test -pl . -Dtest=PackageJsonParserTest`
Expected: All 3 tests PASS

- [ ] **Step 8: Write GradleParser test**

Create `backend/src/test/java/com/devradar/ingest/deps/GradleParserTest.java`:

```java
package com.devradar.ingest.deps;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class GradleParserTest {

    final GradleParser parser = new GradleParser();

    @Test
    void parse_extractsSingleQuotedDeps() {
        String gradle = """
            dependencies {
                implementation 'org.springframework.boot:spring-boot-starter-web:3.5.0'
                testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
            }
            """;

        List<ParsedDependency> deps = parser.parse(gradle);

        assertThat(deps).hasSize(2);
        assertThat(deps.get(0).ecosystem()).isEqualTo("GRADLE");
        assertThat(deps.get(0).packageName()).isEqualTo("org.springframework.boot:spring-boot-starter-web");
        assertThat(deps.get(0).version()).isEqualTo("3.5.0");
    }

    @Test
    void parse_extractsDoubleQuotedDeps() {
        String gradle = """
            dependencies {
                implementation "io.micrometer:micrometer-core:1.12.0"
            }
            """;

        List<ParsedDependency> deps = parser.parse(gradle);

        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).packageName()).isEqualTo("io.micrometer:micrometer-core");
    }

    @Test
    void parse_skipsVersionlessAndVariableRefs() {
        String gradle = """
            dependencies {
                implementation 'org.springframework.boot:spring-boot-starter'
                implementation "io.jsonwebtoken:jjwt-api:$jjwtVersion"
            }
            """;

        assertThat(parser.parse(gradle)).isEmpty();
    }
}
```

- [ ] **Step 9: Implement GradleParser**

Create `backend/src/main/java/com/devradar/ingest/deps/GradleParser.java`:

```java
package com.devradar.ingest.deps;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleParser implements DependencyFileParser {

    private static final Pattern DEP_PATTERN = Pattern.compile(
        "(?:implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly)" +
        "\\s+['\"]([^:]+):([^:]+):([^'\"$]+)['\"]"
    );

    @Override
    public List<ParsedDependency> parse(String fileContent) {
        List<ParsedDependency> out = new ArrayList<>();
        Matcher m = DEP_PATTERN.matcher(fileContent);
        while (m.find()) {
            String group = m.group(1);
            String artifact = m.group(2);
            String version = m.group(3).trim();
            if (version.isEmpty()) continue;
            out.add(new ParsedDependency("GRADLE", group + ":" + artifact, version));
        }
        return out;
    }
}
```

- [ ] **Step 10: Run all parser tests**

Run: `cd backend && mvn test -pl . -Dtest="PomParserTest,PackageJsonParserTest,GradleParserTest"`
Expected: All 9 tests PASS

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/devradar/ingest/deps/ src/test/java/com/devradar/ingest/deps/
git commit -m "feat(backend): add dependency file parsers for pom.xml, package.json, and build.gradle"
```

---

### Task 4: Add listDirectoryEntries to GitHubApiClient

**Files:**
- Modify: `backend/src/main/java/com/devradar/github/GitHubApiClient.java`
- Modify: `backend/src/test/java/com/devradar/github/GitHubApiClientTest.java`

- [ ] **Step 1: Write the failing test**

Add a test to `backend/src/test/java/com/devradar/github/GitHubApiClientTest.java`:

```java
@Test
void listDirectoryEntries_returnsNamesAndTypes() {
    wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/alice/api/contents/"))
        .willReturn(WireMock.okJson("""
            [
              {"name": "pom.xml", "type": "file"},
              {"name": "src", "type": "dir"},
              {"name": "backend", "type": "dir"},
              {"name": "README.md", "type": "file"}
            ]
            """)));

    List<GitHubApiClient.DirEntry> entries = client.listDirectoryEntries("token", "alice/api", "");

    assertThat(entries).hasSize(4);
    assertThat(entries.get(0).name()).isEqualTo("pom.xml");
    assertThat(entries.get(0).type()).isEqualTo("file");
    assertThat(entries.get(2).name()).isEqualTo("backend");
    assertThat(entries.get(2).type()).isEqualTo("dir");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=GitHubApiClientTest#listDirectoryEntries_returnsNamesAndTypes -DfailIfNoTests=false`
Expected: FAIL — method does not exist yet

- [ ] **Step 3: Implement listDirectoryEntries**

Add to `backend/src/main/java/com/devradar/github/GitHubApiClient.java`, before the inner record declarations:

```java
    public List<DirEntry> listDirectoryEntries(String token, String repoFullName, String path) {
        JsonNode arr = http.get()
                .uri("/repos/" + repoFullName + "/contents/" + path)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve().body(JsonNode.class);
        List<DirEntry> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                out.add(new DirEntry(n.path("name").asText(), n.path("type").asText()));
            }
        }
        return out;
    }
```

Add the inner record alongside the existing ones:

```java
    public record DirEntry(String name, String type) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=GitHubApiClientTest`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/devradar/github/GitHubApiClient.java src/test/java/com/devradar/github/GitHubApiClientTest.java
git commit -m "feat(backend): add listDirectoryEntries to GitHubApiClient"
```

---

### Task 5: DependencyScanJob — daily repo scanner

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/job/DependencyScanJob.java`
- Create: `backend/src/test/java/com/devradar/ingest/job/DependencyScanJobTest.java`
- Modify: `backend/src/main/resources/application-test.yml`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/devradar/ingest/job/DependencyScanJobTest.java`:

```java
package com.devradar.ingest.job;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.UserDependency;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.github.GitHubApiClient.DirEntry;
import com.devradar.github.GitHubApiClient.FileContent;
import com.devradar.github.GitHubApiClient.RepoInfo;
import com.devradar.repository.UserDependencyRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DependencyScanJobTest {

    @Mock UserGithubIdentityRepository identityRepo;
    @Mock UserDependencyRepository depRepo;
    @Mock GitHubApiClient github;
    @Mock TokenEncryptor encryptor;
    @InjectMocks DependencyScanJob job;

    @Test
    void run_scansPomXmlAtRoot() {
        UserGithubIdentity identity = new UserGithubIdentity();
        identity.setUserId(1L);
        identity.setAccessTokenEncrypted("enc");
        when(identityRepo.findAll()).thenReturn(List.of(identity));
        when(encryptor.decrypt("enc")).thenReturn("token");
        when(github.listRepos("token")).thenReturn(List.of(new RepoInfo("alice/api", "main")));
        when(github.listDirectoryEntries("token", "alice/api", ""))
            .thenReturn(List.of(new DirEntry("pom.xml", "file"), new DirEntry("src", "dir")));
        when(github.getFileContent("token", "alice/api", "pom.xml", null))
            .thenReturn(new FileContent("""
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.16.1</version>
                        </dependency>
                    </dependencies>
                </project>
                """, "sha1", "base64"));
        when(depRepo.findByUserIdAndRepoFullNameAndFilePathAndPackageName(
            any(), any(), any(), any())).thenReturn(Optional.empty());

        job.run();

        ArgumentCaptor<UserDependency> cap = ArgumentCaptor.forClass(UserDependency.class);
        verify(depRepo).save(cap.capture());
        UserDependency saved = cap.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getRepoFullName()).isEqualTo("alice/api");
        assertThat(saved.getFilePath()).isEqualTo("pom.xml");
        assertThat(saved.getEcosystem()).isEqualTo("MAVEN");
        assertThat(saved.getPackageName()).isEqualTo("com.fasterxml.jackson.core:jackson-databind");
        assertThat(saved.getCurrentVersion()).isEqualTo("2.16.1");
    }

    @Test
    void run_scansSubdirectories() {
        UserGithubIdentity identity = new UserGithubIdentity();
        identity.setUserId(2L);
        identity.setAccessTokenEncrypted("enc");
        when(identityRepo.findAll()).thenReturn(List.of(identity));
        when(encryptor.decrypt("enc")).thenReturn("token");
        when(github.listRepos("token")).thenReturn(List.of(new RepoInfo("bob/mono", "main")));
        when(github.listDirectoryEntries("token", "bob/mono", ""))
            .thenReturn(List.of(new DirEntry("frontend", "dir"), new DirEntry("README.md", "file")));
        when(github.listDirectoryEntries("token", "bob/mono", "frontend"))
            .thenReturn(List.of(new DirEntry("package.json", "file")));
        when(github.getFileContent("token", "bob/mono", "frontend/package.json", null))
            .thenReturn(new FileContent("""
                {"dependencies":{"react":"^18.2.0"}}
                """, "sha2", "base64"));
        when(depRepo.findByUserIdAndRepoFullNameAndFilePathAndPackageName(
            any(), any(), any(), any())).thenReturn(Optional.empty());

        job.run();

        ArgumentCaptor<UserDependency> cap = ArgumentCaptor.forClass(UserDependency.class);
        verify(depRepo).save(cap.capture());
        assertThat(cap.getValue().getFilePath()).isEqualTo("frontend/package.json");
        assertThat(cap.getValue().getEcosystem()).isEqualTo("NPM");
    }

    @Test
    void run_skipsUsersWithNoGithubIdentity() {
        when(identityRepo.findAll()).thenReturn(List.of());

        job.run();

        verifyNoInteractions(github, depRepo);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=DependencyScanJobTest -DfailIfNoTests=false`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement DependencyScanJob**

Create `backend/src/main/java/com/devradar/ingest/job/DependencyScanJob.java`:

```java
package com.devradar.ingest.job;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.UserDependency;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.github.GitHubApiClient.DirEntry;
import com.devradar.github.GitHubApiClient.FileContent;
import com.devradar.github.GitHubApiClient.RepoInfo;
import com.devradar.ingest.deps.*;
import com.devradar.repository.UserDependencyRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DependencyScanJob {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyScanJob.class);

    private static final Map<String, DependencyFileParser> PARSERS = Map.of(
        "pom.xml", new PomParser(),
        "package.json", new PackageJsonParser(),
        "build.gradle", new GradleParser(),
        "build.gradle.kts", new GradleParser()
    );

    private static final Set<String> DEP_FILES = PARSERS.keySet();

    private final UserGithubIdentityRepository identityRepo;
    private final UserDependencyRepository depRepo;
    private final GitHubApiClient github;
    private final TokenEncryptor encryptor;
    private final int maxReposPerUser;

    public DependencyScanJob(UserGithubIdentityRepository identityRepo,
                             UserDependencyRepository depRepo,
                             GitHubApiClient github,
                             TokenEncryptor encryptor,
                             @Value("${devradar.ingest.dep-scan.max-repos-per-user:20}") int maxReposPerUser) {
        this.identityRepo = identityRepo;
        this.depRepo = depRepo;
        this.github = github;
        this.encryptor = encryptor;
        this.maxReposPerUser = maxReposPerUser;
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.dep-scan.fixed-delay-ms:86400000}",
               initialDelayString = "${devradar.ingest.dep-scan.initial-delay-ms:120000}")
    public void run() {
        List<UserGithubIdentity> users = identityRepo.findAll();
        LOG.info("dependency scan starting; {} users with GitHub identity", users.size());

        for (UserGithubIdentity identity : users) {
            try {
                scanUser(identity);
            } catch (Exception e) {
                LOG.warn("dependency scan failed userId={}: {}", identity.getUserId(), e.toString());
            }
        }
    }

    private void scanUser(UserGithubIdentity identity) {
        String token = encryptor.decrypt(identity.getAccessTokenEncrypted());
        List<RepoInfo> repos = github.listRepos(token);
        int limit = Math.min(repos.size(), maxReposPerUser);

        for (int i = 0; i < limit; i++) {
            RepoInfo repo = repos.get(i);
            try {
                scanRepo(identity.getUserId(), token, repo.fullName());
            } catch (Exception e) {
                LOG.debug("repo scan skipped repo={}: {}", repo.fullName(), e.getMessage());
            }
        }
    }

    private void scanRepo(Long userId, String token, String repoFullName) {
        List<DirEntry> rootEntries = github.listDirectoryEntries(token, repoFullName, "");

        for (DirEntry entry : rootEntries) {
            if ("file".equals(entry.type()) && DEP_FILES.contains(entry.name())) {
                parseAndUpsert(userId, token, repoFullName, entry.name());
            }
        }

        for (DirEntry entry : rootEntries) {
            if (!"dir".equals(entry.type())) continue;
            try {
                List<DirEntry> subEntries = github.listDirectoryEntries(token, repoFullName, entry.name());
                for (DirEntry sub : subEntries) {
                    if ("file".equals(sub.type()) && DEP_FILES.contains(sub.name())) {
                        parseAndUpsert(userId, token, repoFullName, entry.name() + "/" + sub.name());
                    }
                }
            } catch (Exception e) {
                LOG.debug("subdir scan skipped {}/{}: {}", repoFullName, entry.name(), e.getMessage());
            }
        }
    }

    private void parseAndUpsert(Long userId, String token, String repoFullName, String filePath) {
        String fileName = filePath.contains("/") ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath;
        DependencyFileParser parser = PARSERS.get(fileName);
        if (parser == null) return;

        FileContent content = github.getFileContent(token, repoFullName, filePath, null);
        List<ParsedDependency> deps = parser.parse(content.text());

        Instant now = Instant.now();
        for (ParsedDependency dep : deps) {
            UserDependency existing = depRepo.findByUserIdAndRepoFullNameAndFilePathAndPackageName(
                userId, repoFullName, filePath, dep.packageName()).orElse(null);

            if (existing != null) {
                existing.setCurrentVersion(dep.version());
                existing.setScannedAt(now);
                depRepo.save(existing);
            } else {
                UserDependency ud = new UserDependency();
                ud.setUserId(userId);
                ud.setRepoFullName(repoFullName);
                ud.setFilePath(filePath);
                ud.setEcosystem(dep.ecosystem());
                ud.setPackageName(dep.packageName());
                ud.setCurrentVersion(dep.version());
                ud.setScannedAt(now);
                depRepo.save(ud);
            }
        }
    }
}
```

- [ ] **Step 4: Disable scan job during tests**

Add to `backend/src/main/resources/application-test.yml`, inside the `devradar.ingest` block, after the `article` section:

```yaml
    dep-scan:
      fixed-delay-ms: 86400000
      initial-delay-ms: 86400000
    dep-release:
      fixed-delay-ms: 86400000
      initial-delay-ms: 86400000
```

- [ ] **Step 5: Run tests**

Run: `cd backend && mvn test -pl . -Dtest=DependencyScanJobTest`
Expected: All 3 tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/devradar/ingest/job/DependencyScanJob.java src/test/java/com/devradar/ingest/job/DependencyScanJobTest.java src/main/resources/application-test.yml
git commit -m "feat(backend): add DependencyScanJob for scanning user repos for dependency files"
```

---

### Task 6: DependencyReleaseClient — Maven Central + npm registry lookup

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/client/DependencyReleaseClient.java`
- Create: `backend/src/test/java/com/devradar/ingest/client/DependencyReleaseClientTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/devradar/ingest/client/DependencyReleaseClientTest.java`:

```java
package com.devradar.ingest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyReleaseClientTest {

    WireMockServer wm;
    DependencyReleaseClient client;

    @BeforeEach
    void setup() {
        wm = new WireMockServer(0);
        wm.start();
        String base = "http://localhost:" + wm.port();
        client = new DependencyReleaseClient(RestClient.builder(), base, base);
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void checkMaven_returnsLatestVersion() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/solrsearch/select"))
            .willReturn(WireMock.okJson("""
                {
                  "response": {
                    "docs": [
                      {"latestVersion": "2.17.0", "p": "jar", "timestamp": 1700000000000}
                    ]
                  }
                }
                """)));

        Optional<FetchedItem> item = client.checkForNewerVersion("MAVEN",
            "com.fasterxml.jackson.core:jackson-databind", "2.16.1");

        assertThat(item).isPresent();
        assertThat(item.get().title()).contains("jackson-databind");
        assertThat(item.get().title()).contains("2.17.0");
        assertThat(item.get().externalId()).isEqualTo("MAVEN:com.fasterxml.jackson.core:jackson-databind:2.17.0");
    }

    @Test
    void checkMaven_returnsEmpty_whenSameVersion() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/solrsearch/select"))
            .willReturn(WireMock.okJson("""
                {"response": {"docs": [{"latestVersion": "2.16.1"}]}}
                """)));

        Optional<FetchedItem> item = client.checkForNewerVersion("MAVEN",
            "com.fasterxml.jackson.core:jackson-databind", "2.16.1");

        assertThat(item).isEmpty();
    }

    @Test
    void checkNpm_returnsLatestVersion() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/react/latest"))
            .willReturn(WireMock.okJson("""
                {"version": "19.0.0", "description": "A JavaScript library for building user interfaces"}
                """)));

        Optional<FetchedItem> item = client.checkForNewerVersion("NPM", "react", "18.2.0");

        assertThat(item).isPresent();
        assertThat(item.get().title()).contains("react");
        assertThat(item.get().title()).contains("19.0.0");
        assertThat(item.get().externalId()).isEqualTo("NPM:react:19.0.0");
    }

    @Test
    void checkNpm_returnsEmpty_whenSameVersion() {
        wm.stubFor(WireMock.get(WireMock.urlPathEqualTo("/react/latest"))
            .willReturn(WireMock.okJson("""
                {"version": "18.2.0"}
                """)));

        assertThat(client.checkForNewerVersion("NPM", "react", "18.2.0")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=DependencyReleaseClientTest -DfailIfNoTests=false`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement DependencyReleaseClient**

Create `backend/src/main/java/com/devradar/ingest/client/DependencyReleaseClient.java`:

```java
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
```

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn test -pl . -Dtest=DependencyReleaseClientTest`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/devradar/ingest/client/DependencyReleaseClient.java src/test/java/com/devradar/ingest/client/DependencyReleaseClientTest.java
git commit -m "feat(backend): add DependencyReleaseClient for Maven Central and npm registry lookups"
```

---

### Task 7: DependencyReleaseIngestor — scheduled release checker

**Files:**
- Create: `backend/src/main/java/com/devradar/ingest/job/DependencyReleaseIngestor.java`
- Create: `backend/src/test/java/com/devradar/ingest/job/DependencyReleaseIngestorTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/devradar/ingest/job/DependencyReleaseIngestorTest.java`:

```java
package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.DependencyReleaseClient;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.UserDependencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DependencyReleaseIngestorTest {

    @Mock SourceRepository sources;
    @Mock UserDependencyRepository depRepo;
    @Mock DependencyReleaseClient client;
    @Mock IngestionService ingestion;
    @InjectMocks DependencyReleaseIngestor ingestor;

    @Test
    void run_skipsWhenSourceNotActive() {
        when(sources.findByCode("DEP_RELEASE")).thenReturn(Optional.empty());

        ingestor.run();

        verifyNoInteractions(depRepo, client, ingestion);
    }

    @Test
    void run_checksRegistryAndIngestsNewReleases() {
        Source src = mock(Source.class);
        when(src.isActive()).thenReturn(true);
        when(sources.findByCode("DEP_RELEASE")).thenReturn(Optional.of(src));
        when(depRepo.findDistinctPackages()).thenReturn(List.of(
            new Object[]{"MAVEN", "com.fasterxml.jackson.core:jackson-databind", "2.16.1"}
        ));

        FetchedItem releaseItem = new FetchedItem(
            "MAVEN:com.fasterxml.jackson.core:jackson-databind:2.17.0",
            "https://central.sonatype.com/artifact/com.fasterxml.jackson.core/jackson-databind/2.17.0",
            "jackson-databind 2.17.0 released", null, null, Instant.now(), null, List.of()
        );
        when(client.checkForNewerVersion("MAVEN", "com.fasterxml.jackson.core:jackson-databind", "2.16.1"))
            .thenReturn(Optional.of(releaseItem));

        ingestor.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FetchedItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestion).ingestBatch(eq(src), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).title()).contains("jackson-databind");
    }

    @Test
    void run_skipsWhenNoNewerVersion() {
        Source src = mock(Source.class);
        when(src.isActive()).thenReturn(true);
        when(sources.findByCode("DEP_RELEASE")).thenReturn(Optional.of(src));
        when(depRepo.findDistinctPackages()).thenReturn(List.of(
            new Object[]{"NPM", "react", "18.2.0"}
        ));
        when(client.checkForNewerVersion("NPM", "react", "18.2.0")).thenReturn(Optional.empty());

        ingestor.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FetchedItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestion).ingestBatch(eq(src), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=DependencyReleaseIngestorTest -DfailIfNoTests=false`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement DependencyReleaseIngestor**

Create `backend/src/main/java/com/devradar/ingest/job/DependencyReleaseIngestor.java`:

```java
package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.ingest.IngestionService;
import com.devradar.ingest.client.DependencyReleaseClient;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.UserDependencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class DependencyReleaseIngestor {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyReleaseIngestor.class);
    private static final String CODE = "DEP_RELEASE";

    private final SourceRepository sources;
    private final UserDependencyRepository depRepo;
    private final DependencyReleaseClient client;
    private final IngestionService ingestion;

    public DependencyReleaseIngestor(SourceRepository sources, UserDependencyRepository depRepo,
                                     DependencyReleaseClient client, IngestionService ingestion) {
        this.sources = sources;
        this.depRepo = depRepo;
        this.client = client;
        this.ingestion = ingestion;
    }

    @Scheduled(fixedDelayString = "${devradar.ingest.dep-release.fixed-delay-ms:86400000}",
               initialDelayString = "${devradar.ingest.dep-release.initial-delay-ms:7200000}")
    public void run() {
        Source src = sources.findByCode(CODE).orElse(null);
        if (src == null || !src.isActive()) {
            LOG.info("DEP_RELEASE source not active; skipping");
            return;
        }

        List<Object[]> packages = depRepo.findDistinctPackages();
        LOG.info("dependency release check starting; {} distinct packages", packages.size());

        List<FetchedItem> allReleases = new ArrayList<>();
        for (Object[] row : packages) {
            String ecosystem = (String) row[0];
            String packageName = (String) row[1];
            String currentVersion = (String) row[2];
            try {
                Optional<FetchedItem> item = client.checkForNewerVersion(ecosystem, packageName, currentVersion);
                item.ifPresent(allReleases::add);
            } catch (Exception e) {
                LOG.debug("dep release check failed pkg={}: {}", packageName, e.getMessage());
            }
        }

        ingestion.ingestBatch(src, allReleases);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn test -pl . -Dtest=DependencyReleaseIngestorTest`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/devradar/ingest/job/DependencyReleaseIngestor.java src/test/java/com/devradar/ingest/job/DependencyReleaseIngestorTest.java
git commit -m "feat(backend): add DependencyReleaseIngestor for checking registries and ingesting new versions"
```

---

### Task 8: Update orchestrator prompt and frontend label

**Files:**
- Modify: `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java`
- Modify: `frontend/src/components/SourceCard.tsx`

- [ ] **Step 1: Add DEP_RELEASE to orchestrator prompt**

In `backend/src/main/java/com/devradar/ai/RadarOrchestrator.java`, find the CITATION PRIORITY section. After the ARTICLE paragraph (which was added in Plan 12), add:

```
        - DEP_RELEASE items are new versions of packages the user actually depends on in their
          GitHub repos. These are the HIGHEST priority citations — they represent direct, actionable
          updates. Always build a theme around DEP_RELEASE items when available, explaining what
          changed and whether the user should upgrade.
```

- [ ] **Step 2: Add DEP_RELEASE to SourceCard**

In `frontend/src/components/SourceCard.tsx`, add `DEP_RELEASE: "Dependency"` to `SOURCE_LABELS`:

```typescript
const SOURCE_LABELS: Record<string, string> = {
  HN: "HN",
  GH_TRENDING: "GitHub",
  GH_RELEASES: "Release",
  GH_STARS: "Starred",
  GHSA: "GHSA",
  ARTICLE: "Article",
  DEP_RELEASE: "Dependency",
};
```

- [ ] **Step 3: Verify**

Run: `cd backend && mvn -DskipTests compile`
Run: `cd frontend && npx vitest run`
Expected: Both pass

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/devradar/ai/RadarOrchestrator.java
cd .. && git add frontend/src/components/SourceCard.tsx
git commit -m "feat: add DEP_RELEASE source to orchestrator prompt and frontend SourceCard"
```

---

### Task 9: Full test suite verification

**Files:** None (verification only)

- [ ] **Step 1: Run full backend test suite**

Run: `cd backend && mvn clean test`
Expected: All tests PASS, BUILD SUCCESS

- [ ] **Step 2: Run full frontend test suite**

Run: `cd frontend && npx vitest run`
Expected: All tests PASS

- [ ] **Step 3: Commit any remaining fixes**

If any tests failed due to integration issues, fix and commit.
