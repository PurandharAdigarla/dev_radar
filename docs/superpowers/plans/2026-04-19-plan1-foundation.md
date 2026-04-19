# Dev Radar — Plan 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a Spring Boot backend where a user can register with email + password, log in, manage their profile, and pick interests from a curated catalog. No AI, no ingestion, no GitHub yet — just the foundation layer the rest of the system will build on.

**Architecture:** Modular monolith with clean architecture (domain → repository → service → web). Stateless JWT auth, Liquibase migrations, MapStruct DTO mapping. Mirrors the Creeno backend conventions the user already knows.

**Tech Stack:** Java 21, Spring Boot 3.5+, Maven, MySQL 8, Liquibase, Spring Security, JJWT, MapStruct, JUnit 5, Mockito, Testcontainers, AssertJ.

---

## File Structure

```
backend/
├── pom.xml
├── docker-compose.yml                          (local MySQL for dev)
├── src/main/
│   ├── java/com/devradar/
│   │   ├── DevRadarApplication.java
│   │   ├── config/
│   │   │   └── SecurityConfig.java
│   │   ├── domain/
│   │   │   ├── BaseAuditableEntity.java
│   │   │   ├── AuditEntityListener.java
│   │   │   ├── User.java
│   │   │   ├── RefreshToken.java
│   │   │   ├── InterestTag.java
│   │   │   ├── InterestCategory.java          (enum)
│   │   │   ├── UserInterest.java
│   │   │   ├── AuditLog.java
│   │   │   └── exception/
│   │   │       ├── UserAlreadyExistsException.java
│   │   │       ├── UserNotFoundException.java
│   │   │       ├── InvalidCredentialsException.java
│   │   │       ├── InvalidRefreshTokenException.java
│   │   │       ├── InterestTagNotFoundException.java
│   │   │       └── UserNotAuthenticatedException.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── RefreshTokenRepository.java
│   │   │   ├── InterestTagRepository.java
│   │   │   ├── UserInterestRepository.java
│   │   │   └── AuditLogRepository.java
│   │   ├── security/
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   ├── JwtTokenProvider.java
│   │   │   ├── JwtUserDetails.java
│   │   │   └── SecurityUtils.java
│   │   ├── service/
│   │   │   ├── AuthService.java
│   │   │   ├── UserService.java
│   │   │   ├── InterestTagService.java
│   │   │   ├── UserInterestService.java
│   │   │   ├── AuditLogService.java
│   │   │   ├── TagNormalizer.java
│   │   │   ├── application/
│   │   │   │   ├── AuthApplicationService.java
│   │   │   │   ├── UserApplicationService.java
│   │   │   │   └── InterestApplicationService.java
│   │   │   └── mapper/
│   │   │       ├── UserMapper.java
│   │   │       └── InterestTagMapper.java
│   │   └── web/rest/
│   │       ├── AuthResource.java
│   │       ├── UserResource.java
│   │       ├── InterestTagResource.java
│   │       ├── dto/
│   │       │   ├── RegisterRequestDTO.java
│   │       │   ├── LoginRequestDTO.java
│   │       │   ├── LoginResponseDTO.java
│   │       │   ├── RefreshRequestDTO.java
│   │       │   ├── UserResponseDTO.java
│   │       │   ├── UserUpdateDTO.java
│   │       │   ├── InterestTagResponseDTO.java
│   │       │   └── UserInterestsUpdateDTO.java
│   │       └── exception/
│   │           ├── ErrorResponse.java
│   │           └── GlobalExceptionHandler.java
│   └── resources/
│       ├── application.yml
│       └── db/changelog/
│           ├── db.changelog-master.xml
│           ├── 001-initial-auth-schema.xml
│           ├── 002-interest-tags-schema.xml
│           ├── 003-audit-log-schema.xml
│           └── 004-seed-interest-tags.xml
├── src/test/
│   ├── java/com/devradar/
│   │   ├── AbstractIntegrationTest.java
│   │   ├── service/
│   │   │   ├── AuthServiceTest.java
│   │   │   ├── UserServiceTest.java
│   │   │   ├── InterestTagServiceTest.java
│   │   │   ├── UserInterestServiceTest.java
│   │   │   └── TagNormalizerTest.java
│   │   ├── security/
│   │   │   └── JwtTokenProviderTest.java
│   │   └── web/rest/
│   │       ├── AuthResourceIT.java
│   │       ├── UserResourceIT.java
│   │       └── InterestTagResourceIT.java
│   └── resources/
│       └── application-test.yml
└── .github/workflows/
    └── ci.yml
```

---

## Task 1: Project Bootstrap

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/devradar/DevRadarApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/docker-compose.yml`
- Create: `backend/.gitignore`

- [ ] **Step 1: Create the Maven `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <groupId>com.devradar</groupId>
    <artifactId>devradar-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>devradar-backend</name>

    <properties>
        <java.version>21</java.version>
        <mapstruct.version>1.6.3</mapstruct.version>
        <jjwt.version>0.12.6</jjwt.version>
        <testcontainers.version>1.20.4</testcontainers.version>
    </properties>

    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>

        <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>org.liquibase</groupId><artifactId>liquibase-core</artifactId></dependency>

        <dependency>
            <groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>${jjwt.version}</version><scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>${jjwt.version}</version><scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId><version>${mapstruct.version}</version>
        </dependency>

        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
        <dependency>
            <groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version><scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId><artifactId>mysql</artifactId>
            <version>${testcontainers.version}</version><scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>${java.version}</source><target>${java.version}</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create main application class**

```java
// backend/src/main/java/com/devradar/DevRadarApplication.java
package com.devradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class DevRadarApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevRadarApplication.class, args);
    }
}
```

- [ ] **Step 3: Create `application.yml`**

```yaml
# backend/src/main/resources/application.yml
spring:
  application:
    name: devradar-backend
  datasource:
    url: jdbc:mysql://localhost:3306/devradar?useSSL=false&serverTimezone=UTC
    username: devradar
    password: devradar
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate.dialect: org.hibernate.dialect.MySQLDialect
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
  data:
    web:
      pageable:
        max-page-size: 100
        default-page-size: 20

jwt:
  secret: ${JWT_SECRET:dev-only-secret-please-override-in-prod-must-be-at-least-256-bits-long-ok}
  access-token-ttl-minutes: 1440
  refresh-token-ttl-days: 30

server:
  port: 8080
  error:
    include-message: always
    include-stacktrace: never
```

- [ ] **Step 4: Create `application-test.yml`**

```yaml
# backend/src/test/resources/application-test.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml

jwt:
  secret: test-only-secret-must-be-at-least-256-bits-long-for-hs256-algorithm-to-work
  access-token-ttl-minutes: 60
  refresh-token-ttl-days: 1
```

- [ ] **Step 5: Create local MySQL `docker-compose.yml`**

```yaml
# backend/docker-compose.yml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: devradar
      MYSQL_USER: devradar
      MYSQL_PASSWORD: devradar
    ports:
      - "3306:3306"
    volumes:
      - devradar-mysql:/var/lib/mysql
volumes:
  devradar-mysql:
```

- [ ] **Step 6: Create `.gitignore`**

```gitignore
# backend/.gitignore
target/
.idea/
*.iml
.vscode/
.DS_Store
.env
```

- [ ] **Step 7: Verify build**

Run: `cd backend && mvn -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 8: Commit**

The git repo is at the project root (not inside `backend/`). From the project root:

```bash
git add backend/
git commit -m "feat(backend): bootstrap Spring Boot 3.5 project with auth dependencies"
```

---

## Task 2: Liquibase — Auth Schema

**Files:**
- Create: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `backend/src/main/resources/db/changelog/001-initial-auth-schema.xml`

- [ ] **Step 1: Create master changelog**

```xml
<!-- backend/src/main/resources/db/changelog/db.changelog-master.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">
    <include file="db/changelog/001-initial-auth-schema.xml"/>
    <include file="db/changelog/002-interest-tags-schema.xml"/>
    <include file="db/changelog/003-audit-log-schema.xml"/>
    <include file="db/changelog/004-seed-interest-tags.xml"/>
