# Dev Radar — Backend

Foundation layer for the Dev Radar MVP (sub-project 1, plan 1).

## Stack
Java 21, Spring Boot 3.5+, MySQL 8, Liquibase, Spring Security (JWT), MapStruct, JUnit 5 + Testcontainers.

## Local development

```bash
# Start MySQL (override host port via DB_HOST_PORT env var if 3306 is taken)
docker compose up -d
# example with custom port: DB_HOST_PORT=3307 docker compose up -d

# Run app
mvn spring-boot:run

# App runs at http://localhost:8080
```

## Tests

```bash
mvn test
```

Integration tests use Testcontainers MySQL — Docker required.

On macOS with Docker Desktop, if Testcontainers cannot find your Docker socket, create `~/.testcontainers.properties` with:

```
docker.host=unix:///Users/<you>/.docker/run/docker.sock
```

## What this plan ships

- Email + password registration
- Login (returns JWT access token + opaque refresh token)
- Refresh token rotation (each refresh issues a new pair, revokes the old)
- Logout (revokes refresh)
- `GET /api/users/me`, `PATCH /api/users/me`
- Interest tags catalog: `GET /api/interest-tags?q=&category=`
- Set/get user interests: `GET/PUT /api/users/me/interests`
- Audit log of auth events (USER_REGISTERED, USER_LOGIN, USER_LOGOUT)
- GlobalExceptionHandler returning structured `ErrorResponse`

## What this plan does NOT ship

- Anything AI-related (deferred to Plan 3)
- Ingestion, sources, source items (Plan 2)
- GitHub OAuth + Auto-PR (Plan 4)
- MCP server (Plan 5)
- Eval harness (Plan 6)
- Observability dashboard (Plan 7)
- Frontend (Plan 8)
