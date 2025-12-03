# Docker Setup Guide

This guide provides comprehensive instructions for running the Image Search Application using Docker and Docker Compose.

## Architecture Overview

The Docker setup includes 6 services in a microservices architecture:

```
┌─────────────────┐
│   Frontend      │  React + Nginx (Port 3000)
│  (Nginx:80)     │
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  Java Backend   │  Spring Boot REST API (Port 8080)
│   (Port 8080)   │
└─┬───────────┬───┘
  │           │
  ↓           ↓
┌──────────┐ ┌────────────────┐
│PostgreSQL│ │   RabbitMQ     │
│(Port 5432)│ │ (Ports 5672,   │
└──────────┘ │    15672)      │
             └────────┬───────┘
                      │
                      ↓
           ┌──────────────────┐
           │ Python Embedding │
           │     Worker       │
           └────────┬─────────┘
                    │
                    ↓
         ┌─────────────────────┐
         │ Python Search Service│  CLIP + FAISS (Port 5000)
         │     (Port 5000)      │
         └─────────────────────┘
```

### Services

1. **PostgreSQL Database** - Data persistence for users, folders, images, sessions
2. **RabbitMQ** - Message queue for asynchronous embedding job processing
3. **Python Search Service** - CLIP embeddings and FAISS vector similarity search
4. **Java Backend** - Spring Boot REST API handling all business logic
5. **Python Embedding Worker** - Consumes RabbitMQ messages to process embeddings
6. **Frontend** - React SPA served by Nginx with API proxying

### Data Volumes

- `postgres-data` - PostgreSQL database files
- `rabbitmq-data` - RabbitMQ persistent queue data
- `app-data` - Shared volume containing:
  - `/app/data/uploads/` - Uploaded images
  - `/app/data/indexes/` - FAISS vector indexes

## Quick Start

### Prerequisites

- Docker Engine 20.10+
- Docker Compose 1.29+
- At least 8GB RAM available for Docker
- 10GB free disk space

### One-Command Startup

```bash
# Build and start all services
docker-compose up --build -d

# View logs
docker-compose logs -f

# Check service status
docker-compose ps
```

Access the application:
- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **RabbitMQ Management**: http://localhost:15672 (user: imageuser, pass: imagepass123)
- **PostgreSQL**: localhost:5432

### Stopping Services

```bash
# Stop all services (preserves data volumes)
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

## Detailed Operations

### Building Services

```bash
# Build all services
docker-compose build

# Build specific service
docker-compose build java-backend

# Build without cache (force rebuild)
docker-compose build --no-cache

# Build with parallel processing
docker-compose build --parallel
```

### Starting Services

```bash
# Start all services in foreground (see logs)
docker-compose up

# Start all services in background (detached mode)
docker-compose up -d

# Start specific services
docker-compose up postgres rabbitmq
docker-compose up java-backend python-search-service

# Build and start together
docker-compose up --build -d
```

### Viewing Logs

```bash
# View all service logs
docker-compose logs

# Follow logs in real-time
docker-compose logs -f

# View specific service logs
docker-compose logs -f java-backend
docker-compose logs -f python-search-service

# View last 100 lines
docker-compose logs --tail=100

# View logs since specific time
docker-compose logs --since 2025-12-03T10:00:00
```

### Service Management

```bash
# Restart all services
docker-compose restart

# Restart specific service
docker-compose restart java-backend

# Stop specific service
docker-compose stop python-embedding-worker

# Start stopped service
docker-compose start python-embedding-worker

# Recreate service (with new config)
docker-compose up -d --force-recreate java-backend
```

### Debugging

```bash
# Access running container shell
docker-compose exec java-backend sh
docker-compose exec postgres sh

# Run command in container
docker-compose exec java-backend wget http://localhost:8080/actuator/health

# View container resource usage
docker stats

# Inspect service configuration
docker-compose config

# View service details
docker inspect imagesearch-java-backend
```

### Volume Management

```bash
# List volumes
docker volume ls

