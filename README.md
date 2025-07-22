# CamCheck

A lightweight personal security camera system with chat functionality.

## Features

- Camera streaming and monitoring
- User authentication and role-based access control
- Chat functionality between users
- Superuser mode for monitoring all active cameras
- Dark/light theme toggle
- Responsive design for mobile devices

## Profiles

The application supports multiple profiles for different environments:

### Default Profile

The default profile is used when no specific profile is activated. It uses hardcoded defaults for local development.

### Development Profile

The development profile is optimized for development with additional debugging features:

- Enhanced logging
- H2 console enabled
- SQL query logging
- Default credentials

To run with the development profile:

```bash
java -jar cam-check.jar --spring.profiles.active=dev
```

Or with Maven:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Production Profile

The production profile is optimized for deployment and requires all configuration to be provided through environment variables:

- Minimal logging
- H2 console disabled
- Swagger UI disabled
- All configuration through environment variables (no defaults)

To run with the production profile:

```bash
java -jar cam-check.jar --spring.profiles.active=prod
```

## Environment Variables for Production

When running in production mode, you must provide all configuration through environment variables:

### Required Environment Variables

```bash
# Server Configuration
export SERVER_PORT=8080
export SERVER_ADDRESS=0.0.0.0

# Database Configuration
export DB_URL=jdbc:h2:file:./data/camcheck-prod;DB_CLOSE_ON_EXIT=FALSE
export DB_USERNAME=sa
export DB_PASSWORD=your_secure_password
export DB_DRIVER_CLASS=org.h2.Driver
export JPA_DDL_AUTO=update
export JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect

# Multipart Configuration
export MAX_FILE_SIZE=10MB
export MAX_REQUEST_SIZE=10MB

# User Credentials
export USER_USERNAME=user
export USER_PASSWORD=your_secure_password
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=your_secure_admin_password
export LEGACY_USERNAME=admin
export LEGACY_PASSWORD=your_secure_legacy_password
export SUPERUSER_USERNAME=superuser
export SUPERUSER_PASSWORD=your_secure_superuser_password

# Security Configuration
export TRUSTED_IPS=127.0.0.1,::1

# Camera Settings
export CAMERA_TYPE=webcam
export CAMERA_FRAME_RATE=15
export CAMERA_WIDTH=640
export CAMERA_HEIGHT=480
export CAMERA_DEVICE_INDEX=0
export CAMERA_FORCE_FALLBACK=false
export CAMERA_IP_URL=

# Motion Detection Settings
export MOTION_DETECTION_ENABLED=false
export MOTION_DETECTION_SENSITIVITY=20
export MOTION_DETECTION_COOLDOWN=10000

# Storage Settings
export STORAGE_RECORD_ON_MOTION=false
export STORAGE_RECORDING_LENGTH=10
export STORAGE_MAX_SIZE_MB=1000
export STORAGE_DELETE_OLDEST=true
export STORAGE_RECORDINGS_PATH=recordings

# Logging Configuration
export LOG_LEVEL_ROOT=WARN
export LOG_LEVEL_CAMCHECK=INFO
export LOG_LEVEL_SPRING_WEB=WARN
export LOG_LEVEL_SPRING_SECURITY=WARN
export LOG_LEVEL_SPRING_MESSAGING=WARN
export LOG_LEVEL_SPRING_WEBSOCKET=WARN
export LOG_LEVEL_UNDERTOW=WARN
```

## Building

Build the application with Maven:

```bash
mvn clean package
```

This will create a JAR file in the `target` directory.

## Running

Run the application with:

```bash
java -jar target/cam-check-0.0.1-SNAPSHOT.jar
```

## Default Users

When using the default or development profile:

- Regular User: username: `user`, password: `password`
- Admin: username: `admin`, password: `admin`
- Superuser: username: `superuser`, password: `changeme`

## API Documentation

When running in default or development mode, API documentation is available at:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api-docs