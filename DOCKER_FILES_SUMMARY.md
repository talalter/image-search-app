# Docker Setup - Files Summary

This document provides a complete reference of all Docker-related files created for the Image Search Application.

## Created Files

### Dockerfiles

1. **Java Backend Dockerfile**
   - Path: `/home/tal-alter/Documents/image-search-app/java-backend/Dockerfile`
   - Base Image: `eclipse-temurin:17-jre-alpine`
   - Build: Multi-stage (JDK build → JRE runtime)
   - Size: ~200MB (optimized)
   - Features:
     - Gradle dependency caching
     - Non-root user (appuser)
     - Health check via actuator
     - JVM container optimizations

2. **Python Search Service Dockerfile**
   - Path: `/home/tal-alter/Documents/image-search-app/python-search-service/Dockerfile`
   - Base Image: `python:3.11-slim`
   - Build: Multi-stage (dependencies → runtime)
   - Size: ~2GB (includes CLIP model and PyTorch CPU)
   - Features:
     - Virtual environment isolation
     - Non-root user (appuser)
     - CPU-optimized PyTorch
     - CLIP and FAISS pre-installed

3. **Python Embedding Worker Dockerfile**
   - Path: `/home/tal-alter/Documents/image-search-app/python-embedding-worker/Dockerfile`
   - Base Image: `python:3.11-slim`
   - Build: Single-stage (minimal dependencies)
   - Size: ~200MB
   - Features:
     - Non-root user (worker)
     - RabbitMQ consumer
     - Lightweight runtime

4. **Frontend Dockerfile**
   - Path: `/home/tal-alter/Documents/image-search-app/frontend/Dockerfile.frontend`
   - Base Image: `nginx:1.25-alpine`
   - Build: Multi-stage (Node build → Nginx serve)
   - Size: ~30MB (static files only)
   - Features:
     - npm ci for reproducible builds
     - Build argument for backend selection
     - Nginx with API proxy configuration
     - Optimized caching headers

### Docker Ignore Files

1. **Java Backend .dockerignore**
   - Path: `/home/tal-alter/Documents/image-search-app/java-backend/.dockerignore`
   - Excludes: build artifacts, IDE files, data volumes, Git files

2. **Python Search Service .dockerignore**
   - Path: `/home/tal-alter/Documents/image-search-app/python-search-service/.dockerignore`
   - Excludes: Python cache, venv, indexes, IDE files, Git files

3. **Python Embedding Worker .dockerignore**
   - Path: `/home/tal-alter/Documents/image-search-app/python-embedding-worker/.dockerignore`
   - Excludes: Python cache, venv, data volumes, IDE files, Git files

4. **Frontend .dockerignore**
   - Path: `/home/tal-alter/Documents/image-search-app/frontend/.dockerignore`
   - Excludes: node_modules, build output, IDE files, environment files

### Orchestration Files

1. **Docker Compose Configuration**
   - Path: `/home/tal-alter/Documents/image-search-app/docker-compose.yml`
   - Services: 6 (postgres, rabbitmq, python-search-service, java-backend, python-embedding-worker, frontend)
   - Networks: 1 custom bridge (app-network)
   - Volumes: 3 named volumes (postgres-data, rabbitmq-data, app-data)
   - Features:
     - Service dependency ordering with health checks
     - Resource limits for each service
     - Environment variable configuration
     - Restart policies
     - Health checks for all services

### Documentation

1. **Docker Setup Guide**
   - Path: `/home/tal-alter/Documents/image-search-app/DOCKER_SETUP_GUIDE.md`
   - Contents:
     - Architecture overview with diagram
     - Quick start instructions
     - Detailed operations guide
     - Service-specific configurations
     - Troubleshooting section
     - Production considerations

2. **This Summary Document**
   - Path: `/home/tal-alter/Documents/image-search-app/DOCKER_FILES_SUMMARY.md`

### Scripts

1. **Docker Startup Script**
   - Path: `/home/tal-alter/Documents/image-search-app/docker-start.sh`
   - Executable: Yes (chmod +x applied)
   - Features:
     - Pre-flight validation checks
     - Port conflict detection
     - Disk space verification
     - Interactive build options
     - Service status display

## Quick Command Reference

### Start Everything