# Inspect volume
docker volume inspect image-search-app_app-data

# Remove specific volume (DESTRUCTIVE)
docker volume rm image-search-app_app-data

# Backup app data volume
docker run --rm -v image-search-app_app-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/app-data-backup.tar.gz /data

# Restore app data volume
docker run --rm -v image-search-app_app-data:/data -v $(pwd):/backup \
  alpine tar xzf /backup/app-data-backup.tar.gz -C /
```

### Health Checks

```bash
# Check service health status
docker-compose ps

# View health check logs
docker inspect --format='{{json .State.Health}}' imagesearch-postgres | python3 -m json.tool

# Manual health checks
curl http://localhost:8080/actuator/health
curl http://localhost:5000/
psql -h localhost -U imageuser -d imagesearch -c "SELECT 1;"
```

## Service-Specific Details

### Java Backend

**Dockerfile**: `/home/tal-alter/Documents/image-search-app/java-backend/Dockerfile`

**Multi-stage build**:
- Stage 1: Build with Gradle (eclipse-temurin:17-jdk-alpine)
- Stage 2: Runtime with JRE (eclipse-temurin:17-jre-alpine)

**Environment Variables**:
- `SPRING_DATASOURCE_URL` - PostgreSQL JDBC URL
- `DB_USERNAME`, `DB_PASSWORD` - Database credentials
- `SEARCH_SERVICE_URL` - Python search service endpoint
- `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASS` - RabbitMQ config
- `IMAGES_ROOT` - Path to uploaded images
- `JAVA_OPTS` - JVM tuning parameters

**Rebuild after code changes**:
```bash
docker-compose up --build -d java-backend
```

### Python Search Service

**Dockerfile**: `/home/tal-alter/Documents/image-search-app/python-search-service/Dockerfile`

**Multi-stage build**:
- Stage 1: Build dependencies with git (python:3.11-slim)
- Stage 2: Runtime with CLIP and FAISS (python:3.11-slim)

**Features**:
- CLIP model downloaded on first run (~500MB)
- CPU-optimized PyTorch build
- FAISS for fast vector similarity search

**Memory Requirements**: 2-4GB RAM

**Rebuild after code changes**:
```bash
docker-compose up --build -d python-search-service
```

### Python Embedding Worker

**Dockerfile**: `/home/tal-alter/Documents/image-search-app/python-embedding-worker/Dockerfile`

**Purpose**: Consumes embedding jobs from RabbitMQ queue and calls search service

**Environment Variables**:
- `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASS` - Queue connection
- `SEARCH_SERVICE_URL` - Search service endpoint
- `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD` - Database connection

**Scaling workers**:
```bash
docker-compose up -d --scale python-embedding-worker=3
```

### Frontend

**Dockerfile**: `/home/tal-alter/Documents/image-search-app/frontend/Dockerfile.frontend`

**Multi-stage build**:
- Stage 1: Build React app (node:18-alpine)
- Stage 2: Serve with Nginx (nginx:1.25-alpine)

**Build Arguments**:
- `REACT_APP_BACKEND=java` - Backend selection (baked into build)

**Nginx Configuration**: Proxies API calls to Java backend

**Rebuild after code changes**:
```bash
docker-compose up --build -d frontend
```

## Troubleshooting

### Services Won't Start

```bash
# Check logs for errors
docker-compose logs

# Verify ports are available
lsof -i :3000
lsof -i :8080
lsof -i :5000
lsof -i :5432
lsof -i :5672
lsof -i :15672

# Clean restart
docker-compose down
docker-compose up -d
```

### Database Connection Issues

```bash
# Check PostgreSQL health
docker-compose exec postgres pg_isready -U imageuser

# Access PostgreSQL shell
docker-compose exec postgres psql -U imageuser -d imagesearch

# View database logs
docker-compose logs postgres
```

### Java Backend Fails to Connect to Search Service

```bash
# Check search service is running
docker-compose ps python-search-service

# Test connectivity from backend container
docker-compose exec java-backend wget -O- http://python-search-service:5000/