</databaseChangeLog>
```

- [ ] **Step 2: Create initial auth schema**

```xml
<!-- backend/src/main/resources/db/changelog/001-initial-auth-schema.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="001-create-users" author="devradar">
        <createTable tableName="users">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="email" type="VARCHAR(255)"><constraints nullable="false" unique="true" uniqueConstraintName="uk_users_email"/></column>
            <column name="password_hash" type="VARCHAR(60)"/>
            <column name="display_name" type="VARCHAR(100)"><constraints nullable="false"/></column>
            <column name="active" type="BOOLEAN" defaultValueBoolean="true"><constraints nullable="false"/></column>
            <column name="created_at" type="TIMESTAMP"><constraints nullable="false"/></column>
            <column name="created_by" type="BIGINT"/>
            <column name="updated_at" type="TIMESTAMP"/>
            <column name="updated_by" type="BIGINT"/>
        </createTable>
    </changeSet>

    <changeSet id="001-create-refresh-tokens" author="devradar">
        <createTable tableName="refresh_tokens">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="user_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="token_hash" type="VARCHAR(64)"><constraints nullable="false" unique="true" uniqueConstraintName="uk_refresh_tokens_hash"/></column>
            <column name="expires_at" type="TIMESTAMP"><constraints nullable="false"/></column>
            <column name="revoked_at" type="TIMESTAMP"/>
            <column name="created_at" type="TIMESTAMP"><constraints nullable="false"/></column>
        </createTable>
        <addForeignKeyConstraint baseTableName="refresh_tokens" baseColumnNames="user_id"
            constraintName="fk_refresh_tokens_user" referencedTableName="users" referencedColumnNames="id"
            onDelete="CASCADE"/>
        <createIndex tableName="refresh_tokens" indexName="ix_refresh_tokens_user">
            <column name="user_id"/>
        </createIndex>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/changelog/
git commit -m "feat(db): add Liquibase auth schema (users + refresh_tokens)"
```

---

## Task 3: Liquibase — Interest Tags Schema + Seed

**Files:**
- Create: `backend/src/main/resources/db/changelog/002-interest-tags-schema.xml`
- Create: `backend/src/main/resources/db/changelog/004-seed-interest-tags.xml`

- [ ] **Step 1: Create interest tags schema**

```xml
<!-- backend/src/main/resources/db/changelog/002-interest-tags-schema.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="002-create-interest-tags" author="devradar">
        <createTable tableName="interest_tags">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="slug" type="VARCHAR(80)"><constraints nullable="false" unique="true" uniqueConstraintName="uk_interest_tags_slug"/></column>
            <column name="display_name" type="VARCHAR(100)"><constraints nullable="false"/></column>
            <column name="category" type="VARCHAR(20)"><constraints nullable="false"/></column>
            <column name="created_at" type="TIMESTAMP"><constraints nullable="false"/></column>
        </createTable>
        <createIndex tableName="interest_tags" indexName="ix_interest_tags_category">
            <column name="category"/>
        </createIndex>
    </changeSet>

    <changeSet id="002-create-user-interests" author="devradar">
        <createTable tableName="user_interests">
            <column name="user_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="interest_tag_id" type="BIGINT"><constraints nullable="false"/></column>
            <column name="created_at" type="TIMESTAMP"><constraints nullable="false"/></column>
        </createTable>
        <addPrimaryKey tableName="user_interests" columnNames="user_id, interest_tag_id" constraintName="pk_user_interests"/>
        <addForeignKeyConstraint baseTableName="user_interests" baseColumnNames="user_id"
            constraintName="fk_user_interests_user" referencedTableName="users" referencedColumnNames="id" onDelete="CASCADE"/>
        <addForeignKeyConstraint baseTableName="user_interests" baseColumnNames="interest_tag_id"
            constraintName="fk_user_interests_tag" referencedTableName="interest_tags" referencedColumnNames="id" onDelete="CASCADE"/>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: Create initial seed of common dev interest tags**

```xml
<!-- backend/src/main/resources/db/changelog/004-seed-interest-tags.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="004-seed-interest-tags" author="devradar">
        <sql>
INSERT INTO interest_tags (slug, display_name, category, created_at) VALUES
  ('java','Java','language',NOW()),('python','Python','language',NOW()),('javascript','JavaScript','language',NOW()),
  ('typescript','TypeScript','language',NOW()),('go','Go','language',NOW()),('rust','Rust','language',NOW()),
  ('kotlin','Kotlin','language',NOW()),('csharp','C#','language',NOW()),('swift','Swift','language',NOW()),
  ('spring_boot','Spring Boot','framework',NOW()),('react','React','framework',NOW()),('next_js','Next.js','framework',NOW()),
  ('vue','Vue','framework',NOW()),('svelte','Svelte','framework',NOW()),('angular','Angular','framework',NOW()),
  ('django','Django','framework',NOW()),('fastapi','FastAPI','framework',NOW()),('rails','Rails','framework',NOW()),
  ('mysql','MySQL','tool',NOW()),('postgres','PostgreSQL','tool',NOW()),('redis','Redis','tool',NOW()),
  ('mongodb','MongoDB','tool',NOW()),('elasticsearch','Elasticsearch','tool',NOW()),('docker','Docker','tool',NOW()),
  ('kubernetes','Kubernetes','tool',NOW()),('terraform','Terraform','tool',NOW()),('aws','AWS','tool',NOW()),
  ('gcp','GCP','tool',NOW()),('azure','Azure','tool',NOW()),
  ('ai_tooling','AI Tooling','topic',NOW()),('llm','LLM','topic',NOW()),('mcp','MCP','topic',NOW()),
  ('security','Security','topic',NOW()),('performance','Performance','topic',NOW()),
  ('devops','DevOps','topic',NOW()),('observability','Observability','topic',NOW()),
  ('frontend','Frontend','topic',NOW()),('backend','Backend','topic',NOW()),
  ('database','Database','topic',NOW()),('testing','Testing','topic',NOW());
        </sql>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/changelog/
git commit -m "feat(db): add interest_tags + user_interests schema with seed data"
```

---

## Task 4: Liquibase — Audit Log Schema

**Files:**
- Create: `backend/src/main/resources/db/changelog/003-audit-log-schema.xml`

- [ ] **Step 1: Create audit_log schema**

```xml
<!-- backend/src/main/resources/db/changelog/003-audit-log-schema.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="003-create-audit-log" author="devradar">
        <createTable tableName="audit_log">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="user_id" type="BIGINT"/>
            <column name="action" type="VARCHAR(80)"><constraints nullable="false"/></column>
            <column name="entity" type="VARCHAR(80)"/>
            <column name="entity_id" type="VARCHAR(80)"/>
            <column name="details" type="JSON"/>
            <column name="created_at" type="TIMESTAMP"><constraints nullable="false"/></column>
        </createTable>
        <createIndex tableName="audit_log" indexName="ix_audit_log_user_created">
            <column name="user_id"/>
            <column name="created_at"/>
        </createIndex>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: Bring up local MySQL and verify migrations apply**

```bash
cd backend && docker compose up -d
# Wait ~10s for MySQL to be ready
mvn spring-boot:run
```
Expected: app starts, Liquibase logs show all 4 changesets applied. Tail logs for `Liquibase: Update has been successful`.

Stop the app (Ctrl+C). Then:

```bash
docker compose exec mysql mysql -udevradar -pdevradar devradar -e "SHOW TABLES;"
```
Expected output includes: `audit_log`, `databasechangelog`, `databasechangeloglock`, `interest_tags`, `refresh_tokens`, `user_interests`, `users`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/changelog/
git commit -m "feat(db): add audit_log schema"
```

---

## Task 5: Domain Entities + AuditEntityListener

**Files:**
- Create: `backend/src/main/java/com/devradar/domain/BaseAuditableEntity.java`
- Create: `backend/src/main/java/com/devradar/domain/AuditEntityListener.java`
- Create: `backend/src/main/java/com/devradar/domain/User.java`
- Create: `backend/src/main/java/com/devradar/domain/RefreshToken.java`
- Create: `backend/src/main/java/com/devradar/domain/InterestCategory.java`
- Create: `backend/src/main/java/com/devradar/domain/InterestTag.java`
- Create: `backend/src/main/java/com/devradar/domain/UserInterest.java`
- Create: `backend/src/main/java/com/devradar/domain/UserInterestId.java`
- Create: `backend/src/main/java/com/devradar/domain/AuditLog.java`

- [ ] **Step 1: Create BaseAuditableEntity**

