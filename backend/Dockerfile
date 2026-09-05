# =============================================================================
# Stage 1: Build with Maven
# =============================================================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first for layer caching of dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy source code and build executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# =============================================================================
# Stage 2: Runtime
# =============================================================================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="DocAssistant Team"
LABEL description="AI-Powered Document Assistant Backend"

WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Expose the application port
EXPOSE 8080

# JVM tuning for containerised environments
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
