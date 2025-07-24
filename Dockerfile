# Build stage
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn clean package -DskipTests

# Run stage
FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Create directories
RUN mkdir -p /data
RUN mkdir -p /app/frontend/web

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=dev
ENV FRONTEND_PATH=/app/frontend/web

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"] 