```java
// backend/src/main/java/com/devradar/domain/BaseAuditableEntity.java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
@EntityListeners(AuditEntityListener.class)
public abstract class BaseAuditableEntity {
    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;
    @Column(name = "created_by", updatable = false)
    protected Long createdBy;
    @Column(name = "updated_at")
    protected Instant updatedAt;
    @Column(name = "updated_by")
    protected Long updatedBy;

    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
}
```

- [ ] **Step 2: Create AuditEntityListener (uses SecurityUtils — created in Task 7)**

```java
// backend/src/main/java/com/devradar/domain/AuditEntityListener.java
package com.devradar.domain;

import com.devradar.security.SecurityUtils;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

public class AuditEntityListener {
    @PrePersist
    public void onCreate(Object entity) {
        if (entity instanceof BaseAuditableEntity e) {
            Instant now = Instant.now();
            e.createdAt = now;
            e.createdBy = SecurityUtils.getCurrentUserId();
        }
    }

    @PreUpdate
    public void onUpdate(Object entity) {
        if (entity instanceof BaseAuditableEntity e) {
            e.updatedAt = Instant.now();
            e.updatedBy = SecurityUtils.getCurrentUserId();
        }
    }
}
```

- [ ] **Step 3: Create User entity**

```java
// backend/src/main/java/com/devradar/domain/User.java
package com.devradar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User extends BaseAuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 60)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
```

- [ ] **Step 4: Create RefreshToken entity**

```java
// backend/src/main/java/com/devradar/domain/RefreshToken.java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
```

- [ ] **Step 5: Create InterestCategory enum**

```java
// backend/src/main/java/com/devradar/domain/InterestCategory.java
package com.devradar.domain;

public enum InterestCategory { language, framework, topic, tool }
```

- [ ] **Step 6: Create InterestTag entity**

```java
// backend/src/main/java/com/devradar/domain/InterestTag.java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "interest_tags")
public class InterestTag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterestCategory category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public InterestCategory getCategory() { return category; }
    public void setCategory(InterestCategory category) { this.category = category; }
}
```

- [ ] **Step 7: Create UserInterestId composite key**

```java
// backend/src/main/java/com/devradar/domain/UserInterestId.java
package com.devradar.domain;

import java.io.Serializable;
import java.util.Objects;

public class UserInterestId implements Serializable {
    private Long userId;
    private Long interestTagId;

    public UserInterestId() {}
    public UserInterestId(Long userId, Long interestTagId) {
        this.userId = userId; this.interestTagId = interestTagId;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserInterestId other)) return false;
        return Objects.equals(userId, other.userId) && Objects.equals(interestTagId, other.interestTagId);
    }
    @Override public int hashCode() { return Objects.hash(userId, interestTagId); }
}
```

- [ ] **Step 8: Create UserInterest entity**

```java
// backend/src/main/java/com/devradar/domain/UserInterest.java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_interests")
@IdClass(UserInterestId.class)
public class UserInterest {
    @Id @Column(name = "user_id") private Long userId;
    @Id @Column(name = "interest_tag_id") private Long interestTagId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public UserInterest() {}
    public UserInterest(Long userId, Long interestTagId) {
        this.userId = userId; this.interestTagId = interestTagId;
    }
    public Long getUserId() { return userId; }
    public Long getInterestTagId() { return interestTagId; }
}
```

- [ ] **Step 9: Create AuditLog entity**

