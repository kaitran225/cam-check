# Build stage - optimized for smaller build
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer)
RUN mvn dependency:go-offline

# Copy source and build
COPY backend backend
RUN mvn clean package -DskipTests

# Run stage - optimized for smaller runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create a non-root user to run the application
RUN addgroup -S camcheck && adduser -S camcheck -G camcheck
USER camcheck

# Copy the jar file
COPY --from=build /app/target/*.jar app.jar

# Create directories with correct permissions
USER root
RUN mkdir -p /data && chown -R camcheck:camcheck /data
RUN mkdir -p /app/frontend/web && chown -R camcheck:camcheck /app/frontend/web
USER camcheck

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=prod
ENV FRONTEND_PATH=/app/frontend/web
ENV LOW_RESOURCE_MODE=true

# Memory settings for ultra-low memory usage (30MB target)
ENV JAVA_OPTS="-Xmx40m -Xms20m -XX:MaxMetaspaceSize=64m -XX:CompressedClassSpaceSize=16m -XX:ReservedCodeCacheSize=16m -XX:+UseSerialGC -XX:+UseStringDeduplication -XX:+DisableExplicitGC -XX:SoftRefLRUPolicyMSPerMB=0 -XX:MaxDirectMemorySize=5M -Djava.awt.headless=true -Djava.security.egd=file:/dev/./urandom"

# VM capacity measurement settings
ENV vm_capacity_measure=true
ENV vm_capacity_stress_test=false

# Dynamic memory optimization settings
ENV dynamic_memory_enabled=true
ENV dynamic_memory_target_percent=75
ENV dynamic_memory_check_interval=5000

# Memory optimization settings
ENV memory_target_mb=30
ENV memory_max_mb=40
ENV memory_aggressive_gc=true
ENV memory_check_interval_ms=5000

# Memory-efficient frame processor settings
ENV frame_processor_cache_size=5
ENV frame_processor_cache_expire_ms=5000
ENV frame_processor_max_concurrent=2

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD wget -q --spider http://localhost:${SERVER_PORT:-10000}/health || exit 1

# Expose port (Render free tier uses port 10000)
EXPOSE 10000

# Run with optimized settings
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"] 