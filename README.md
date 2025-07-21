# CamCheck - Lightweight Personal Security Camera System

A lightweight, secure personal camera streaming solution built with Spring Boot that allows you to monitor your space remotely with minimal resource usage.

## Features

- **Lightweight**: Designed to run continuously on low-power devices
- **Secure Access**: Spring Security authentication to protect your stream
- **Remote Viewing**: Stream camera footage to the web for viewing anywhere
- **Motion Detection**: Optional motion detection with notifications
- **Recording**: Save footage when motion is detected
- **Self-Hosted**: Deploy directly to your own domain without third-party services
- **Easy Setup**: Simple configuration for quick deployment
- **REST API**: Comprehensive API with Swagger documentation for integration

## Tech Stack

- **Backend**: Spring Boot
- **Streaming**: WebSocket for efficient video streaming
- **Authentication**: Spring Security
- **Storage**: Local file system for recordings
- **Frontend**: Thymeleaf with HTML/CSS/JS interface
- **API Documentation**: Swagger/OpenAPI 3.0

## Requirements

- Java 17+
- Webcam or IP camera
- Domain name (already purchased)
- Server or Raspberry Pi to host the application

## Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/cam-check.git
cd cam-check

# Build the project
./mvnw clean package

# Run the application
java -jar target/cam-check-0.0.1-SNAPSHOT.jar
```

## Usage

```bash
# Start the server
java -jar cam-check.jar

# Access your camera stream
# Visit https://your-domain.com in your browser

# Access API documentation
# Visit https://your-domain.com/swagger-ui.html
```

## Configuration Options

Edit `application.yml` to customize your setup:

- `server.port`: Server port (default: 8080)
- `camcheck.security.username` and `password`: Authentication credentials
- `camcheck.camera.motion-detection`: Enable/disable motion detection
- `camcheck.storage.record-on-motion`: Save video when motion is detected
- `camcheck.storage.max-size-mb`: Maximum disk space for recordings (in MB)

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
   - During deployment, you'll be prompted to set:
     - `CAMCHECK_SECURITY_USERNAME`: Admin username
     - `CAMCHECK_SECURITY_PASSWORD`: Admin password

4. **Important Notes**
   - The application is configured to use fallback mode on Render.com since webcam access is limited
   - To use with real cameras, consider setting up a self-hosted instance
   - The persistent disk is configured to store recordings

5. **Access Your Deployed Application**
   - Once deployed, your application will be available at `https://camcheck.onrender.com` (or your custom domain)
   - Access the API documentation at `https://camcheck.onrender.com/swagger-ui.html`

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
   - Upload the JAR file to your server
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

## Project Structure

```
cam-check/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── camcheck/
│   │   │           ├── controller/
│   │   │           ├── service/
│   │   │           ├── model/
│   │   │           └── config/
│   │   └── resources/
│   │       ├── static/
│   │       └── templates/
├── deploy/
│   ├── nginx.conf
│   └── camcheck.service
└── pom.xml
```

## Todo

- [ ] Set up Spring Boot project
- [ ] Implement camera streaming
- [ ] Add Spring Security authentication
- [ ] Create web interface
- [ ] Implement motion detection
- [ ] Add recording functionality
- [ ] Optimize for long-term running
- [ ] Create deployment scripts

## License

MIT

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.