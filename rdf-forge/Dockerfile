FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Create a non-root user for the build stage
RUN addgroup -g 1000 -S rdfforge && \
    adduser -u 1000 -S rdfforge -G rdfforge

# 1. Copy POMs only first (to cache dependencies)
COPY pom.xml .
COPY rdf-forge-common/pom.xml rdf-forge-common/
COPY rdf-forge-engine/pom.xml rdf-forge-engine/
COPY rdf-forge-pipeline-service/pom.xml rdf-forge-pipeline-service/
COPY rdf-forge-shacl-service/pom.xml rdf-forge-shacl-service/
COPY rdf-forge-job-service/pom.xml rdf-forge-job-service/
COPY rdf-forge-data-service/pom.xml rdf-forge-data-service/
COPY rdf-forge-dimension-service/pom.xml rdf-forge-dimension-service/
COPY rdf-forge-triplestore-service/pom.xml rdf-forge-triplestore-service/
COPY rdf-forge-gateway/pom.xml rdf-forge-gateway/
COPY rdf-forge-auth-service/pom.xml rdf-forge-auth-service/
COPY rdf-forge-cli/pom.xml rdf-forge-cli/

# 2. Download dependencies (this layer will be cached unless POMs change)
RUN mvn dependency:go-offline -B

# 3. Copy Source Code
COPY rdf-forge-common/src rdf-forge-common/src
COPY rdf-forge-engine/src rdf-forge-engine/src
COPY rdf-forge-pipeline-service/src rdf-forge-pipeline-service/src
COPY rdf-forge-shacl-service/src rdf-forge-shacl-service/src
COPY rdf-forge-job-service/src rdf-forge-job-service/src
COPY rdf-forge-data-service/src rdf-forge-data-service/src
COPY rdf-forge-dimension-service/src rdf-forge-dimension-service/src
COPY rdf-forge-triplestore-service/src rdf-forge-triplestore-service/src
COPY rdf-forge-gateway/src rdf-forge-gateway/src
COPY rdf-forge-auth-service/src rdf-forge-auth-service/src
COPY rdf-forge-cli/src rdf-forge-cli/src

# 4. Build all modules
RUN mvn clean package -Dmaven.test.skip

# 5. Final Runtime Image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user (UID 1000) for security
RUN addgroup -g 1000 -S rdfforge && \
    adduser -u 1000 -S rdfforge -G rdfforge

# Create necessary directories with proper permissions
RUN mkdir -p /app/logs /app/tmp /app/data && \
    chown -R rdfforge:rdfforge /app

ARG SERVICE_NAME
COPY --from=builder --chown=rdfforge:rdfforge /app/${SERVICE_NAME}/target/*.jar app.jar

# Switch to non-root user
USER 1000

# JVM optimized settings for production:
# - Use G1 Garbage Collector for better performance with large heaps
# - Set initial and maximum heap size
# - Enable container-aware memory settings
# - Optimize GC for low latency
ENV JAVA_OPTS="-XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+AlwaysPreTouch \
    -XX:+DisableExplicitGC \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true \
    -Dfile.encoding=UTF-8"

# Service port - override per service (gateway=8000, pipeline=8001, etc.)
ENV SERVER_PORT=8080

# Using exec form for ENTRYPOINT with JVM options
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

# Health check using the configurable server port
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${SERVER_PORT}/actuator/health || exit 1
