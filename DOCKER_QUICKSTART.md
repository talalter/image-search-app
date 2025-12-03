# Docker Quick Start

One-page guide to get the Image Search Application running with Docker.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose 1.29+
- 8GB+ RAM available
- 10GB+ free disk space

## Start Application (One Command)

```bash
./docker-start.sh
```

Or manually:

```bash
docker-compose up --build -d
```

## Access Application

- **Frontend**: http://localhost:3000
- **API**: http://localhost:8080
- **RabbitMQ UI**: http://localhost:15672 (imageuser/imagepass123)

## Common Commands

```bash
# View logs (all services)
docker-compose logs -f

# View logs (specific service)
docker-compose logs -f java-backend

# Check service status
docker-compose ps

# Restart a service
docker-compose restart java-backend

# Stop all services
docker-compose down

# Stop and remove data (clean slate)
docker-compose down -v

# Rebuild specific service
docker-compose up --build -d java-backend
```

## Services

| Service | Port | Purpose |
|---------|------|---------|
| frontend | 3000 | React UI + Nginx |
| java-backend | 8080 | Spring Boot API |
| python-search-service | 5000 | CLIP + FAISS search |
| python-embedding-worker | - | Async job processor |
| postgres | 5432 | Database |
| rabbitmq | 5672, 15672 | Message queue |

## Troubleshooting

**Services won't start?**
```bash
docker-compose logs
```

**Port conflicts?**
```bash
lsof -i :3000
lsof -i :8080
```

**Want to rebuild from scratch?**
```bash
docker-compose down -v
docker-compose up --build -d
```

**Check service health:**
```bash
docker-compose ps
curl http://localhost:8080/actuator/health
```

## File Structure

```
.
├── docker-compose.yml              # Main orchestration file
├── docker-start.sh                 # Interactive startup script
├── DOCKER_QUICKSTART.md           # This file
├── DOCKER_SETUP_GUIDE.md          # Comprehensive guide
├── DOCKER_FILES_SUMMARY.md        # Complete file reference
├── java-backend/
│   ├── Dockerfile                 # Java backend build
│   └── .dockerignore
├── python-search-service/
│   ├── Dockerfile                 # Search service build
│   └── .dockerignore
├── python-embedding-worker/
│   ├── Dockerfile                 # Worker build
│   └── .dockerignore
└── frontend/
    ├── Dockerfile.frontend        # Frontend build
    └── .dockerignore
```

## First-Time Setup Flow

1. **Start services** (downloads images, builds containers - takes 5-10 min):
   ```bash
   docker-compose up --build -d
   ```

2. **Wait for initialization** (1-2 minutes):
   - PostgreSQL creates database
   - RabbitMQ sets up queues
   - Python Search Service downloads CLIP model (~500MB)
   - Java Backend connects to dependencies

3. **Check status**:
   ```bash
   docker-compose ps
   ```
   All services should show "Up (healthy)"

4. **Open browser**: http://localhost:3000

5. **Register account** and start uploading images

## Architecture

```
User Browser
    ↓
Frontend (React + Nginx) :3000
    ↓
Java Backend (Spring Boot) :8080
    ↓
├─→ PostgreSQL :5432 (metadata)
├─→ RabbitMQ :5672 (job queue)
└─→ Python Search Service :5000
        ↓
    CLIP + FAISS (vector search)
        ↑
    Python Embedding Worker
```

## Data Persistence

Three Docker volumes persist data:

- `postgres-data` - Database
- `rabbitmq-data` - Message queue
- `app-data` - Uploaded images + FAISS indexes

To backup:
```bash
docker run --rm -v image-search-app_app-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/backup.tar.gz /data
```

## Development Tips

**Run only infrastructure (develop locally):**
```bash
docker-compose up -d postgres rabbitmq python-search-service
```

**Hot reload for backend changes:**
```bash
docker-compose up --build -d java-backend
```

**Scale workers for parallel processing:**
```bash
docker-compose up -d --scale python-embedding-worker=3
```

**Access container shell:**
```bash
docker-compose exec java-backend sh
docker-compose exec postgres psql -U imageuser -d imagesearch
```

## Resource Usage

Expected memory usage:
- PostgreSQL: ~100MB
- RabbitMQ: ~150MB
- Python Search Service: ~2GB (CLIP model)
- Java Backend: ~500MB
- Embedding Worker: ~200MB
- Frontend: ~10MB

**Total**: ~3GB RAM

## Need More Help?

- **Comprehensive guide**: `DOCKER_SETUP_GUIDE.md`
- **File reference**: `DOCKER_FILES_SUMMARY.md`
- **Project architecture**: `CLAUDE.md`

## Quick Health Check

```bash
# Check all services are healthy
docker-compose ps

# Test backend API
curl http://localhost:8080/actuator/health

# Test search service
curl http://localhost:5000/

# Check PostgreSQL
docker-compose exec postgres pg_isready -U imageuser

# Check RabbitMQ
docker-compose exec rabbitmq rabbitmq-diagnostics ping
```

## Production Considerations

Before deploying to production:

1. Change default passwords in `docker-compose.yml`
2. Enable HTTPS with reverse proxy (nginx/traefik)
3. Configure backup strategy
4. Set up log aggregation
5. Monitor resource usage
6. Scale embedding workers based on load

## Clean Uninstall

```bash
# Stop and remove everything
docker-compose down -v

# Remove Docker images
docker rmi $(docker images 'image-search-app*' -q)

# Remove project-specific volumes
docker volume rm image-search-app_postgres-data
docker volume rm image-search-app_rabbitmq-data
docker volume rm image-search-app_app-data
```

## Success Indicators

After starting, you should see:

```bash
$ docker-compose ps
NAME                              STATUS              PORTS
imagesearch-embedding-worker      Up (healthy)
imagesearch-frontend              Up (healthy)        0.0.0.0:3000->80/tcp
imagesearch-java-backend          Up (healthy)        0.0.0.0:8080->8080/tcp
imagesearch-postgres              Up (healthy)        0.0.0.0:5432->5432/tcp
imagesearch-python-search         Up (healthy)        0.0.0.0:5000->5000/tcp
imagesearch-rabbitmq              Up (healthy)        0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
```

All services showing "Up (healthy)" means you're ready to go!
