# ============================================================
# Build stage
# ============================================================
FROM gradle:8.10-jdk21 AS builder

WORKDIR /workspace

# Copy Gradle configuration first for better Docker layer caching
COPY settings.gradle build.gradle ./

# Copy all modules
COPY common ./common
COPY gateway ./gateway
COPY document-service ./document-service
COPY indexer-service ./indexer-service
COPY search-service ./search-service

# Module is passed from docker-compose.yml
ARG MODULE

# Build only the requested Spring Boot application
RUN gradle :${MODULE}:bootJar --no-daemon


# ============================================================
# Runtime stage
# ============================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

ARG MODULE

# Copy the generated Spring Boot jar
COPY --from=builder /workspace/${MODULE}/build/libs/*.jar /app/app.jar

# Spring Boot ports
EXPOSE 8080
EXPOSE 8081
EXPOSE 8082
EXPOSE 8083

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