```java
// backend/src/main/java/com/devradar/domain/AuditLog.java
package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(length = 80)
    private String entity;

    @Column(name = "entity_id", length = 80)
    private String entityId;

    @Column(columnDefinition = "JSON")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
```

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/devradar/domain/
git commit -m "feat(domain): add User, RefreshToken, InterestTag, UserInterest, AuditLog entities"
```

---

## Task 6: Domain Exceptions

**Files:**
- Create all exception classes under `backend/src/main/java/com/devradar/domain/exception/`

- [ ] **Step 1: Create the six exception classes**

Each is a simple `RuntimeException` subclass. Same pattern for all:

```java
// backend/src/main/java/com/devradar/domain/exception/UserAlreadyExistsException.java
package com.devradar.domain.exception;
public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String email) { super("User already exists: " + email); }
}
```

```java
// backend/src/main/java/com/devradar/domain/exception/UserNotFoundException.java
package com.devradar.domain.exception;
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) { super("User not found: " + id); }
}
```

```java
// backend/src/main/java/com/devradar/domain/exception/InvalidCredentialsException.java
package com.devradar.domain.exception;
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() { super("Invalid email or password"); }
}
```

```java
// backend/src/main/java/com/devradar/domain/exception/InvalidRefreshTokenException.java
package com.devradar.domain.exception;
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() { super("Refresh token is invalid or expired"); }
}
```

```java
// backend/src/main/java/com/devradar/domain/exception/InterestTagNotFoundException.java
package com.devradar.domain.exception;
public class InterestTagNotFoundException extends RuntimeException {
    public InterestTagNotFoundException(String slug) { super("Interest tag not found: " + slug); }
}
```

```java
// backend/src/main/java/com/devradar/domain/exception/UserNotAuthenticatedException.java
package com.devradar.domain.exception;
public class UserNotAuthenticatedException extends RuntimeException {
    public UserNotAuthenticatedException() { super("User is not authenticated"); }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/devradar/domain/exception/
git commit -m "feat(domain): add domain exception classes"
```

---

## Task 7: Repositories

**Files:**
- Create: `backend/src/main/java/com/devradar/repository/UserRepository.java`
- Create: `backend/src/main/java/com/devradar/repository/RefreshTokenRepository.java`
- Create: `backend/src/main/java/com/devradar/repository/InterestTagRepository.java`
- Create: `backend/src/main/java/com/devradar/repository/UserInterestRepository.java`
- Create: `backend/src/main/java/com/devradar/repository/AuditLogRepository.java`

- [ ] **Step 1: Create UserRepository**

```java
// backend/src/main/java/com/devradar/repository/UserRepository.java
package com.devradar.repository;

import com.devradar.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 2: Create RefreshTokenRepository**

```java
// backend/src/main/java/com/devradar/repository/RefreshTokenRepository.java
package com.devradar.repository;

import com.devradar.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.tokenHash = :hash AND r.revokedAt IS NULL")
    int revokeByTokenHash(String hash, Instant now);
}
```

- [ ] **Step 3: Create InterestTagRepository**

```java
// backend/src/main/java/com/devradar/repository/InterestTagRepository.java
package com.devradar.repository;

import com.devradar.domain.InterestCategory;
import com.devradar.domain.InterestTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface InterestTagRepository extends JpaRepository<InterestTag, Long> {
    Optional<InterestTag> findBySlug(String slug);
    List<InterestTag> findBySlugIn(List<String> slugs);

    @Query("SELECT t FROM InterestTag t WHERE " +
           "(:q IS NULL OR LOWER(t.displayName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(t.slug) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:category IS NULL OR t.category = :category)")
    Page<InterestTag> search(@Param("q") String q, @Param("category") InterestCategory category, Pageable pageable);
}
```

- [ ] **Step 4: Create UserInterestRepository**

```java
// backend/src/main/java/com/devradar/repository/UserInterestRepository.java
package com.devradar.repository;

import com.devradar.domain.UserInterest;
import com.devradar.domain.UserInterestId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface UserInterestRepository extends JpaRepository<UserInterest, UserInterestId> {
    List<UserInterest> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM UserInterest u WHERE u.userId = :userId")
    int deleteAllByUserId(Long userId);
}
```

- [ ] **Step 5: Create AuditLogRepository**

```java
// backend/src/main/java/com/devradar/repository/AuditLogRepository.java
package com.devradar.repository;

import com.devradar.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/devradar/repository/
git commit -m "feat(repo): add JPA repositories for all entities"
```

---

## Task 8: Security — JwtUserDetails, SecurityUtils, JwtTokenProvider

**Files:**
- Create: `backend/src/main/java/com/devradar/security/JwtUserDetails.java`
- Create: `backend/src/main/java/com/devradar/security/SecurityUtils.java`
- Create: `backend/src/main/java/com/devradar/security/JwtTokenProvider.java`
- Create: `backend/src/test/java/com/devradar/security/JwtTokenProviderTest.java`

- [ ] **Step 1: Create JwtUserDetails**

```java
// backend/src/main/java/com/devradar/security/JwtUserDetails.java
package com.devradar.security;

public record JwtUserDetails(Long userId, String email) {}
```

- [ ] **Step 2: Create SecurityUtils**

```java
// backend/src/main/java/com/devradar/security/SecurityUtils.java
package com.devradar.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth.getDetails() instanceof JwtUserDetails d) return d.userId();
        return null;
    }
}
```

- [ ] **Step 3: Write failing test for JwtTokenProvider**

```java
// backend/src/test/java/com/devradar/security/JwtTokenProviderTest.java
package com.devradar.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
        "test-only-secret-must-be-at-least-256-bits-long-for-hs256-algorithm-to-work",
        60
    );

    @Test
    void generateAndParse_roundTrip() {
        String token = provider.generateAccessToken(42L, "alice@example.com");
        JwtUserDetails details = provider.parseAccessToken(token);
        assertThat(details.userId()).isEqualTo(42L);
        assertThat(details.email()).isEqualTo("alice@example.com");
    }

    @Test
    void parse_returnsNullForInvalidToken() {
        assertThat(provider.parseAccessToken("not-a-token")).isNull();
    }
}
```

- [ ] **Step 4: Run test — expect compile failure (no JwtTokenProvider yet)**

Run: `cd backend && mvn -Dtest=JwtTokenProviderTest test`
Expected: compile error.

- [ ] **Step 5: Implement JwtTokenProvider**

```java
// backend/src/main/java/com/devradar/security/JwtTokenProvider.java
package com.devradar.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-token-ttl-minutes}") long ttlMinutes
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    public String generateAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(email)
            .claim("userId", userId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
            .signWith(key)
            .compact();
    }

    public JwtUserDetails parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            Long userId = claims.get("userId", Long.class);
            String email = claims.getSubject();
            return new JwtUserDetails(userId, email);
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 6: Run test — expect PASS**

Run: `cd backend && mvn -Dtest=JwtTokenProviderTest test`
Expected: BUILD SUCCESS, 2/2 passing.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/devradar/security/ src/test/java/com/devradar/security/
git commit -m "feat(security): add JWT token provider, user details, security utils"
```

---

## Task 9: Security — JwtAuthenticationFilter + SecurityConfig

**Files:**
- Create: `backend/src/main/java/com/devradar/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/devradar/config/SecurityConfig.java`

- [ ] **Step 1: Create JwtAuthenticationFilter**

```java
// backend/src/main/java/com/devradar/security/JwtAuthenticationFilter.java
package com.devradar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider provider;

    public JwtAuthenticationFilter(JwtTokenProvider provider) { this.provider = provider; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            JwtUserDetails details = provider.parseAccessToken(token);
            if (details != null) {
                var auth = new UsernamePasswordAuthenticationToken(details.email(), null, List.of());
                auth.setDetails(details);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: Create SecurityConfig**

```java
// backend/src/main/java/com/devradar/config/SecurityConfig.java
package com.devradar.config;

import com.devradar.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) { this.jwtFilter = jwtFilter; }

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
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 3: Verify compile**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/devradar/security/JwtAuthenticationFilter.java src/main/java/com/devradar/config/
git commit -m "feat(security): add JWT auth filter and SecurityConfig (stateless, JWT-only)"
```

---

## Task 10: TagNormalizer Service (TDD)

**Files:**
- Create: `backend/src/test/java/com/devradar/service/TagNormalizerTest.java`
- Create: `backend/src/main/java/com/devradar/service/TagNormalizer.java`

- [ ] **Step 1: Write failing tests**

```java
// backend/src/test/java/com/devradar/service/TagNormalizerTest.java
package com.devradar.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TagNormalizerTest {

    private final TagNormalizer normalizer = new TagNormalizer();

    @Test
    void replaceSpacesAndPunctuationWithUnderscore() {
        assertThat(normalizer.normalize("Spring Boot")).isEqualTo("spring_boot");
        assertThat(normalizer.normalize("React.js")).isEqualTo("react_js");
        assertThat(normalizer.normalize("C++")).isEqualTo("c__");
    }

    @Test
    void caseInsensitiveLowercased() {
        assertThat(normalizer.normalize("REACT")).isEqualTo("react");
        assertThat(normalizer.normalize("React")).isEqualTo("react");
    }

    @Test
    void handlesDotsAndCommasAndHyphens() {
        assertThat(normalizer.normalize("next-js")).isEqualTo("next_js");
        assertThat(normalizer.normalize("a,b,c")).isEqualTo("a_b_c");
        assertThat(normalizer.normalize(".net")).isEqualTo("_net");
    }

    @Test
    void trimsWhitespace() {
        assertThat(normalizer.normalize("  java  ")).isEqualTo("java");
    }

    @Test
    void emptyOrNullReturnsEmpty() {
        assertThat(normalizer.normalize("")).isEqualTo("");
        assertThat(normalizer.normalize(null)).isEqualTo("");
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

Run: `cd backend && mvn -Dtest=TagNormalizerTest test`
Expected: compile error.

- [ ] **Step 3: Implement TagNormalizer**

```java
// backend/src/main/java/com/devradar/service/TagNormalizer.java
package com.devradar.service;

import org.springframework.stereotype.Component;

@Component
public class TagNormalizer {
    public String normalize(String input) {
        if (input == null) return "";
        String trimmed = input.trim().toLowerCase();
        return trimmed.replaceAll("[\\s.,\\-+]", "_");
    }
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `cd backend && mvn -Dtest=TagNormalizerTest test`
Expected: 5/5 passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/devradar/service/TagNormalizer.java src/test/java/com/devradar/service/TagNormalizerTest.java
git commit -m "feat(service): add TagNormalizer with normalization rules"
```

---

## Task 11: AuthService — Register (TDD)

**Files:**
- Create: `backend/src/main/java/com/devradar/service/AuthService.java` (start)
- Create: `backend/src/test/java/com/devradar/service/AuthServiceTest.java` (start)

- [ ] **Step 1: Write failing test for register**

```java
// backend/src/test/java/com/devradar/service/AuthServiceTest.java
package com.devradar.service;

import com.devradar.domain.User;
import com.devradar.domain.exception.UserAlreadyExistsException;
import com.devradar.repository.RefreshTokenRepository;
import com.devradar.repository.UserRepository;
import com.devradar.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    UserRepository userRepo;
    RefreshTokenRepository refreshRepo;
    JwtTokenProvider jwt;
    AuthService service;

    @BeforeEach
    void setup() {
        userRepo = mock(UserRepository.class);
        refreshRepo = mock(RefreshTokenRepository.class);
        jwt = mock(JwtTokenProvider.class);
        service = new AuthService(userRepo, refreshRepo, new BCryptPasswordEncoder(12), jwt, 1);
    }

    @Test
    void register_persistsUserWithHashedPassword() {
        when(userRepo.existsByEmail("a@b.c")).thenReturn(false);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            // simulate ID assignment
            try { var f = User.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, 1L); } catch (Exception e) {}
            return u;
        });

        User created = service.register("a@b.c", "Password1!", "Alice");

        assertThat(created.getEmail()).isEqualTo("a@b.c");
        assertThat(created.getDisplayName()).isEqualTo("Alice");
        assertThat(created.getPasswordHash()).isNotEqualTo("Password1!");
        assertThat(created.getPasswordHash()).startsWith("$2");
    }

    @Test
    void register_throwsWhenEmailExists() {
        when(userRepo.existsByEmail("a@b.c")).thenReturn(true);
        assertThatThrownBy(() -> service.register("a@b.c", "x", "x"))
            .isInstanceOf(UserAlreadyExistsException.class);
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

Run: `cd backend && mvn -Dtest=AuthServiceTest test`
Expected: compile error.

- [ ] **Step 3: Create initial AuthService with register only**

```java
// backend/src/main/java/com/devradar/service/AuthService.java
package com.devradar.service;

import com.devradar.domain.User;
import com.devradar.domain.exception.UserAlreadyExistsException;
import com.devradar.repository.RefreshTokenRepository;
import com.devradar.repository.UserRepository;
import com.devradar.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwt;
    private final long refreshTtlDays;

    public AuthService(
        UserRepository userRepo,
        RefreshTokenRepository refreshRepo,
        PasswordEncoder encoder,
        JwtTokenProvider jwt,
        @Value("${jwt.refresh-token-ttl-days}") long refreshTtlDays
    ) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Transactional
    public User register(String email, String password, String displayName) {
        if (userRepo.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email);
        }
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(encoder.encode(password));
        return userRepo.save(user);
    }
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `cd backend && mvn -Dtest=AuthServiceTest test`
Expected: 2/2 passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/devradar/service/AuthService.java src/test/java/com/devradar/service/AuthServiceTest.java
git commit -m "feat(service): AuthService.register with bcrypt hashing"
```

---

## Task 12: AuthService — Login + Refresh + Logout (TDD)

**Files:**
- Modify: `backend/src/main/java/com/devradar/service/AuthService.java`
- Modify: `backend/src/test/java/com/devradar/service/AuthServiceTest.java`

- [ ] **Step 1: Add failing tests for login, refresh, logout**

Append to `AuthServiceTest.java`:

```java
    @Test
    void login_returnsTokens_whenCredentialsValid() {
        User u = makeStoredUser("a@b.c", "Password1!");
        when(userRepo.findByEmail("a@b.c")).thenReturn(Optional.of(u));
        when(jwt.generateAccessToken(1L, "a@b.c")).thenReturn("ACCESS");

        AuthService.AuthResult r = service.login("a@b.c", "Password1!");

        assertThat(r.accessToken()).isEqualTo("ACCESS");
        assertThat(r.refreshToken()).isNotBlank();
        verify(refreshRepo).save(any());
    }

    @Test
    void login_throws_whenPasswordWrong() {
        User u = makeStoredUser("a@b.c", "RealPassword!");
        when(userRepo.findByEmail("a@b.c")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.login("a@b.c", "Wrong"))
            .isInstanceOf(com.devradar.domain.exception.InvalidCredentialsException.class);
    }

    @Test
    void login_throws_whenUserNotFound() {
        when(userRepo.findByEmail("nope@b.c")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("nope@b.c", "x"))
            .isInstanceOf(com.devradar.domain.exception.InvalidCredentialsException.class);
    }

    private User makeStoredUser(String email, String plainPassword) {
        User u = new User();
        u.setEmail(email);
        u.setDisplayName("Alice");
        u.setPasswordHash(new BCryptPasswordEncoder(12).encode(plainPassword));
        try { var f = User.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, 1L); } catch (Exception e) {}
        u.setActive(true);
        return u;
    }
