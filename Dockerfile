# Single-service build: the frontend's static output gets baked directly
# into the backend's jar (src/main/resources/static), and Spring Boot
# serves both the API and the SPA from one process on one port. This is
# the image to deploy as ONE Render Web Service instead of running
# frontend/Dockerfile and backend/Dockerfile as two separate services.
#
# Local development is unaffected - docker-compose.yml still uses the two
# original Dockerfiles (frontend/Dockerfile, backend/Dockerfile) for the
# live-reloading dev experience. This file is specifically for production
# deployment as a single combined service.

FROM node:20-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/. .
# Same-origin deployment: the frontend's API client already defaults to a
# relative "/api/v1" base path (see frontend/src/services/api.ts), so no
# VITE_BACKEND_URL is needed here - requests naturally hit this same
# service instead of a separate frontend origin.
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
# Copy the frontend's built assets into Spring Boot's default static
# resource location before packaging, so they end up bundled inside the
# fat jar and are served automatically at "/".
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
