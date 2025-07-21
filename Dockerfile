FROM maven:3.9-eclipse-temurin-17 AS build

# Set the working directory
WORKDIR /app

# Copy the POM file to download dependencies
COPY pom.xml .

# Download dependencies (this layer will be cached)
RUN mvn dependency:go-offline -B

# Copy the rest of the application
COPY src ./src

# Build the application
RUN mvn package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

# Create a non-root user to run the application
RUN groupadd -r camcheck && useradd -r -g camcheck camcheck

# Set the working directory
WORKDIR /app

# Create recordings directory and set permissions
RUN mkdir -p /app/recordings/snapshots && chown -R camcheck:camcheck /app

# Copy the built artifact from the build stage
COPY --from=build /app/target/*.jar app.jar

# Set permissions
RUN chown -R camcheck:camcheck /app

# Switch to non-root user
USER camcheck

# Expose the port the app runs on
EXPOSE 80

# Set environment variables that can be overridden
ENV SPRING_PROFILES_ACTIVE=production
ENV SERVER_PORT=80

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"] 