```bash
# Using startup script (recommended)
./docker-start.sh

# Direct Docker Compose
docker-compose up --build -d
```

### Stop Everything

```bash
docker-compose down
```

### View Logs

```bash
docker-compose logs -f
```

### Rebuild Specific Service

```bash
docker-compose up --build -d [service-name]
```

### Service Names

- `postgres` - PostgreSQL database
- `rabbitmq` - RabbitMQ message queue
- `python-search-service` - CLIP + FAISS search
- `java-backend` - Spring Boot API
- `python-embedding-worker` - Async embedding processor
- `frontend` - React + Nginx

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Docker Host (Linux)                      │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              app-network (bridge)                       │ │
│  │                                                          │ │
│  │  ┌─────────────┐      ┌──────────────┐                │ │
│  │  │  Frontend   │      │ Java Backend │                │ │
│  │  │  (Nginx)    │─────▶│ (Spring Boot)│                │ │
│  │  │  Port: 3000 │      │  Port: 8080  │                │ │
│  │  └─────────────┘      └───────┬──────┘                │ │
│  │                                │                         │ │
│  │                         ┌──────┴────────┐               │ │
│  │                         │               │               │ │
│  │                   ┌─────▼─────┐  ┌─────▼─────┐        │ │
│  │                   │ PostgreSQL │  │  RabbitMQ │        │ │
│  │                   │ Port: 5432 │  │Port: 5672 │        │ │
│  │                   └────────────┘  └─────┬─────┘        │ │
│  │                                          │               │ │
│  │                                   ┌──────▼────────┐     │ │
│  │                                   │   Embedding   │     │ │
│  │                                   │    Worker     │     │ │
│  │                                   └──────┬────────┘     │ │
│  │                                          │               │ │
│  │                                   ┌──────▼────────┐     │ │
│  │                                   │Python Search  │     │ │
│  │                                   │   Service     │     │ │
│  │                                   │ (CLIP+FAISS)  │     │ │
│  │                                   │  Port: 5000   │     │ │
│  │                                   └───────────────┘     │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                              │
│  Volumes:                                                    │
│  • postgres-data    (Database persistence)                  │
│  • rabbitmq-data    (Queue persistence)                     │
│  • app-data         (Uploads + FAISS indexes - shared)      │
└─────────────────────────────────────────────────────────────┘
```

## Build Optimization Summary

### Layer Caching Strategy

1. **Java Backend**
   - Copy Gradle files first
   - Download dependencies (cached layer)
   - Copy source code last
   - Result: Only rebuilds app code on changes

2. **Python Services**
   - Copy requirements.txt first
   - Install dependencies (cached layer)
   - Copy source code last
   - Result: Fast rebuilds during development

3. **Frontend**
   - Copy package.json first
   - Run npm ci (cached layer)
   - Copy source code
   - Build React app
   - Copy to Nginx image (multi-stage)
   - Result: Minimal final image size

### Image Sizes (Approximate)

| Service | Image Size | Notes |
|---------|-----------|-------|
| Java Backend | ~200MB | JRE-only in final stage |
| Python Search | ~2GB | Includes PyTorch CPU + CLIP |
| Embedding Worker | ~200MB | Minimal Python runtime |
| Frontend | ~30MB | Static files + Nginx |
| PostgreSQL | ~240MB | Official alpine image |
| RabbitMQ | ~180MB | Management plugin included |

**Total**: ~2.85GB disk space for all images

## Environment Variables Reference

### Java Backend

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/imagesearch
DB_USERNAME=imageuser
DB_PASSWORD=imagepass123
SEARCH_SERVICE_URL=http://python-search-service:5000
SEARCH_BACKEND=python
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=imageuser
RABBITMQ_PASS=imagepass123
STORAGE_BACKEND=local
IMAGES_ROOT=/app/data/uploads
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

### Python Search Service

```env
PYTHONUNBUFFERED=1
```

### Python Embedding Worker

```env
RABBITMQ_HOST=rabbitmq
RABBITMQ_USER=imageuser
RABBITMQ_PASS=imagepass123
SEARCH_SERVICE_URL=http://python-search-service:5000
DB_HOST=postgres
DB_NAME=imagesearch
DB_USERNAME=imageuser
DB_PASSWORD=imagepass123
PYTHONUNBUFFERED=1
```

### Frontend Build Args

```env
REACT_APP_BACKEND=java
```

## Volume Mapping Details

### app-data Volume

Shared by multiple services:

```
/app/data/
├── uploads/           # Uploaded images
│   └── {userId}/
│       └── {folderId}/
│           └── *.jpg/png/etc
└── indexes/           # FAISS vector indexes
    └── {userId}/
        └── {folderId}/
            └── *.faiss
