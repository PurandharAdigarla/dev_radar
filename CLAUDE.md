# Dev Radar — Project Notes for Claude

## Stack
- **Backend:** Java 21 + Spring Boot 3.5+, MySQL 8, Redis (later), Liquibase, JJWT, MapStruct, JUnit 5 + Testcontainers
- **Frontend (later):** React + Redux Toolkit + MUI
- **Layout:** `backend/` and `frontend/` (later) under repo root. Git repo is at the root.

## Commands (backend)
- Build: `cd backend && mvn -DskipTests compile`
- Test: `cd backend && mvn test`
- Verify (all): `cd backend && mvn verify`
- Run: `cd backend && mvn spring-boot:run`
- Local MySQL: `cd backend && docker compose up -d` (override host port via `DB_HOST_PORT` env var, default 3306)

## Commit conventions
- Format: `type(scope): subject` (e.g., `feat(domain): add User entity`, `fix(backend): ...`)
- **Never add a `Co-Authored-By: Claude` trailer.** Commits are attributed solely to the user.
- Conventional types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `ci`.

## Plan / spec docs
- Spec: `docs/superpowers/specs/2026-04-19-dev-radar-mvp-design.md`
- Plan 1: `docs/superpowers/plans/2026-04-19-plan1-foundation.md`
- Subsequent plans: `docs/superpowers/plans/YYYY-MM-DD-planN-<name>.md`

## Architecture
- Modular monolith, clean architecture: `domain/` → `repository/` → `service/` → `service/application/` → `web/rest/`
- Stateless JWT auth (HS256), refresh tokens stored hashed
- Liquibase owns the schema; JPA `ddl-auto: validate`

## Subagent guidance
- When dispatching subagents that commit, **explicitly instruct them not to add `Co-Authored-By: Claude` trailers**.
- Subagents follow TDD on logic-bearing tasks (services with branching logic, validators, parsers).