# Check network
docker network inspect image-search-app_app-network
```

### Out of Memory Errors

```bash
# Check current resource usage
docker stats

# Increase memory limits in docker-compose.yml
# Then restart:
docker-compose down
docker-compose up -d
```

### RabbitMQ Connection Refused

```bash
# Check RabbitMQ health
docker-compose exec rabbitmq rabbitmq-diagnostics ping

# View RabbitMQ logs
docker-compose logs rabbitmq

# Access management UI
open http://localhost:15672
```

### Frontend Can't Reach Backend

1. Check Nginx configuration in container:
```bash
docker-compose exec frontend cat /etc/nginx/conf.d/default.conf
```

2. Check backend is accessible from frontend container:
```bash
docker-compose exec frontend wget -O- http://java-backend:8080/actuator/health
```

3. Verify network connectivity:
```bash
docker-compose exec frontend ping java-backend
```

### CLIP Model Download Issues

The Python search service downloads the CLIP model on first run. If it fails:

```bash
# View logs
docker-compose logs -f python-search-service

# Restart to retry download
docker-compose restart python-search-service

# If persistent, rebuild without cache
docker-compose build --no-cache python-search-service
docker-compose up -d python-search-service
```

## Production Considerations

### Security

1. **Change default passwords** in docker-compose.yml:
   - PostgreSQL: `POSTGRES_PASSWORD`
   - RabbitMQ: `RABBITMQ_DEFAULT_PASS`

2. **Use secrets** instead of environment variables:
```yaml
services:
  postgres:
    environment:
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    secrets:
      - db_password

secrets:
  db_password:
    file: ./secrets/db_password.txt
```

3. **Run containers as non-root** (already implemented in Dockerfiles)

### Performance

1. **Resource Limits**: Adjust memory limits based on load:
```yaml
services:
  python-search-service:
    deploy:
      resources:
        limits:
          memory: 8G  # Increase for production
```

2. **Scale workers** for parallel processing:
```bash
docker-compose up -d --scale python-embedding-worker=5
```

3. **Enable HTTP/2 in Nginx** for frontend

### Monitoring

1. **Java Backend Actuator**: http://localhost:8080/actuator
   - `/health` - Health status
   - `/metrics` - Application metrics
   - `/circuitbreakers` - Circuit breaker status

2. **RabbitMQ Management**: http://localhost:15672
   - Monitor queue depth
   - View message rates
   - Check consumer status

3. **Log aggregation**: Configure log drivers
```yaml
services:
  java-backend:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

### Backup Strategy

```bash
# Automated backup script
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
docker-compose exec -T postgres pg_dump -U imageuser imagesearch > backup_${DATE}.sql
docker run --rm -v image-search-app_app-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/data_${DATE}.tar.gz /data
```

## Development Workflow

### Local Development with Hot Reload

For development, you may want to run some services locally:

```bash
# Start only infrastructure services
docker-compose up -d postgres rabbitmq python-search-service

# Run Java backend locally
cd java-backend
./gradlew bootRun

# Run frontend locally
cd frontend
npm start
```

### Hybrid Setup

Edit docker-compose.yml to comment out services you run locally, adjusting ports and hostnames accordingly.

## Common Commands Reference

```bash
# Build and start
docker-compose up --build -d

# View logs
docker-compose logs -f [service-name]

# Restart service
docker-compose restart [service-name]

# Rebuild single service
docker-compose up --build -d [service-name]

# Stop all
docker-compose down

# Clean slate (removes volumes)
docker-compose down -v

# Scale workers
docker-compose up -d --scale python-embedding-worker=3

# Access container shell
docker-compose exec [service-name] sh

# View resource usage
docker stats
```

## Additional Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot Docker Guide](https://spring.io/guides/gs/spring-boot-docker/)
- [Nginx Docker Documentation](https://hub.docker.com/_/nginx)
- Project Architecture: `/home/tal-alter/Documents/image-search-app/CLAUDE.md`
