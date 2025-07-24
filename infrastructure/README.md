# CamCheck

CamCheck is a real-time camera monitoring and streaming application with mobile support.

## Project Structure

```
camcheck/
├── backend/            # Spring Boot backend services
│   ├── api/           # REST API endpoints
│   ├── core/          # Core functionality
│   ├── media/         # Media processing
│   └── security/      # Authentication & authorization
├── frontend/          # Frontend applications
│   └── mobile/        # Flutter mobile app
├── docs/              # Documentation
│   ├── api/           # API documentation
│   ├── architecture/  # Architecture docs
│   └── development/   # Development guides
├── infrastructure/    # Deployment configurations
│   ├── docker/        # Docker configs
│   └── kubernetes/    # K8s manifests
├── config/            # Configuration files
│   ├── dev/          # Development configs
│   └── prod/         # Production configs
└── tools/            # Development tools and scripts
```

## Features

- Real-time camera streaming
- WebRTC peer-to-peer communication
- Mobile application support
- User authentication and authorization
- Media processing and optimization
- Recording and snapshot functionality
- Motion detection
- Push notifications

## Technology Stack

### Backend
- Java 17
- Spring Boot 3.2
- Spring Security
- Spring WebSocket
- H2 Database
- JWT Authentication
- WebRTC

### Frontend
- Flutter 3.x
- Dart 3.x
- Provider
- WebRTC
- WebSocket

### Infrastructure
- Docker
- Kubernetes
- Nginx
- Redis
- Prometheus
- Grafana

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/camcheck.git
   cd camcheck
   ```

2. Set up the backend:
   ```bash
   cd backend
   mvn clean install
   ```

3. Set up the frontend:
   ```bash
   cd frontend/mobile
   flutter pub get
   ```

4. Run the application:
   ```bash
   # Terminal 1 - Backend
   cd backend/api
   mvn spring-boot:run

   # Terminal 2 - Frontend
   cd frontend/mobile
   flutter run
   ```

## Development

See the following guides for detailed development instructions:

- [Backend Development](backend/README.md)
- [Frontend Development](frontend/README.md)
- [Infrastructure Setup](infrastructure/README.md)

## Documentation

- [API Reference](docs/api/README.md)
- [Architecture Guide](docs/architecture/README.md)
- [Development Guide](docs/development/README.md)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Write/update tests
5. Submit a pull request

## Testing

### Backend Tests
```bash
cd backend
mvn test
```

### Frontend Tests
```bash
cd frontend/mobile
flutter test
```

## Deployment

See [Infrastructure Documentation](infrastructure/README.md) for detailed deployment instructions.

Quick start with Docker:
```bash
docker-compose up -d
```

## Configuration

- Development configuration: `config/dev/`
- Production configuration: `config/prod/`
- Environment variables: See `.env.example`

## Security

- JWT authentication
- Role-based access control
- Secure WebRTC configuration
- TLS encryption
- Input validation
- Error handling

## Performance

- WebRTC optimization
- Media compression
- Caching strategies
- Load balancing
- Resource optimization

## Monitoring

- Prometheus metrics
- Grafana dashboards
- Application logs
- Performance monitoring
- Error tracking

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support, please:

1. Check the [documentation](docs/)
2. Search existing issues
3. Create a new issue if needed

## Acknowledgments

- List any third-party libraries or tools used
- Credit contributors and maintainers
- Reference any inspiration or related projects