```

- [ ] **Step 2: Run — expect compile fail**

Run: `cd backend && mvn -Dtest=AuthServiceTest test`
Expected: compile error (no AuthResult, no login method).

- [ ] **Step 3: Add login + refresh + logout to AuthService**

Append to `AuthService.java`:

```java
    public record AuthResult(String accessToken, String refreshToken) {}

    @Transactional
    public AuthResult login(String email, String password) {
        User u = userRepo.findByEmail(email)
            .orElseThrow(com.devradar.domain.exception.InvalidCredentialsException::new);
        if (!u.isActive() || !encoder.matches(password, u.getPasswordHash())) {
            throw new com.devradar.domain.exception.InvalidCredentialsException();
        }
        return issueTokens(u);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        var rt = refreshRepo.findByTokenHash(hash)
            .orElseThrow(com.devradar.domain.exception.InvalidRefreshTokenException::new);
        if (!rt.isActive()) {
            throw new com.devradar.domain.exception.InvalidRefreshTokenException();
        }
        // rotate
        refreshRepo.revokeByTokenHash(hash, java.time.Instant.now());
        User u = userRepo.findById(rt.getUserId())
            .orElseThrow(com.devradar.domain.exception.InvalidRefreshTokenException::new);
        return issueTokens(u);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshRepo.revokeByTokenHash(sha256(rawRefreshToken), java.time.Instant.now());
    }

    private AuthResult issueTokens(User u) {
        String access = jwt.generateAccessToken(u.getId(), u.getEmail());
        String raw = java.util.UUID.randomUUID().toString() + "-" + java.util.UUID.randomUUID();
        String hash = sha256(raw);

        com.devradar.domain.RefreshToken rt = new com.devradar.domain.RefreshToken();
        rt.setUserId(u.getId());
        rt.setTokenHash(hash);
        rt.setExpiresAt(java.time.Instant.now().plus(refreshTtlDays, java.time.temporal.ChronoUnit.DAYS));
        refreshRepo.save(rt);

        return new AuthResult(access, raw);
    }

    private static String sha256(String input) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
```

- [ ] **Step 4: Run — expect PASS**

Run: `cd backend && mvn -Dtest=AuthServiceTest test`
Expected: 5/5 passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/devradar/service/AuthService.java src/test/java/com/devradar/service/AuthServiceTest.java
git commit -m "feat(service): AuthService.login/refresh/logout with rotated refresh tokens"
```

---

## Task 13: Auth REST DTOs + Controller (TDD via integration test)

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/dto/RegisterRequestDTO.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/LoginRequestDTO.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/LoginResponseDTO.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/RefreshRequestDTO.java`
- Create: `backend/src/main/java/com/devradar/web/rest/AuthResource.java`
- Create: `backend/src/test/java/com/devradar/AbstractIntegrationTest.java`
- Create: `backend/src/test/java/com/devradar/web/rest/AuthResourceIT.java`

- [ ] **Step 1: Create the four DTOs**

```java
// backend/src/main/java/com/devradar/web/rest/dto/RegisterRequestDTO.java
package com.devradar.web.rest.dto;
import jakarta.validation.constraints.*;

public record RegisterRequestDTO(
    @Email @NotBlank @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Size(max = 100) String displayName
) {}
```

```java
// backend/src/main/java/com/devradar/web/rest/dto/LoginRequestDTO.java
package com.devradar.web.rest.dto;
import jakarta.validation.constraints.*;
public record LoginRequestDTO(
    @Email @NotBlank String email,
    @NotBlank String password
) {}
```

```java
// backend/src/main/java/com/devradar/web/rest/dto/LoginResponseDTO.java
package com.devradar.web.rest.dto;
public record LoginResponseDTO(String accessToken, String refreshToken) {}
```

```java
// backend/src/main/java/com/devradar/web/rest/dto/RefreshRequestDTO.java
package com.devradar.web.rest.dto;
import jakarta.validation.constraints.NotBlank;
public record RefreshRequestDTO(@NotBlank String refreshToken) {}
```

- [ ] **Step 2: Create AbstractIntegrationTest with Testcontainers**

```java
// backend/src/test/java/com/devradar/AbstractIntegrationTest.java
package com.devradar;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("devradar_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }
}
```

- [ ] **Step 3: Write failing AuthResource integration test**

```java
// backend/src/test/java/com/devradar/web/rest/AuthResourceIT.java
package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc
class AuthResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void register_returns201_andLoginReturnsTokens() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
            "email", "alice@example.com",
            "password", "Password1!",
            "displayName", "Alice"
        ));

        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated());

        String loginBody = json.writeValueAsString(java.util.Map.of(
            "email", "alice@example.com",
            "password", "Password1!"
        ));

        mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void register_returns409_whenEmailDuplicated() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
            "email", "dup@example.com",
            "password", "Password1!",
            "displayName", "Dup"
        ));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void login_returns401_whenPasswordWrong() throws Exception {
        String reg = json.writeValueAsString(java.util.Map.of(
            "email", "wrong@example.com",
            "password", "RealPass1!",
            "displayName", "X"
        ));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(reg))
            .andExpect(status().isCreated());

        String bad = json.writeValueAsString(java.util.Map.of(
            "email", "wrong@example.com",
            "password", "BadPass!"
        ));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(bad))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 4: Run — expect failures (no controller, no exception handler)**

Run: `cd backend && mvn -Dtest=AuthResourceIT test`
Expected: tests fail or 404s.

- [ ] **Step 5: Implement AuthResource**

```java
// backend/src/main/java/com/devradar/web/rest/AuthResource.java
package com.devradar.web.rest;

import com.devradar.service.AuthService;
import com.devradar.web.rest.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthResource {

    private final AuthService auth;

    public AuthResource(AuthService auth) { this.auth = auth; }

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
}
```

- [ ] **Step 6: Verify but exception handler is not yet wired (do Task 14 now then re-run)**

