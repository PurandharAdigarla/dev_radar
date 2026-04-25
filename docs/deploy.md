# Dev Radar — Deployment Runbook

## Architecture

Single Cloud Run service serving both the React frontend (static files) and Spring Boot backend from one container. MySQL via Cloud SQL, Redis via Memorystore.

## Prerequisites

1. GCP project with billing enabled
2. APIs enabled: Cloud Run, Cloud Build, Cloud SQL Admin, Secret Manager, Artifact Registry
3. GitHub repo connected to Cloud Build (or use GitHub Actions)

## One-time GCP setup

### 1. Artifact Registry

```bash
gcloud artifacts repositories create devradar \
  --repository-format=docker \
  --location=us-central1
```

### 2. Cloud SQL (MySQL 8.0)

```bash
gcloud sql instances create devradar-mysql \
  --database-version=MYSQL_8_0 \
  --tier=db-f1-micro \
  --region=us-central1 \
  --storage-size=10GB

gcloud sql databases create devradar --instance=devradar-mysql

gcloud sql users create devradar \
  --instance=devradar-mysql \
  --password=<GENERATE_STRONG_PASSWORD>
```

### 3. Memorystore (Redis)

```bash
gcloud redis instances create devradar-redis \
  --size=1 \
  --region=us-central1 \
  --tier=basic \
  --redis-version=redis_7_0
```

Note the `host` IP from: `gcloud redis instances describe devradar-redis --region=us-central1`

### 4. Secret Manager

Create each secret:

```bash
for secret in jwt-secret google-ai-api-key groq-api-key \
  github-oauth-client-id github-oauth-client-secret \
  github-token-encryption-key; do
  echo -n "<VALUE>" | gcloud secrets create $secret --data-file=-
done
```

### 5. Service account permissions

The Cloud Run service account needs:
- `roles/cloudsql.client`
- `roles/secretmanager.secretAccessor`
- `roles/redis.editor`

## Environment variables

Set these on the Cloud Run service:

| Variable | Source | Example |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Hardcoded | `prod` |
| `DB_NAME` | Cloud SQL | `devradar` |
| `DB_USER` | Cloud SQL | `devradar` |
| `DB_PASSWORD` | Secret Manager | - |
| `CLOUD_SQL_INSTANCE` | Cloud SQL | `project:us-central1:devradar-mysql` |
| `REDIS_HOST` | Memorystore | `10.x.x.x` |
| `REDIS_PORT` | Memorystore | `6379` |
| `JWT_SECRET` | Secret Manager | min 256-bit |
| `GOOGLE_AI_API_KEY` | Secret Manager | - |
| `GROQ_API_KEY` | Secret Manager | - |
| `FRONTEND_BASE_URL` | Your domain | `https://devradar.example.com` |
| `GITHUB_OAUTH_CLIENT_ID` | Secret Manager | - |
| `GITHUB_OAUTH_CLIENT_SECRET` | Secret Manager | - |
| `GITHUB_OAUTH_REDIRECT_URI` | Your domain | `https://devradar.example.com/api/auth/github/callback` |
| `GITHUB_TOKEN_ENCRYPTION_KEY` | Secret Manager | base64 AES key |
| `SCHEDULING_ENABLED` | Config | `false` (use Cloud Scheduler triggers instead) |
| `TRIGGER_SECRET` | Secret Manager | for ingestion HTTP triggers |

## Deploy manually

```bash
# Build and deploy from repo root
gcloud builds submit --config=cloudbuild.yaml \
  --substitutions=SHORT_SHA=$(git rev-parse --short HEAD)
```

Or directly:

```bash
gcloud run deploy devradar \
  --source . \
  --region us-central1 \
  --set-env-vars SPRING_PROFILES_ACTIVE=prod,REDIS_HOST=10.x.x.x,FRONTEND_BASE_URL=https://devradar.example.com,GITHUB_OAUTH_REDIRECT_URI=https://devradar.example.com/api/auth/github/callback \
  --set-secrets JWT_SECRET=jwt-secret:latest,GOOGLE_AI_API_KEY=google-ai-api-key:latest \
  --add-cloudsql-instances project:us-central1:devradar-mysql
```

## Deploy via CI/CD

Push to `main` triggers `.github/workflows/deploy.yml` which submits to Cloud Build.

Required GitHub secrets:
- `WIF_PROVIDER` — Workload Identity Federation provider
- `WIF_SERVICE_ACCOUNT` — GCP service account email

## Ingestion jobs

With `SCHEDULING_ENABLED=false`, use Cloud Scheduler to hit the trigger endpoints:

```bash
# Example: HN ingestion every 6 hours
gcloud scheduler jobs create http devradar-ingest-hn \
  --schedule="0 */6 * * *" \
  --uri="https://devradar-xxxxx.run.app/api/internal/trigger/hn" \
  --http-method=POST \
  --headers="X-Trigger-Secret=<TRIGGER_SECRET>" \
  --time-zone="UTC"
```

Available trigger endpoints:
- `/api/internal/trigger/hn`
- `/api/internal/trigger/gh-trending`
- `/api/internal/trigger/gh-releases`
- `/api/internal/trigger/gh-stars`
- `/api/internal/trigger/articles`
- `/api/internal/trigger/security`
- `/api/internal/trigger/dep-releases`
- `/api/internal/trigger/dep-scan`

## Health check

```bash
curl https://devradar-xxxxx.run.app/actuator/health
```
