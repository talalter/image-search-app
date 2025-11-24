# Docker Deployment Guide

This guide provides complete instructions for deploying the Image Search Application using Docker. The application supports two different backend architectures.

## Architecture Options

### Option 1: Java Stack (Microservices Architecture)
**File:** `docker-compose.java.yml`

```
┌─────────────┐
│   Frontend  │  :3000 (Nginx)
│   (React)   │
└──────┬──────┘
       │
       ▼
┌─────────────┐       ┌──────────────┐
│ Java Backend│◄─────►│  PostgreSQL  │
│ Spring Boot │ :8080 │   Database   │ :5432
└──────┬──────┘       └──────────────┘
       │
       ▼
┌─────────────┐
│   Search    │
│  Service    │ :5000
│ (CLIP+FAISS)│
└─────────────┘
```

**Components:**
- React Frontend (Nginx) - Port 3000
- Java Spring Boot Backend - Port 8080
- Python Search Microservice (CLIP + FAISS) - Port 5000
- PostgreSQL Database - Port 5432

**Best for:**
- Production deployments
- Microservices architecture
- Scalable applications
- Team projects with separate services

### Option 2: Python Stack (Monolithic)
**File:** `docker-compose.python.yml`

```
┌─────────────┐
│   Frontend  │  :3000 (Nginx)
│   (React)   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Python    │
│   Backend   │  :9999
│ (FastAPI +  │
│ CLIP+FAISS) │
└─────────────┘
```

**Components:**
- React Frontend (Nginx) - Port 3000
- Python FastAPI Backend (with integrated CLIP/FAISS) - Port 9999
- SQLite Database (embedded)

**Best for:**
- Quick deployment
- Development/testing
- Single-server deployments
- Simpler architecture

## Prerequisites

- **Docker**: Version 20.10 or higher
- **Docker Compose**: Version 2.0 or higher
- **System Requirements**:
  - 4GB+ RAM (CLIP model requires ~2GB)
  - 10GB+ disk space
  - x86_64 or ARM64 architecture

### Installation

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install docker.io docker-compose-v2
sudo usermod -aG docker $USER
# Log out and back in for group changes to take effect
```

**macOS:**
```bash
# Install Docker Desktop from: https://www.docker.com/products/docker-desktop
# Docker Compose is included in Docker Desktop
```

**Verify Installation:**
```bash
docker --version
docker compose version
```

## Quick Start

### Option 1: Java Stack (Recommended for Production)

1. **Clone the repository:**
```bash
git clone <repository-url>
cd image-search-app
```

2. **Set environment variables (optional):**
```bash
# Create .env file for sensitive data
echo "POSTGRES_PASSWORD=your_secure_password_here" > .env
```

3. **Build and start services:**
```bash
docker compose -f docker-compose.java.yml up --build -d
```

4. **Wait for services to be healthy:**
```bash
# Check status
docker compose -f docker-compose.java.yml ps

# View logs
docker compose -f docker-compose.java.yml logs -f
```

5. **Access the application:**
- Frontend: http://localhost:3000
- Java Backend API: http://localhost:8080
- Search Service: http://localhost:5000
- PostgreSQL: localhost:5432

6. **Stop services:**
```bash
docker compose -f docker-compose.java.yml down

# To remove volumes (delete all data):
docker compose -f docker-compose.java.yml down -v
```

### Option 2: Python Stack (Quick Deploy)

1. **Clone the repository:**
```bash
git clone <repository-url>
cd image-search-app
```

2. **Build and start services:**
```bash
docker compose -f docker-compose.python.yml up --build -d
```

3. **Access the application:**
- Frontend: http://localhost:3000
- Python Backend API: http://localhost:9999

4. **Stop services:**
```bash
docker compose -f docker-compose.python.yml down

# To remove volumes (delete all data):
docker compose -f docker-compose.python.yml down -v
```

## Configuration

### Environment Variables

#### Java Stack (.env file)
```bash
# PostgreSQL Database
POSTGRES_PASSWORD=changeme123

# Optional: AWS S3 Storage (instead of local storage)
STORAGE_BACKEND=aws
AWS_ACCESS_KEY_ID=your_key
AWS_SECRET_ACCESS_KEY=your_secret
AWS_BUCKET_NAME=your_bucket
AWS_REGION=us-east-1
```

#### Python Stack (.env file)
```bash
# Storage configuration
STORAGE_BACKEND=local

