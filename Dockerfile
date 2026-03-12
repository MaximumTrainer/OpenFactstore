# Stage 1: Build the Vue 3 frontend
FROM node:20-alpine AS frontend-builder
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build the Spring Boot backend (with frontend static files embedded)
FROM eclipse-temurin:21-jdk-alpine AS backend-builder
WORKDIR /app
COPY backend/gradle ./gradle
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
RUN chmod +x ./gradlew
# Pre-fetch dependencies for better layer caching
RUN ./gradlew dependencies --no-daemon -q
COPY backend/src ./src
# Embed the compiled frontend as Spring Boot static resources
COPY --from=frontend-builder /app/dist ./src/main/resources/static
RUN ./gradlew bootJar --no-daemon -q && \
    JAR=$(find build/libs -maxdepth 1 -name "*.jar" ! -name "*-plain.jar" | head -1) && \
    test -n "$JAR" && cp "$JAR" /app/factstore.jar

# Stage 3: Minimal JRE runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend-builder /app/factstore.jar factstore.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "factstore.jar"]
