# Multi-stage Dockerfile for Java Spring Boot Backend
# Stage 1: Build stage with Gradle
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Install dependencies needed for native libraries (if any)
RUN apk add --no-cache bash

# Copy Gradle wrapper and build files first (for layer caching)
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (this layer will be cached unless dependencies change)
# Using --refresh-dependencies to ensure consistent builds
RUN ./gradlew dependencies --no-daemon --refresh-dependencies

# Copy source code (only main, not test)
COPY src/main/ src/main/

# Build the application (skip tests for faster builds)
RUN ./gradlew bootJar --no-daemon -x test

# Verify JAR was created
RUN ls -lh /app/build/libs/*.jar

# Stage 2: Runtime stage with JRE
FROM eclipse-temurin:17-jre-alpine

# Install wget for health checks and bash for better debugging
RUN apk add --no-cache wget bash

# Create non-root user for security
RUN addgroup -S appuser && adduser -S appuser -G appuser

WORKDIR /app

# Copy JAR from build stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Verify JAR was copied
RUN ls -lh app.jar

# Create directories for data volumes
RUN mkdir -p /app/data/uploads /app/data/indexes && \
    chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check using actuator endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM optimization for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.jmx.enabled=false"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