Run: `cd backend && mvn -Dtest=AuthResourceIT test`
Expected: register/login pass; 409/401 tests fail (default Spring returns 500 for uncaught exceptions). Proceed to Task 14 to wire the exception handler, then re-run.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/devradar/web/rest/AuthResource.java src/main/java/com/devradar/web/rest/dto/ src/test/java/com/devradar/AbstractIntegrationTest.java src/test/java/com/devradar/web/rest/AuthResourceIT.java
git commit -m "feat(web): add /api/auth endpoints (register, login, refresh, logout)"
```

---

## Task 14: GlobalExceptionHandler + ErrorResponse

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/exception/ErrorResponse.java`
- Create: `backend/src/main/java/com/devradar/web/rest/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create ErrorResponse**

```java
// backend/src/main/java/com/devradar/web/rest/exception/ErrorResponse.java
package com.devradar.web.rest.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(int status, String message, Instant timestamp, Map<String, String> errors) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, Instant.now(), null);
    }
    public static ErrorResponse of(int status, String message, Map<String, String> errors) {
        return new ErrorResponse(status, message, Instant.now(), errors);
    }
}
```

- [ ] **Step 2: Create GlobalExceptionHandler**

```java
// backend/src/main/java/com/devradar/web/rest/exception/GlobalExceptionHandler.java
package com.devradar.web.rest.exception;

import com.devradar.domain.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> userExists(UserAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> userNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> invalidCreds(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(401, e.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> invalidRefresh(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(401, e.getMessage()));
    }

    @ExceptionHandler(InterestTagNotFoundException.class)
    public ResponseEntity<ErrorResponse> tagNotFound(InterestTagNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, e.getMessage()));
    }

    @ExceptionHandler(UserNotAuthenticatedException.class)
    public ResponseEntity<ErrorResponse> notAuth(UserNotAuthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(401, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errors.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Validation failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> generic(Exception e) {
        return ResponseEntity.internalServerError()
            .body(ErrorResponse.of(500, "An unexpected error occurred"));
    }
}
```

- [ ] **Step 3: Re-run AuthResourceIT — expect 5/5 PASS**

Run: `cd backend && mvn -Dtest=AuthResourceIT test`
Expected: all assertions pass; 409 and 401 statuses now correct.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/devradar/web/rest/exception/
git commit -m "feat(web): add GlobalExceptionHandler with structured ErrorResponse"
```

---

## Task 15: User REST — GET /me and PATCH /me

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/dto/UserResponseDTO.java`
- Create: `backend/src/main/java/com/devradar/web/rest/dto/UserUpdateDTO.java`
- Create: `backend/src/main/java/com/devradar/service/mapper/UserMapper.java`
- Create: `backend/src/main/java/com/devradar/service/UserService.java`
- Create: `backend/src/main/java/com/devradar/service/application/UserApplicationService.java`
- Create: `backend/src/main/java/com/devradar/web/rest/UserResource.java`
- Create: `backend/src/test/java/com/devradar/web/rest/UserResourceIT.java`

- [ ] **Step 1: Create DTOs**

```java
// backend/src/main/java/com/devradar/web/rest/dto/UserResponseDTO.java
package com.devradar.web.rest.dto;
public record UserResponseDTO(Long id, String email, String displayName, boolean active) {}
```

```java
// backend/src/main/java/com/devradar/web/rest/dto/UserUpdateDTO.java
package com.devradar.web.rest.dto;
import jakarta.validation.constraints.*;
public record UserUpdateDTO(@NotBlank @Size(max = 100) String displayName) {}
```

- [ ] **Step 2: Create UserMapper**

```java
// backend/src/main/java/com/devradar/service/mapper/UserMapper.java
package com.devradar.service.mapper;

import com.devradar.domain.User;
import com.devradar.web.rest.dto.UserResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponseDTO toDto(User user);
}
```

- [ ] **Step 3: Create UserService**

```java
// backend/src/main/java/com/devradar/service/UserService.java
package com.devradar.service;

import com.devradar.domain.User;
import com.devradar.domain.exception.UserNotFoundException;
import com.devradar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository repo;
    public UserService(UserRepository repo) { this.repo = repo; }

    public User findById(Long id) {
        return repo.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    public User updateDisplayName(Long id, String displayName) {
        User u = findById(id);
        u.setDisplayName(displayName);
        return u;
    }
}
```

- [ ] **Step 4: Create UserApplicationService**

```java
// backend/src/main/java/com/devradar/service/application/UserApplicationService.java
package com.devradar.service.application;

import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.security.SecurityUtils;
import com.devradar.service.UserService;
import com.devradar.service.mapper.UserMapper;
import com.devradar.web.rest.dto.UserResponseDTO;
import org.springframework.stereotype.Service;

@Service
public class UserApplicationService {
    private final UserService users;
    private final UserMapper mapper;

    public UserApplicationService(UserService users, UserMapper mapper) {
        this.users = users; this.mapper = mapper;
    }

    public UserResponseDTO me() {
        Long id = SecurityUtils.getCurrentUserId();
        if (id == null) throw new UserNotAuthenticatedException();
        return mapper.toDto(users.findById(id));
    }

    public UserResponseDTO updateMe(String displayName) {
        Long id = SecurityUtils.getCurrentUserId();
        if (id == null) throw new UserNotAuthenticatedException();
        return mapper.toDto(users.updateDisplayName(id, displayName));
    }
}
```

- [ ] **Step 5: Create UserResource**

```java
// backend/src/main/java/com/devradar/web/rest/UserResource.java
package com.devradar.web.rest;