```

**Mounted by**:
- Java Backend (read/write)
- Python Search Service (read/write)
- Python Embedding Worker (read-only)

### postgres-data Volume

Contains PostgreSQL data directory at `/var/lib/postgresql/data`

### rabbitmq-data Volume

Contains RabbitMQ data at `/var/lib/rabbitmq`

## Health Checks

All services include health checks for reliable orchestration:

| Service | Endpoint/Command | Interval | Start Period |
|---------|-----------------|----------|--------------|
| PostgreSQL | `pg_isready` | 10s | 10s |
| RabbitMQ | `rabbitmq-diagnostics ping` | 30s | 30s |
| Python Search | `GET http://localhost:5000/` | 30s | 90s |
| Java Backend | `GET /actuator/health` | 30s | 60s |
| Embedding Worker | Process check | 30s | 30s |
| Frontend | `GET http://localhost:80/` | 30s | 30s |

## Resource Limits

Configured in docker-compose.yml:

| Service | Memory Limit | Memory Reservation |
|---------|-------------|-------------------|
| PostgreSQL | (default) | (default) |
| RabbitMQ | (default) | (default) |
| Python Search | 4GB | 2GB |
| Java Backend | 1GB | 512MB |
| Embedding Worker | 512MB | 256MB |
| Frontend | (default) | (default) |

**Total Reserved**: ~3GB RAM minimum

## Network Configuration

**Network Name**: `app-network`
**Driver**: bridge
**Subnet**: Auto-assigned by Docker

**Internal DNS**: All services can reach each other by service name
- Example: `http://java-backend:8080`
- Example: `http://python-search-service:5000`

## Ports Exposed to Host

| Port | Service | Purpose |
|------|---------|---------|
| 3000 | Frontend | Web UI |
| 5000 | Python Search | API (optional external access) |
| 5432 | PostgreSQL | Database (optional external access) |
| 5672 | RabbitMQ | AMQP protocol |
| 8080 | Java Backend | REST API |
| 15672 | RabbitMQ | Management UI |

## Next Steps

1. **Start the application**:
   ```bash
   ./docker-start.sh
   ```

2. **Access the UI**:
   - Open http://localhost:3000

3. **Monitor services**:
   ```bash
   docker-compose logs -f
   ```

4. **Read the full guide**:
   - See `DOCKER_SETUP_GUIDE.md` for detailed operations

## Maintenance

### Update Docker Images

```bash
# Pull latest base images
docker-compose pull

# Rebuild with latest bases
docker-compose build --no-cache
docker-compose up -d
```

### Clean Up Unused Resources

```bash
# Remove stopped containers
docker container prune -f

# Remove unused images
docker image prune -a -f

# Remove unused volumes (CAUTION: data loss)
docker volume prune -f

# Remove unused networks
docker network prune -f

# All-in-one cleanup
docker system prune -a --volumes -f
```

### Backup Data

```bash
# Backup PostgreSQL
docker-compose exec -T postgres pg_dump -U imageuser imagesearch > backup.sql

# Backup app-data volume
docker run --rm -v image-search-app_app-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/app-data-backup.tar.gz /data
```

## Troubleshooting Quick Reference

**Services won't start**: Check logs with `docker-compose logs [service]`

**Port conflicts**: Use `lsof -i :[port]` to find conflicting processes

**Out of memory**: Increase Docker Desktop memory allocation or adjust limits in docker-compose.yml

**Database connection fails**: Ensure PostgreSQL health check passes: `docker-compose ps`

**Frontend can't reach backend**: Check Nginx config in container: `docker-compose exec frontend cat /etc/nginx/conf.d/default.conf`

**CLIP model download fails**: Restart search service: `docker-compose restart python-search-service`

For detailed troubleshooting, see `DOCKER_SETUP_GUIDE.md`.
