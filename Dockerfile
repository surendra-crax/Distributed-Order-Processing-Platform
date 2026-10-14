# ==========================================
# Multi-stage Dockerfile for Order Platform
# ==========================================

# 1. Build Stage
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy dependency definition and download offline dependencies (improves caching)
COPY pom.xml checkstyle.xml ./
RUN mvn dependency:go-offline -B

# Copy source code and build runnable artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# 2. Runtime Stage
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Add unprivileged system user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy compiled jar from builder stage
COPY --from=builder /build/target/distributed-order-processing-platform-*.jar /app/app.jar

# Set ownership
RUN chown -R appuser:appgroup /app
USER appuser:appgroup

# Environment variables
ENV SERVER_PORT=8080
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=30s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