import com.devradar.service.application.UserApplicationService;
import com.devradar.web.rest.dto.UserResponseDTO;
import com.devradar.web.rest.dto.UserUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserResource {
    private final UserApplicationService app;
    public UserResource(UserApplicationService app) { this.app = app; }

    @GetMapping("/me")
    public UserResponseDTO me() { return app.me(); }

    @PatchMapping("/me")
    public UserResponseDTO updateMe(@Valid @RequestBody UserUpdateDTO body) {
        return app.updateMe(body.displayName());
    }
}
```

- [ ] **Step 6: Write integration test**

```java
// backend/src/test/java/com/devradar/web/rest/UserResourceIT.java
package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class UserResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String registerAndLogin(String email) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
            "email", email, "password", "Password1!", "displayName", "Alice"
        ));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated());

        String resp = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "Password1!"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(resp);
        return node.get("accessToken").asText();
    }

    @Test
    void me_returnsAuthenticatedUser() throws Exception {
        String token = registerAndLogin("me1@example.com");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("me1@example.com"))
            .andExpect(jsonPath("$.displayName").value("Alice"));
    }

    @Test
    void me_returns401_whenNoToken() throws Exception {
        mvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMe_changesDisplayName() throws Exception {
        String token = registerAndLogin("me2@example.com");
        mvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("displayName", "Bob"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Bob"));
    }
}
```

- [ ] **Step 7: Run — expect PASS**

Run: `cd backend && mvn -Dtest=UserResourceIT test`
Expected: 3/3 passing.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/devradar/web/rest/UserResource.java src/main/java/com/devradar/service/UserService.java src/main/java/com/devradar/service/application/UserApplicationService.java src/main/java/com/devradar/service/mapper/UserMapper.java src/main/java/com/devradar/web/rest/dto/UserResponseDTO.java src/main/java/com/devradar/web/rest/dto/UserUpdateDTO.java src/test/java/com/devradar/web/rest/UserResourceIT.java
git commit -m "feat(user): add /api/users/me GET and PATCH endpoints"
```

---

## Task 16: Interest Tags — Catalog (GET /api/interest-tags)

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/dto/InterestTagResponseDTO.java`
- Create: `backend/src/main/java/com/devradar/service/mapper/InterestTagMapper.java`
- Create: `backend/src/main/java/com/devradar/service/InterestTagService.java`
- Create: `backend/src/main/java/com/devradar/web/rest/InterestTagResource.java`
- Create: `backend/src/test/java/com/devradar/web/rest/InterestTagResourceIT.java`

- [ ] **Step 1: Create DTO**

```java
// backend/src/main/java/com/devradar/web/rest/dto/InterestTagResponseDTO.java
package com.devradar.web.rest.dto;

import com.devradar.domain.InterestCategory;
public record InterestTagResponseDTO(Long id, String slug, String displayName, InterestCategory category) {}
```

- [ ] **Step 2: Create mapper**

```java
// backend/src/main/java/com/devradar/service/mapper/InterestTagMapper.java
package com.devradar.service.mapper;

import com.devradar.domain.InterestTag;
import com.devradar.web.rest.dto.InterestTagResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InterestTagMapper {
    InterestTagResponseDTO toDto(InterestTag tag);
}
```

- [ ] **Step 3: Create service**

```java
// backend/src/main/java/com/devradar/service/InterestTagService.java
package com.devradar.service;

import com.devradar.domain.InterestCategory;
import com.devradar.domain.InterestTag;
import com.devradar.repository.InterestTagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class InterestTagService {
    private final InterestTagRepository repo;
    public InterestTagService(InterestTagRepository repo) { this.repo = repo; }

    public Page<InterestTag> search(String q, InterestCategory category, Pageable pageable) {
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();
        return repo.search(trimmed, category, pageable);
    }
}
```

- [ ] **Step 4: Create resource**

```java
// backend/src/main/java/com/devradar/web/rest/InterestTagResource.java
package com.devradar.web.rest;

import com.devradar.domain.InterestCategory;
import com.devradar.service.InterestTagService;
import com.devradar.service.mapper.InterestTagMapper;
import com.devradar.web.rest.dto.InterestTagResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interest-tags")
public class InterestTagResource {

    private final InterestTagService service;
    private final InterestTagMapper mapper;

    public InterestTagResource(InterestTagService service, InterestTagMapper mapper) {
        this.service = service; this.mapper = mapper;
    }

    @GetMapping
    public Page<InterestTagResponseDTO> search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) InterestCategory category,
        Pageable pageable
    ) {
        return service.search(q, category, pageable).map(mapper::toDto);
    }
}
```

- [ ] **Step 5: Integration test**

```java
// backend/src/test/java/com/devradar/web/rest/InterestTagResourceIT.java
package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class InterestTagResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void list_returnsSeededTags() throws Exception {
        mvc.perform(get("/api/interest-tags?size=100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.slug=='spring_boot')]").exists())
            .andExpect(jsonPath("$.content[?(@.slug=='react')]").exists());
    }

    @Test
    void list_filtersByCategory() throws Exception {
        mvc.perform(get("/api/interest-tags?category=language&size=100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.slug=='java')]").exists())
            .andExpect(jsonPath("$.content[?(@.slug=='spring_boot')]").doesNotExist());
    }

    @Test
    void list_searchByQuery() throws Exception {
        mvc.perform(get("/api/interest-tags?q=spring&size=100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.slug=='spring_boot')]").exists())
            .andExpect(jsonPath("$.content[?(@.slug=='react')]").doesNotExist());
    }
}
```

- [ ] **Step 6: Run — expect PASS**

Run: `cd backend && mvn -Dtest=InterestTagResourceIT test`
Expected: 3/3 passing.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/devradar/web/rest/InterestTagResource.java src/main/java/com/devradar/service/InterestTagService.java src/main/java/com/devradar/service/mapper/InterestTagMapper.java src/main/java/com/devradar/web/rest/dto/InterestTagResponseDTO.java src/test/java/com/devradar/web/rest/InterestTagResourceIT.java
git commit -m "feat(interests): add /api/interest-tags catalog with search + category filter"
```

---

## Task 17: User Interests — PUT /api/users/me/interests

**Files:**
- Create: `backend/src/main/java/com/devradar/web/rest/dto/UserInterestsUpdateDTO.java`
- Create: `backend/src/main/java/com/devradar/service/UserInterestService.java`
- Create: `backend/src/main/java/com/devradar/service/application/InterestApplicationService.java`
- Modify: `backend/src/main/java/com/devradar/web/rest/UserResource.java` (add interests endpoints)
- Modify: `backend/src/test/java/com/devradar/web/rest/UserResourceIT.java` (add interests tests)

- [ ] **Step 1: Create DTO**

```java
// backend/src/main/java/com/devradar/web/rest/dto/UserInterestsUpdateDTO.java
package com.devradar.web.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UserInterestsUpdateDTO(@NotNull @Size(min = 1, max = 50) List<String> tagSlugs) {}
```

- [ ] **Step 2: Create UserInterestService**

```java
// backend/src/main/java/com/devradar/service/UserInterestService.java
package com.devradar.service;

import com.devradar.domain.InterestTag;
import com.devradar.domain.UserInterest;
import com.devradar.domain.exception.InterestTagNotFoundException;
import com.devradar.repository.InterestTagRepository;
import com.devradar.repository.UserInterestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserInterestService {

    private final UserInterestRepository userInterestRepo;
    private final InterestTagRepository tagRepo;

    public UserInterestService(UserInterestRepository userInterestRepo, InterestTagRepository tagRepo) {
        this.userInterestRepo = userInterestRepo; this.tagRepo = tagRepo;
    }

    public List<InterestTag> findInterestsForUser(Long userId) {
        var interests = userInterestRepo.findByUserId(userId);
        if (interests.isEmpty()) return List.of();
        return tagRepo.findAllById(interests.stream().map(UserInterest::getInterestTagId).toList());
    }

    @Transactional
    public List<InterestTag> setInterestsForUser(Long userId, List<String> slugs) {
        Set<String> distinct = new HashSet<>(slugs);
        List<InterestTag> tags = tagRepo.findBySlugIn(List.copyOf(distinct));
        if (tags.size() != distinct.size()) {
            Set<String> found = new HashSet<>();
            for (InterestTag t : tags) found.add(t.getSlug());
            for (String s : distinct) if (!found.contains(s)) throw new InterestTagNotFoundException(s);
        }
        userInterestRepo.deleteAllByUserId(userId);
        for (InterestTag t : tags) userInterestRepo.save(new UserInterest(userId, t.getId()));
        return tags;
    }
}
```

- [ ] **Step 3: Create InterestApplicationService**

```java
// backend/src/main/java/com/devradar/service/application/InterestApplicationService.java
package com.devradar.service.application;

import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.security.SecurityUtils;
import com.devradar.service.UserInterestService;
import com.devradar.service.mapper.InterestTagMapper;
import com.devradar.web.rest.dto.InterestTagResponseDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InterestApplicationService {

    private final UserInterestService service;
    private final InterestTagMapper mapper;

    public InterestApplicationService(UserInterestService service, InterestTagMapper mapper) {
        this.service = service; this.mapper = mapper;
    }

    public List<InterestTagResponseDTO> myInterests() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return service.findInterestsForUser(uid).stream().map(mapper::toDto).toList();
    }

    public List<InterestTagResponseDTO> setMyInterests(List<String> slugs) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return service.setInterestsForUser(uid, slugs).stream().map(mapper::toDto).toList();
    }
}
```

- [ ] **Step 4: Replace UserResource.java with the full updated version**

```java
// backend/src/main/java/com/devradar/web/rest/UserResource.java
package com.devradar.web.rest;

import com.devradar.service.application.InterestApplicationService;
import com.devradar.service.application.UserApplicationService;
import com.devradar.web.rest.dto.InterestTagResponseDTO;
import com.devradar.web.rest.dto.UserInterestsUpdateDTO;
import com.devradar.web.rest.dto.UserResponseDTO;
import com.devradar.web.rest.dto.UserUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserResource {

    private final UserApplicationService app;
    private final InterestApplicationService interests;

    public UserResource(UserApplicationService app, InterestApplicationService interests) {
        this.app = app;
        this.interests = interests;
    }

    @GetMapping("/me")
    public UserResponseDTO me() { return app.me(); }

    @PatchMapping("/me")
    public UserResponseDTO updateMe(@Valid @RequestBody UserUpdateDTO body) {
        return app.updateMe(body.displayName());
    }

    @GetMapping("/me/interests")
    public List<InterestTagResponseDTO> myInterests() { return interests.myInterests(); }

    @PutMapping("/me/interests")
    public List<InterestTagResponseDTO> setMyInterests(@Valid @RequestBody UserInterestsUpdateDTO body) {
        return interests.setMyInterests(body.tagSlugs());
    }
}
```

- [ ] **Step 5: Add tests to UserResourceIT**

Append:

