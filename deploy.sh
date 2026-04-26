#!/usr/bin/env bash
set -euo pipefail

# ── DevRadar — Cloud Run Deploy Script ──
# Usage:
#   ./deploy.sh              # build and deploy (single fullstack service)

PROJECT="project-323da946-d916-4f40-98e"
REGION="us-central1"
REGISTRY="us-central1-docker.pkg.dev/$PROJECT/app-registry"
SERVICE_ACCOUNT="agent-482@$PROJECT.iam.gserviceaccount.com"
SQL_CONN="$PROJECT:$REGION:devradar-mysql"

# Use git short SHA as image tag
TAG=$(git rev-parse --short HEAD 2>/dev/null || echo "latest")

IMAGE="$REGISTRY/devradar:$TAG"
SERVICE_URL="https://devradar-414645578716.$REGION.run.app"

echo "▸ Building DevRadar..."
docker build --platform linux/amd64 -t "$IMAGE" .

echo "▸ Pushing image..."
docker push "$IMAGE"

echo "▸ Deploying devradar to Cloud Run..."
gcloud run deploy devradar \
  --image="$IMAGE" \
  --region="$REGION" \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --memory=1Gi \
  --cpu=1 \
  --min-instances=0 \
  --max-instances=3 \
  --add-cloudsql-instances="$SQL_CONN" \
  --service-account="$SERVICE_ACCOUNT" \
  --set-env-vars="^|^SPRING_PROFILES_ACTIVE=demo|CLOUD_SQL_INSTANCE=$SQL_CONN|DB_NAME=devradar|DB_USER=devradar_user|SCHEDULING_ENABLED=false|FRONTEND_BASE_URL=$SERVICE_URL|GITHUB_OAUTH_REDIRECT_URI=https://devradar-755wjr4w2a-uc.a.run.app/api/auth/github/callback" \
  --set-secrets="JWT_SECRET=dr-jwt-secret:latest,GOOGLE_AI_API_KEY=dr-google-ai-api-key:latest,GITHUB_OAUTH_CLIENT_ID=dr-github-oauth-client-id:latest,GITHUB_OAUTH_CLIENT_SECRET=dr-github-oauth-client-secret:latest,GITHUB_TOKEN_ENCRYPTION_KEY=dr-github-token-enc-key:latest,MAIL_PASSWORD=ar-smtp-password:latest,TRIGGER_SECRET=dr-trigger-secret:latest,DB_PASSWORD=dr-db-password:latest"

echo ""
echo "✓ DevRadar deployed: $SERVICE_URL"
