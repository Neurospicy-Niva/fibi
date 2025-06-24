# 🚀 Niva Setup Guide

This guide will help you set up Niva for local development and testing.

## 📋 Prerequisites

### Required Software
- **Java 17+** (OpenJDK recommended)
- **Docker** and **Docker Compose**
- **Git**
- **Signal CLI** (for Signal integration)

### Optional Tools
- **IntelliJ IDEA** (recommended IDE)
- **MongoDB Compass** (for database inspection)
- **Postman** (for API testing)

## 🐳 Quick Start with Docker

The fastest way to get Niva running is using Docker Compose:

```bash
# Clone the repository
git clone https://github.com/Neurospicy-Niva/fibi.git
cd fibi

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f niva
```

This will start:
- Niva application (port 8080)
- MongoDB (port 27017)
- Ollama (port 11434)
- Signal CLI mock (for testing)

## 🛠 Development Setup

### 1. Clone and Build

```bash
git clone https://github.com/Neurospicy-Niva/fibi.git
cd fibi
./gradlew build
```

### 2. Start Dependencies

```bash
# Start MongoDB and Ollama
docker-compose up -d mongodb ollama

# Wait for services to be ready
docker-compose logs -f mongodb ollama
```

### 3. Configure Application

Create `src/main/resources/application-dev.yaml`:

```yaml
spring:
  profiles:
    active: dev
  data:
    mongodb:
      uri: mongodb://localhost:27017/niva-dev

ollama:
  base-url: http://localhost:11434

signal:
  cli:
    webhook-url: http://localhost:8080/signal/webhook
    base-url: http://localhost:8080/signal

logging:
  level:
    icu.neurospicy.fibi: DEBUG
```

### 4. Set Up Ollama Models

```bash
# Pull required models
docker exec niva-ollama ollama pull [MODEL_NAME]
docker exec niva-ollama ollama pull [MODEL_NAME]

# Verify models are available
docker exec niva-ollama ollama list
```

### 5. Run the Application

```bash
# Run with development profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Or run from your IDE with VM options:
# -Dspring.profiles.active=dev
```

## 📱 Signal Integration Setup

### For Development (Mock)
The Docker Compose setup includes a Signal CLI mock for testing:

```bash
# Signal mock is available at http://localhost:8081
# Send test messages via:
curl -X POST http://localhost:8081/v1/send \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Hello Niva!",
    "recipient": "+1234567890"
  }'
```

### For Production (Real Signal)

1. **Install Signal CLI**:
   ```bash
   # Download and setup Signal CLI
   wget https://github.com/AsamK/signal-cli/releases/download/v0.11.12/signal-cli-0.11.12.tar.gz
   tar xf signal-cli-0.11.12.tar.gz
   ```

2. **Register Phone Number**:
   ```bash
   ./signal-cli-0.11.12/bin/signal-cli -u +YOUR_PHONE_NUMBER register
   ./signal-cli-0.11.12/bin/signal-cli -u +YOUR_PHONE_NUMBER verify CODE
   ```

3. **Configure Webhook**:
   ```bash
   ./signal-cli-0.11.12/bin/signal-cli -u +YOUR_PHONE_NUMBER daemon \
     --http-port 8082 \
     --send-read-receipts
   ```

4. **Update Configuration**:
   ```yaml
   signal:
     cli:
       webhook-url: http://localhost:8080/signal/webhook
       base-url: http://localhost:8082
       phone-number: +YOUR_PHONE_NUMBER
   ```

## 🗄️ Database Setup

### MongoDB Configuration

The application uses MongoDB for data persistence. Default collections include:

- `friendships`: User registrations and preferences
- `conversations`: Message history and context
- `tasks`: User tasks and reminders
- `routines`: Daily routines and schedules
- `calendars`: Calendar configurations

### Initialize Sample Data

```bash
# Run database initialization script
./gradlew bootRun --args='--spring.profiles.active=dev,sample-data'
```

Or manually create a test user:

```javascript
// MongoDB shell commands
use niva-dev

db.friendships.insertOne({
  "_id": ObjectId(),
  "signalId": "+1234567890",
  "name": "Test User",
  "phoneNumber": "+1234567890",
  "acceptedAgreement": {
    "version": "1.0",
    "acceptedAt": new Date(),
    "response": "yes"
  },
  "timeZone": "UTC",
  "createdAt": new Date()
})
```

## 🧪 Running Tests

### Unit Tests
```bash
./gradlew test
```

### Integration Tests
```bash
# Start test containers
docker-compose -f docker-compose.test.yml up -d

# Run integration tests
./gradlew integrationTest

# Cleanup
docker-compose -f docker-compose.test.yml down
```

### AI Tests
```bash
# Requires Ollama with models
./gradlew aiTest
```

## 🔧 Configuration Options

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profiles | `dev` |
| `MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/niva` |
| `OLLAMA_BASE_URL` | Ollama service URL | `http://localhost:11434` |
| `SIGNAL_WEBHOOK_URL` | Signal webhook endpoint | `http://localhost:8080/signal/webhook` |
| `LOG_LEVEL` | Logging level | `INFO` |

### Application Properties

Key configuration properties in `application.yaml`:

```yaml
# LLM Configuration
ollama:
  base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
  models:
    classification: [MODEL_NAME]
    processing: [MODEL_NAME]
    generation: [MODEL_NAME]

# Signal Configuration
signal:
  cli:
    webhook-url: ${SIGNAL_WEBHOOK_URL}
    base-url: ${SIGNAL_BASE_URL}
    timeout: 30s

# MongoDB Configuration
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/niva}
```

## 🐛 Troubleshooting

### Common Issues

#### Ollama Connection Failed
```bash
# Check Ollama is running
docker-compose logs ollama

# Test connection
curl http://localhost:11434/api/tags
```

#### MongoDB Connection Issues
```bash
# Check MongoDB logs
docker-compose logs mongodb

# Test connection
mongosh mongodb://localhost:27017/niva-dev
```

#### Signal Integration Problems
```bash
# Check Signal CLI mock
curl http://localhost:8081/health

# Verify webhook registration
curl http://localhost:8080/actuator/health
```

### Logging Configuration

Enable debug logging for specific components:

```yaml
logging:
  level:
    icu.neurospicy.fibi.domain.service.friends.interaction: DEBUG
    icu.neurospicy.fibi.outgoing.ollama: DEBUG
    icu.neurospicy.fibi.incoming.signal: DEBUG
```

### Development Tools

#### MongoDB Compass
Connect to `mongodb://localhost:27017` to inspect data.

#### Ollama Web UI
Access Ollama at `http://localhost:11434` for model management.

#### Application Metrics
View Spring Boot metrics at `http://localhost:8080/actuator`.

## 🔄 Development Workflow

1. **Make Changes**: Edit code in your IDE
2. **Run Tests**: `./gradlew test`
3. **Test Locally**: Run application and test via Signal
4. **Integration Tests**: `./gradlew integrationTest`
5. **Commit**: Follow conventional commit format
6. **Push**: Create pull request

## 📞 Getting Help

- 📚 **Documentation**: Check the [Architecture Guide](ARCHITECTURE.md)
- 💬 **Community**: Join our [Discord](https://discord.gg/neurospicy)
- 🐛 **Issues**: Report bugs on [GitHub](https://github.com/your-org/niva/issues)
- 📧 **Email**: [dev@neurospicy.icu](mailto:dev@neurospicy.icu)

---

Happy coding! 🧠💻 