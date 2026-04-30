# Dev Radar — Project Notes for Claude

## Stack
- **Backend:** Java 21 + Spring Boot 3.5+, MySQL 8, Liquibase, JJWT, MapStruct, JUnit 5 + Testcontainers
- **Frontend:** React + Redux Toolkit + MUI, served by Spring Boot (single deployable)
- **AI:** Gemini 2.5 Flash via Google AI SDK (agentic radar generation with tool use)
- **Layout:** `backend/` and `frontend/` under repo root. Single Dockerfile at root.

## Commands
- Build: `cd backend && mvn -DskipTests compile`
- Test: `cd backend && mvn test`
- Verify (all): `cd backend && mvn verify`
- Run: `cd backend && mvn spring-boot:run`
- Local MySQL: `cd backend && docker compose up -d` (override host port via `DB_HOST_PORT` env var, default 3306)
- Deploy to Cloud Run: `./deploy.sh`

## Branches
- `main` — stable base
- `demo` — deployed to Cloud Run (this is the primary working branch)

## Profiles
- base (`application.yml`) — local development (localhost MySQL, localhost Redis)
- `demo` — Cloud Run deployment (Cloud SQL socket factory, secrets from env vars)
- `prod` — Cloud Run deployment (identical to demo currently)

## Cloud Run Deployment
- **Service:** `https://devradar-414645578716.us-central1.run.app` (single fullstack service)
- **Database:** `devradar` on Cloud SQL instance `devradar-mysql` (us-central1, MySQL 8.0)
- **DB User:** `devradar_user`
- **Service Account:** `agent-482@project-323da946-d916-4f40-98e.iam.gserviceaccount.com`
- **Registry:** `us-central1-docker.pkg.dev/project-323da946-d916-4f40-98e/app-registry/`
- **Secrets:** `dr-jwt-secret`, `dr-db-password`, `dr-google-ai-api-key`, `dr-github-oauth-client-id`, `dr-github-oauth-client-secret`, `dr-github-token-enc-key`, `dr-trigger-secret`, `ar-smtp-password`

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
- GitHub OAuth integration for connecting developer accounts
- Agentic AI radar generation: orchestrator model with tool-calling (GitHub API, scoring)
- Rate-limited radar generation (10/hour per user)

## Subagent guidance
- When dispatching subagents that commit, **explicitly instruct them not to add `Co-Authored-By: Claude` trailers**.
- Subagents follow TDD on logic-bearing tasks (services with branching logic, validators, parsers).

## Skill routing

When the user's request matches an available skill, invoke it via the Skill tool. When in doubt, invoke the skill.

Key routing rules:
- Product ideas/brainstorming → invoke /office-hours
- Strategy/scope → invoke /plan-ceo-review
- Architecture → invoke /plan-eng-review
- Design system/plan review → invoke /design-consultation or /plan-design-review
- Full review pipeline → invoke /autoplan
- Bugs/errors → invoke /investigate
- QA/testing site behavior → invoke /qa or /qa-only
- Code review/diff check → invoke /review
- Visual polish → invoke /design-review
- Ship/deploy/PR → invoke /ship or /land-and-deploy
- Save progress → invoke /context-save
- Resume context → invoke /context-restore
