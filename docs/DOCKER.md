# Docker Deployment Guide

## Quick Start with Docker

The easiest way to run the Image Search App is using Docker Compose:

```bash
# Build and start all services
docker-compose up --build

# Or run in detached mode
docker-compose up -d --build
```

The application will be available at:
- **Frontend:** http://localhost:3000
- **Backend API:** http://localhost:9999

## Architecture

The Docker setup includes:

1. **Backend Container** (FastAPI + Python 3.12)
   - Runs on port 9999
   - Includes CLIP model and FAISS for vector search
   - Persists data via volume mounts

2. **Frontend Container** (React + Nginx)
   - Runs on port 80 (mapped to host port 3000)
   - Serves optimized production build
   - Proxies API requests to backend

3. **Network**
   - Both containers communicate via `image-search-network`
   - Frontend can reach backend at `http://backend:9999`

## Docker Commands

### Basic Operations

```bash
# Start services
docker-compose up

# Start in background
docker-compose up -d

# Stop services
docker-compose down

# Stop and remove volumes (WARNING: deletes all data)
docker-compose down -v

# View logs
docker-compose logs

# View logs for specific service
docker-compose logs backend
docker-compose logs frontend

# Follow logs in real-time
docker-compose logs -f
```

### Rebuild After Changes

```bash
# Rebuild all images
docker-compose build

# Rebuild specific service
docker-compose build backend
docker-compose build frontend

# Rebuild and restart
docker-compose up --build
```

### Container Management

```bash
# List running containers
docker-compose ps

# Execute command in backend container
docker-compose exec backend python -c "from database import init_db; init_db()"

# Access backend shell
docker-compose exec backend /bin/bash

# Access frontend shell
docker-compose exec frontend /bin/sh
```

## Data Persistence

The following directories are mounted as volumes to persist data:

- `./backend/images` - Uploaded images
- `./backend/faisses_indexes` - FAISS vector indexes
- `./backend/database.sqlite` - SQLite database

**Note:** These directories will be created automatically when you start the containers.

## Environment Variables

You can customize the backend by creating a `.env` file in the backend directory:

```env
STORAGE_BACKEND=local
AWS_ACCESS_KEY_ID=your_key
AWS_SECRET_ACCESS_KEY=your_secret
AWS_BUCKET_NAME=your_bucket
AWS_REGION=us-east-1
```

Then update `docker-compose.yml` to use the env file:

```yaml
backend:
  env_file:
    - ./backend/.env
```

## Production Deployment

### Using Docker Hub

```bash
# Tag images
docker tag image-search-app_backend:latest your-username/image-search-backend:latest
docker tag image-search-app_frontend:latest your-username/image-search-frontend:latest

# Push to Docker Hub
docker push your-username/image-search-backend:latest
docker push your-username/image-search-frontend:latest
```

### Using Different Ports

Edit `docker-compose.yml` to change exposed ports:

```yaml
frontend:
  ports:
    - "8080:80"  # Change from 3000 to 8080

backend:
  ports:
    - "8000:9999"  # Change from 9999 to 8000
```

### Resource Limits

Add resource constraints for production:

```yaml
backend:
  deploy:
    resources:
      limits:
        cpus: '2'
        memory: 4G
      reservations:
        cpus: '1'
        memory: 2G
```

## Health Checks

Both containers include health checks:

```bash
# Check container health
docker-compose ps

# Manual health check
curl http://localhost:9999/  # Backend
curl http://localhost:3000/  # Frontend
```

## Troubleshooting

### Backend won't start

```bash
# Check logs
docker-compose logs backend

# Common issues:
# - Port 9999 already in use
# - Insufficient memory for CLIP model
# - Missing dependencies
```

### Frontend can't reach backend

```bash
# Verify network
docker network inspect image-search-app_image-search-network

# Check if backend is running
docker-compose ps

# Test backend from frontend container
docker-compose exec frontend wget -O- http://backend:9999/
```

### Permission issues with volumes

```bash
# Fix permissions on Linux
sudo chown -R $USER:$USER ./backend/images
sudo chown -R $USER:$USER ./backend/faisses_indexes
```

### Clean slate restart

```bash
# Stop everything and remove volumes
docker-compose down -v

# Remove images
docker-compose down --rmi all

# Rebuild from scratch
docker-compose up --build
```

## Development Mode

For development, you can override the Docker setup to use live reload:

Create `docker-compose.dev.yml`:

```yaml
version: '3.8'

services:
  backend:
    volumes:
      - ./backend:/app
    command: uvicorn api:app --reload --host 0.0.0.0 --port 9999

  frontend:
    build:
      context: ./frontend
      target: build  # Stop at build stage
    command: npm start
    ports:
      - "3000:3000"
    volumes:
      - ./frontend/src:/app/src
    environment:
      - CHOKIDAR_USEPOLLING=true
```

Run with:
```bash
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up
```

## Security Considerations

For production deployment:

1. **Use secrets management** for sensitive credentials
2. **Enable HTTPS** with a reverse proxy (e.g., Traefik, Nginx)
3. **Set up proper firewall rules**
4. **Use non-root users** in containers
5. **Scan images** for vulnerabilities with `docker scan`
6. **Limit resource usage** with deploy constraints

## Performance Optimization

### Build optimization

```bash
# Use BuildKit for faster builds
DOCKER_BUILDKIT=1 docker-compose build

# Use build cache
docker-compose build --pull
```

### Image size reduction

The Dockerfiles already use:
- Multi-stage builds (frontend)
- Slim base images (`python:3.12-slim`, `nginx:alpine`)
- `.dockerignore` to exclude unnecessary files

### Memory considerations

The CLIP model requires ~1GB of RAM. Ensure your Docker daemon has sufficient memory allocated:
- **Minimum:** 2GB
- **Recommended:** 4GB+

## Monitoring

Add monitoring with docker-compose:

```yaml
services:
  # ... existing services ...
  
  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    networks:
      - image-search-network
```

## Backup and Recovery

### Backup data

```bash
# Create backup directory
mkdir backups

# Backup database
docker-compose exec backend cp database.sqlite /tmp/
docker cp image-search-backend:/tmp/database.sqlite ./backups/

# Backup images and indexes
tar -czf backups/data-$(date +%Y%m%d).tar.gz backend/images backend/faisses_indexes
```

### Restore data

```bash
# Restore database
docker cp ./backups/database.sqlite image-search-backend:/app/

# Restore images and indexes
tar -xzf backups/data-20250103.tar.gz
```

## Next Steps

- Review the [main README](README.md) for application usage
- Check out the [.github/copilot-instructions.md](.github/copilot-instructions.md) for architecture details
- Explore the Jupyter notebooks in `/notebooks` for data analysis