# Optional: AWS S3 Storage
STORAGE_BACKEND=aws
AWS_ACCESS_KEY_ID=your_key
AWS_SECRET_ACCESS_KEY=your_secret
AWS_BUCKET_NAME=your_bucket
AWS_REGION=us-east-1
```

### Port Configuration

If ports 3000, 8080, 5000, or 9999 are already in use, modify the port mappings in the docker-compose files:

```yaml
services:
  frontend:
    ports:
      - "8080:80"  # Change 3000 to 8080 for frontend
```

## Data Persistence

### Java Stack Volumes

| Volume | Purpose | Path in Container |
|--------|---------|-------------------|
| `postgres-data` | PostgreSQL database | `/var/lib/postgresql/data` |
| `java-images` | Uploaded images | `/app/images` |
| `search-indexes` | FAISS vector indexes | `/app/faiss_indexes` |

### Python Stack Volumes

| Volume | Purpose | Path in Container |
|--------|---------|-------------------|
| `python-db` | SQLite database | `/app/data` |
| `python-images` | Uploaded images | `/app/images` |
| `python-indexes` | FAISS vector indexes | `/app/faisses_indexes` |

### Backup Data

**Java Stack:**
```bash
# Backup PostgreSQL database
docker exec imagesearch-postgres pg_dump -U imageuser imagesearch > backup.sql

# Backup images
docker run --rm -v java-images:/data -v $(pwd):/backup alpine \
  tar czf /backup/images-backup.tar.gz -C /data .

# Backup FAISS indexes
docker run --rm -v search-indexes:/data -v $(pwd):/backup alpine \
  tar czf /backup/indexes-backup.tar.gz -C /data .
```

**Python Stack:**
```bash
# Backup SQLite database
docker run --rm -v python-db:/data -v $(pwd):/backup alpine \
  cp /data/database.sqlite /backup/

# Backup images
docker run --rm -v python-images:/data -v $(pwd):/backup alpine \
  tar czf /backup/images-backup.tar.gz -C /data .

# Backup FAISS indexes
docker run --rm -v python-indexes:/data -v $(pwd):/backup alpine \
  tar czf /backup/indexes-backup.tar.gz -C /data .
```

### Restore Data

**Java Stack:**
```bash
# Restore PostgreSQL database
cat backup.sql | docker exec -i imagesearch-postgres psql -U imageuser imagesearch

# Restore images
docker run --rm -v java-images:/data -v $(pwd):/backup alpine \
  tar xzf /backup/images-backup.tar.gz -C /data

# Restore FAISS indexes
docker run --rm -v search-indexes:/data -v $(pwd):/backup alpine \
  tar xzf /backup/indexes-backup.tar.gz -C /data
```

## Troubleshooting

### Check Service Health

```bash
# Java Stack
docker compose -f docker-compose.java.yml ps
docker compose -f docker-compose.java.yml logs <service-name>

# Python Stack
docker compose -f docker-compose.python.yml ps
docker compose -f docker-compose.python.yml logs <service-name>
```

### Common Issues

#### 1. Port Already in Use
```
Error: bind: address already in use
```

**Solution:** Change port mappings in docker-compose file or stop conflicting services.

```bash
# Find what's using the port
sudo lsof -i :8080
sudo kill <PID>
```

#### 2. Out of Memory
```
Error: CLIP model failed to load
```

**Solution:** Increase Docker memory limit (Docker Desktop: Settings > Resources > Memory).

#### 3. Permission Denied
```
Error: permission denied while trying to connect to Docker daemon
```

**Solution:** Add user to docker group:
```bash
sudo usermod -aG docker $USER
# Log out and back in
```

#### 4. Database Connection Failed
```
Error: could not connect to server: Connection refused
```

**Solution:** Wait for PostgreSQL to be healthy:
```bash
docker compose -f docker-compose.java.yml logs postgres
```

#### 5. Frontend Shows Wrong API URL

**Solution:** Rebuild frontend with correct API URL:
```bash
# For Java backend
docker compose -f docker-compose.java.yml build --no-cache frontend

# For Python backend
docker compose -f docker-compose.python.yml build --no-cache frontend
```

### View Service Logs

```bash
# All services
docker compose -f docker-compose.java.yml logs -f

