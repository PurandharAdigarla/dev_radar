# ---- Stage 1: Build frontend ----
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- Stage 2: Build backend ----
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -B
COPY backend/src ./src
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static
RUN mvn package -DskipTests -B

# ---- Stage 3: Runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgrp && adduser -S appusr -G appgrp
COPY --from=backend-build /app/backend/target/devradar-backend-*.jar app.jar
RUN chown -R appusr:appgrp /app
USER appusr

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
