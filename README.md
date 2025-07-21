# CamCheck - Lightweight Personal Security Camera System

A lightweight, secure personal camera streaming solution built with Spring Boot that allows you to monitor your space remotely with minimal resource usage.

## Features

- **Lightweight**: Designed to run continuously on low-power devices with Undertow web server
- **Secure Access**: Spring Security authentication with role-based access control
- **Client-Side Camera**: Uses browser's camera API for efficient client-side streaming
- **Session Management**: One-to-one camera sessions between admin and users
- **Remote Viewing**: Stream camera footage to the web for viewing anywhere
- **Recording**: Save snapshots from client cameras
- **Self-Hosted**: Deploy directly to your own domain without third-party services
- **Easy Setup**: Simple configuration with environment variables for quick deployment
- **REST API**: Comprehensive API with Swagger documentation for integration

## Tech Stack

- **Backend**: Spring Boot with Undertow (high-performance web server)
- **Streaming**: WebSocket (SockJS/STOMP) for efficient video streaming
- **Authentication**: Spring Security with role-based access control
- **Storage**: Local file system for recordings
- **Frontend**: Thymeleaf with HTML/CSS/JS interface
- **Client Camera**: WebRTC/MediaDevices API for browser camera access
- **Configuration**: Environment variables (.env file) for flexible deployment
- **API Documentation**: Swagger/OpenAPI 3.0

## Requirements

- Java 17+
- Modern web browser with camera access
- Domain name (optional, for remote access)
- Server or Raspberry Pi to host the application

## Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/cam-check.git
cd cam-check

# Configure environment variables
cp .env.example .env
# Edit .env with your preferred settings

# Build the project
./mvnw clean package

# Run the application
java -jar target/cam-check-0.0.1-SNAPSHOT.jar
```

## Usage

```bash
# Start the server
java -jar cam-check.jar

# Access your camera dashboard
# Visit http://localhost:80 in your browser (or your configured port)

# Login credentials
# Admin: KaiTran / @KaiTran225
# User: LuxuBoo / @Anh2411
# Legacy: admin / 123

# Access API documentation
# Visit http://localhost:80/swagger-ui.html
```

## Configuration Options

The application is configured using environment variables in a `.env` file:

### Server Configuration
- `SERVER_PORT`: Server port (default: 80)
- `SERVER_ADDRESS`: Server address (default: 0.0.0.0)

### Undertow Configuration
- `UNDERTOW_WORKER_THREADS`: Worker thread count (default: 16)
- `UNDERTOW_IO_THREADS`: I/O thread count (default: 8)
- `UNDERTOW_BUFFER_SIZE`: Buffer size in bytes (default: 1024)
- `UNDERTOW_DIRECT_BUFFERS`: Use direct buffers for better performance (default: true)

### Security Configuration
- `SECURITY_USERNAME_LUXUBOO`: Regular user username (default: LuxuBoo)
- `SECURITY_PASSWORD_LUXUBOO`: Regular user password (default: @Anh2411)
- `SECURITY_USERNAME_KAITRAN`: Admin username (default: KaiTran)
- `SECURITY_PASSWORD_KAITRAN`: Admin password (default: @KaiTran225)
- `LEGACY_USERNAME`: Legacy admin username (default: admin)
- `LEGACY_PASSWORD`: Legacy admin password (default: 123)
- `SECURITY_TRUSTED_IPS`: Comma-separated list of trusted IP addresses/ranges

### Camera and Storage Configuration
- `CAMERA_TYPE`: Camera type (webcam or ip)
- `STORAGE_RECORDINGS_PATH`: Path for storing recordings (default: ./recordings)
- `STORAGE_MAX_SIZE_MB`: Maximum storage size in MB (default: 5000)

## User Roles and Functionality

### Admin Role (KaiTran)
- Create camera sessions
- Generate and share session codes
- View both local and remote cameras
- Take snapshots

### User Role (LuxuBoo)
- Join camera sessions using codes
- View both local and remote cameras
- Take snapshots

## Session Management

1. **Admin Creates Session**:
   - Admin logs in and clicks "Create Session"
   - A unique 6-digit session code is generated
   - Admin shares this code with the user

2. **User Joins Session**:
   - User logs in and enters the session code
   - User clicks "Join Session"
   - Both admin and user can now see each other's camera feeds

## Deployment Options

### Deploy to Render.com

This project includes configuration files for easy deployment to Render.com:

1. **Prerequisites**
   - A Render.com account
   - Git repository with your CamCheck code

2. **Deployment Steps**
   - Fork or clone this repository
   - Connect your Git repository to Render.com
   - Select "Blueprint" as the deployment type
   - Render will automatically detect the `render.yaml` file
   - Configure the required environment variables when prompted
   - Click "Create Blueprint"

3. **Environment Variables**
   - During deployment, you'll be prompted to set all variables from `.env.example`

### Self-Deployment

#### Prerequisites
- Your domain name (already purchased)
- A server with public IP address
- JDK 17+ installed on the server
- SSL certificate (Let's Encrypt recommended)

#### Steps for Self-Deployment

1. **Set up your server**
   - Install Java: `sudo apt install openjdk-17-jdk`
   - Configure firewall to allow ports 80 and 443

2. **Configure DNS**
   - Point your domain to your server's IP address
   - Set up A records in your domain registrar's DNS settings

3. **Install SSL Certificate**
   - Install Certbot: `sudo apt install certbot`
   - Obtain certificate: `sudo certbot certonly --standalone -d yourdomain.com`

4. **Deploy the application**
   - Upload the JAR file and `.env` file to your server
   - Configure as a system service for auto-restart
   - Set up Nginx as a reverse proxy (configuration provided in `deploy/nginx.conf`)

5. **Start the application**
   - `sudo systemctl start camcheck`
   - Access at https://yourdomain.com

## Security Considerations

- Change default credentials immediately
- Use HTTPS (SSL/TLS) for all connections
- Keep the system updated regularly
- Consider IP whitelisting for additional security
- Store sensitive information in `.env` file (not in git repository)

## Project Structure

```
cam-check/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── camcheck/
│   │   │           ├── controller/
│   │   │           │   ├── ApiController.java
│   │   │           │   ├── CameraController.java
│   │   │           │   ├── ClientCameraController.java
│   │   │           │   └── SessionController.java
│   │   │           ├── service/
│   │   │           │   ├── CameraService.java
│   │   │           │   ├── MotionDetectionService.java
│   │   │           │   └── RecordingService.java
│   │   │           ├── model/
│   │   │           │   ├── ApiResponse.java
│   │   │           │   └── CameraStatus.java
│   │   │           └── config/
│   │   │               ├── SecurityConfig.java
│   │   │               ├── WebSocketConfig.java
│   │   │               ├── UndertowConfig.java
│   │   │               └── EnvConfig.java
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── brutalist.css
│   │       │   └── api-guide.html
│   │       └── templates/
│   │           ├── index.html
│   │           ├── login.html
│   │           ├── client-camera.html
│   │           └── receiver.html
├── .env
├── .env.example
├── deploy/
│   ├── nginx.conf
│   └── camcheck.service
└── pom.xml
```

## License

MIT

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.