# Specific service
docker compose -f docker-compose.java.yml logs -f java-backend

# Last 100 lines
docker compose -f docker-compose.java.yml logs --tail=100
```

### Restart a Single Service

```bash
docker compose -f docker-compose.java.yml restart java-backend
```

### Rebuild After Code Changes

```bash
# Rebuild all services
docker compose -f docker-compose.java.yml up --build -d

# Rebuild specific service
docker compose -f docker-compose.java.yml build --no-cache java-backend
docker compose -f docker-compose.java.yml up -d java-backend
```

## Production Deployment Best Practices

### 1. Use Environment Variables for Secrets

Never hardcode passwords in docker-compose files. Use `.env` files:

```bash
# .env
POSTGRES_PASSWORD=super_secure_password_here
JWT_SECRET=random_secret_key_here
```

### 2. Enable HTTPS with Reverse Proxy

Use Nginx or Traefik as a reverse proxy with Let's Encrypt SSL:

```yaml
services:
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
```

### 3. Resource Limits

Add resource limits to prevent services from consuming all system resources:

```yaml
services:
  java-backend:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
```

### 4. Health Checks

All services include health checks. Monitor them:

```bash
docker compose -f docker-compose.java.yml ps
# Look for "healthy" status
```

### 5. Logging

Configure log rotation to prevent disk space issues:

```yaml
services:
  java-backend:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

### 6. Security

- Use non-root users in containers (already configured)
- Keep images updated: `docker compose pull`
- Scan for vulnerabilities: `docker scan imagesearch-java-backend`
- Use secrets management for production

### 7. Monitoring

Consider adding monitoring services:

```yaml
services:
  prometheus:
    image: prom/prometheus
    # Add Prometheus config for metrics

  grafana:
    image: grafana/grafana
    # Add Grafana for visualization
```

## Development Workflow

### Hot Reload During Development

For development with hot reload, use volume mounts:

```yaml
services:
  java-backend:
    volumes:
      - ./java-backend/src:/app/src  # Mount source code
```

Then use your IDE or `./gradlew bootRun` for hot reload.

### Run Tests in Docker

```bash
# Java tests
docker compose -f docker-compose.java.yml exec java-backend ./gradlew test

# Python tests
docker compose -f docker-compose.python.yml exec python-backend pytest
```

## Performance Optimization

### 1. Use Multi-Stage Builds
All Dockerfiles use multi-stage builds to minimize image size.

### 2. Layer Caching
Dependencies are copied before source code for better caching.

### 3. CPU-Only PyTorch
For production without GPU, use CPU-only PyTorch to reduce image size:

```dockerfile
RUN pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
```

## Image Sizes

| Service | Size | Notes |
|---------|------|-------|
| Frontend | ~25MB | Alpine Nginx with static build |
| Java Backend | ~300MB | JRE 17 + Spring Boot JAR |
| Search Service | ~1.8GB | Python + PyTorch + CLIP |
| Python Backend | ~1.8GB | FastAPI + PyTorch + CLIP |
| PostgreSQL | ~200MB | Official Alpine image |

## FAQ

### Q: Which stack should I use?
**A:** Use Java stack for production/scalability, Python stack for quick testing.

### Q: Can I use both stacks simultaneously?
**A:** No, they use conflicting ports and serve the same purpose. Choose one.

### Q: How do I switch between stacks?
**A:** Stop one stack, start the other:
```bash
docker compose -f docker-compose.python.yml down
docker compose -f docker-compose.java.yml up -d
```

### Q: Can I use AWS S3 instead of local storage?
**A:** Yes, set `STORAGE_BACKEND=aws` and provide AWS credentials in `.env`.

### Q: How do I upgrade to a new version?
**A:**
```bash
git pull
docker compose -f docker-compose.java.yml down
docker compose -f docker-compose.java.yml pull
docker compose -f docker-compose.java.yml up -d --build
```

### Q: How do I scale services?
**A:**
```bash
# Scale search service to 3 instances
docker compose -f docker-compose.java.yml up -d --scale search-service=3
```

## Support

For issues or questions:
- Check logs: `docker compose logs -f`
- GitHub Issues: [repository-url]/issues
- Documentation: See main README.md

## License

MIT License - see LICENSE file for details.