```java
    @Test
    void interests_putAndGet_roundTrip() throws Exception {
        String token = registerAndLogin("interests@example.com");

        // PUT
        mvc.perform(put("/api/users/me/interests")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of(
                    "tagSlugs", java.util.List.of("spring_boot", "react", "mysql")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3));

        // GET
        mvc.perform(get("/api/users/me/interests").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[?(@.slug=='spring_boot')]").exists());
    }

    @Test
    void interests_put_returns404_whenSlugUnknown() throws Exception {
        String token = registerAndLogin("badinterests@example.com");
        mvc.perform(put("/api/users/me/interests")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of(
                    "tagSlugs", java.util.List.of("not_a_real_tag")))))
            .andExpect(status().isNotFound());
    }
```

- [ ] **Step 6: Run — expect PASS**

Run: `cd backend && mvn -Dtest=UserResourceIT test`
Expected: 5/5 passing (3 prior + 2 new).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/devradar/service/UserInterestService.java src/main/java/com/devradar/service/application/InterestApplicationService.java src/main/java/com/devradar/web/rest/dto/UserInterestsUpdateDTO.java src/main/java/com/devradar/web/rest/UserResource.java src/test/java/com/devradar/web/rest/UserResourceIT.java
git commit -m "feat(user): add GET/PUT /api/users/me/interests"
```

---

## Task 18: AuditLogService

**Files:**
- Create: `backend/src/main/java/com/devradar/service/AuditLogService.java`
- Modify: `backend/src/main/java/com/devradar/service/AuthService.java` (log register, login, refresh, logout)

- [ ] **Step 1: Create AuditLogService**

```java
// backend/src/main/java/com/devradar/service/AuditLogService.java
package com.devradar.service;

import com.devradar.domain.AuditLog;
import com.devradar.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private final AuditLogRepository repo;
    public AuditLogService(AuditLogRepository repo) { this.repo = repo; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entity, String entityId, String detailsJson) {
        AuditLog a = new AuditLog();
        a.setUserId(userId);
        a.setAction(action);
        a.setEntity(entity);
        a.setEntityId(entityId);
        a.setDetails(detailsJson);
        repo.save(a);
    }
}
```

- [ ] **Step 2: Wire AuditLogService into AuthService — full updated AuthService**

Replace `AuthService.java` with this version (adds audit field, constructor param, and three audit calls):

```java
// backend/src/main/java/com/devradar/service/AuthService.java
package com.devradar.service;

import com.devradar.domain.RefreshToken;
import com.devradar.domain.User;
import com.devradar.domain.exception.InvalidCredentialsException;
import com.devradar.domain.exception.InvalidRefreshTokenException;
import com.devradar.domain.exception.UserAlreadyExistsException;
import com.devradar.repository.RefreshTokenRepository;
import com.devradar.repository.UserRepository;
import com.devradar.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwt;
    private final AuditLogService audit;
    private final long refreshTtlDays;

    public AuthService(
        UserRepository userRepo,
        RefreshTokenRepository refreshRepo,
        PasswordEncoder encoder,
        JwtTokenProvider jwt,
        AuditLogService audit,
        @Value("${jwt.refresh-token-ttl-days}") long refreshTtlDays
    ) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.refreshTtlDays = refreshTtlDays;
    }

    public record AuthResult(String accessToken, String refreshToken) {}

    @Transactional
    public User register(String email, String password, String displayName) {
        if (userRepo.existsByEmail(email)) throw new UserAlreadyExistsException(email);
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(encoder.encode(password));
        User saved = userRepo.save(user);
        audit.log(saved.getId(), "USER_REGISTERED", "user", String.valueOf(saved.getId()), null);
        return saved;
    }

    @Transactional
    public AuthResult login(String email, String password) {
        User u = userRepo.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!u.isActive() || !encoder.matches(password, u.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        AuthResult r = issueTokens(u);
        audit.log(u.getId(), "USER_LOGIN", "user", String.valueOf(u.getId()), null);
        return r;
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        var rt = refreshRepo.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);
        if (!rt.isActive()) throw new InvalidRefreshTokenException();
        refreshRepo.revokeByTokenHash(hash, Instant.now());
        User u = userRepo.findById(rt.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(u);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshRepo.revokeByTokenHash(sha256(rawRefreshToken), Instant.now());
        audit.log(null, "USER_LOGOUT", null, null, null);
    }

    private AuthResult issueTokens(User u) {
        String access = jwt.generateAccessToken(u.getId(), u.getEmail());
        String raw = UUID.randomUUID().toString() + "-" + UUID.randomUUID();
        String hash = sha256(raw);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(u.getId());
        rt.setTokenHash(hash);
        rt.setExpiresAt(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS));
        refreshRepo.save(rt);
        return new AuthResult(access, raw);
    }

    private static String sha256(String input) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
```

Then update `AuthServiceTest.java` `setup()` method to pass the new constructor arg:

```java
    @BeforeEach
    void setup() {
        userRepo = mock(UserRepository.class);
        refreshRepo = mock(RefreshTokenRepository.class);
        jwt = mock(JwtTokenProvider.class);
        AuditLogService audit = mock(AuditLogService.class);
        service = new AuthService(userRepo, refreshRepo, new BCryptPasswordEncoder(12), jwt, audit, 1);
    }
```

- [ ] **Step 3: Verify integration tests still pass**

Run: `cd backend && mvn test`
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/devradar/service/AuditLogService.java src/main/java/com/devradar/service/AuthService.java
git commit -m "feat(audit): record register/login/logout events to audit_log"
```

---

## Task 19: GitHub Actions CI

**Files:**
- Create: `backend/.github/workflows/ci.yml` (note: this should be at repo root, `.github/workflows/ci.yml` — adjust path if backend is in a subdir)

- [ ] **Step 1: Create CI workflow**

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
          cache: maven
      - name: Verify backend
        working-directory: backend
        run: mvn -B verify
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow running backend tests"
```

---

## Task 20: README — Plan 1 deliverable

**Files:**
- Create: `backend/README.md`

- [ ] **Step 1: Create backend README**

```markdown
# Dev Radar — Backend

Foundation layer for the Dev Radar MVP (sub-project 1, plan 1).

## Stack
Java 21, Spring Boot 3.5+, MySQL 8, Liquibase, Spring Security (JWT), MapStruct, JUnit 5 + Testcontainers.

## Local development

```bash
# Start MySQL
docker compose up -d

# Run app
mvn spring-boot:run

# App runs at http://localhost:8080
```

## Tests

```bash
mvn test
```

Integration tests use Testcontainers MySQL — Docker required.

## What this plan ships

- Email + password registration
- Login (returns JWT access token + opaque refresh token)
- Refresh token rotation (each refresh issues a new pair, revokes the old)
- Logout (revokes refresh)
- `GET /api/users/me`, `PATCH /api/users/me`
- Interest tags catalog: `GET /api/interest-tags?q=&category=`
- Set/get user interests: `GET/PUT /api/users/me/interests`
- Audit log of auth events
- GlobalExceptionHandler returning structured `ErrorResponse`

## What this plan does NOT ship

- Anything AI-related (deferred to Plan 3)
- Ingestion, sources, source items (Plan 2)
- GitHub OAuth + Auto-PR (Plan 4)
- MCP server (Plan 5)
- Eval harness (Plan 6)
- Observability dashboard (Plan 7)
- Frontend (Plan 8)
```

- [ ] **Step 2: Commit**

```bash
git add backend/README.md
git commit -m "docs(backend): add README documenting Plan 1 surface"
```

---

## Plan 1 Done — End-to-End Verification

- [ ] **Step 1: Run the full test suite**

Run: `cd backend && mvn verify`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Smoke test against local MySQL**

```bash
docker compose up -d
mvn spring-boot:run &
sleep 10

# Register
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@example.com","password":"Password1!","displayName":"Smoke"}'
# Expected: 201

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@example.com","password":"Password1!"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
echo "TOKEN=$TOKEN"

# Get me
curl -s http://localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# List tags
curl -s "http://localhost:8080/api/interest-tags?q=spring" | python3 -m json.tool

# Set interests
curl -s -X PUT http://localhost:8080/api/users/me/interests \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tagSlugs":["spring_boot","react","mysql"]}' | python3 -m json.tool

# Get interests
curl -s http://localhost:8080/api/users/me/interests -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# Stop app: kill %1
```

Expected: every endpoint responds with the expected payload.

- [ ] **Step 3: Final commit (if needed)**

If smoke test surfaced anything, fix it and commit.

```bash
git log --oneline
# Should show ~20 commits, one per task.
```

Plan 1 complete. Move to **Plan 2: Ingestion**.
