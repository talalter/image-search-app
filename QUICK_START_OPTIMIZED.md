# Quick Start Guide - Optimized Docker Setup

## Prerequisites
- Docker 28.2+ installed
- Docker Compose installed
- At least 4GB RAM available for Docker
- Ports 3000, 5001, 5432, 8080, 9200 available

## First Time Setup

### 1. Build All Services
```bash
cd /home/tal-alter/Documents/image-search-app
docker-compose -f docker-compose.optimized.yml build
```

**Expected time:** ~8 minutes (first build)
- Downloads base images (~2 min)
- Downloads CLIP models (~3 min)
- Compiles Java services (~2 min)
- Builds React frontend (~1 min)

### 2. Start All Services
```bash
docker-compose -f docker-compose.optimized.yml up -d
```

**Services started:**
- PostgreSQL (port 5432)
- Elasticsearch (port 9200)
- Java Search Service (port 5001)
- Java Backend (port 8080)
- React Frontend (port 3000)

### 3. Run Warmup Script
```bash
./scripts/warmup.sh
```

This ensures all services are healthy and ready. Expected output:
```
✓ PostgreSQL
✓ Elasticsearch
✓ Java Search Service
✓ Java Backend
✓ Frontend (Nginx)

Ready for demo! 🚀
```

### 4. Access Application
Open browser: http://localhost:3000

---

## Daily Usage

### Start Services
```bash
docker-compose -f docker-compose.optimized.yml up -d
./scripts/warmup.sh
```

### Stop Services
```bash
docker-compose -f docker-compose.optimized.yml down
```

### View Logs
```bash
# All services
docker-compose -f docker-compose.optimized.yml logs -f

# Specific service
docker-compose -f docker-compose.optimized.yml logs -f java-backend

# Last 100 lines
docker-compose -f docker-compose.optimized.yml logs --tail=100 java-search-service
```

### Restart Single Service
```bash
docker-compose -f docker-compose.optimized.yml restart java-backend
```

---

## After Code Changes

### Backend Code Changed
```bash
# Rebuild only backend
docker-compose -f docker-compose.optimized.yml build java-backend

# Restart backend
docker-compose -f docker-compose.optimized.yml up -d java-backend
```

**Time:** ~1-2 minutes (uses cached dependencies)

### Frontend Code Changed
```bash
# Rebuild only frontend
docker-compose -f docker-compose.optimized.yml build frontend

# Restart frontend
docker-compose -f docker-compose.optimized.yml up -d frontend
```

**Time:** ~30 seconds (uses cached npm packages)

### Search Service Changed
```bash
# Rebuild search service (keeps existing models)
docker-compose -f docker-compose.optimized.yml build java-search-service

# Restart service
docker-compose -f docker-compose.optimized.yml up -d java-search-service
```

**Time:** ~2 minutes (models already downloaded, cached)

---

## Monitoring

### Check Service Status
```bash
docker-compose -f docker-compose.optimized.yml ps
```

### Monitor Resource Usage
```bash
docker stats
```

Expected memory usage:
- postgres: ~200-300MB
- elasticsearch: ~800MB-1.2GB
- java-search-service: ~600MB-1GB
- java-backend: ~300-500MB
- frontend: ~20-50MB
- **Total: ~2-3GB**

### Check Health
```bash
# All services
docker inspect $(docker ps -q) | grep -A 10 Health

# Specific service
docker inspect imagesearch-java-backend | grep -A 10 Health
```

---

## Troubleshooting

### Service Won't Start

**Check logs:**
```bash
docker-compose -f docker-compose.optimized.yml logs [service-name]
```

**Common issues:**
1. Port already in use → Change port in docker-compose.optimized.yml
2. Out of memory → Increase Docker memory limit
3. Permission denied → Check volume permissions

### Build Fails

**Clear everything and rebuild:**
```bash
# Stop all services
docker-compose -f docker-compose.optimized.yml down

# Remove volumes (WARNING: deletes data)
docker-compose -f docker-compose.optimized.yml down -v

# Clear build cache
docker system prune -a

# Rebuild
docker-compose -f docker-compose.optimized.yml build --no-cache
```

### Database Connection Error

**Reset database:**
```bash
# Stop services
docker-compose -f docker-compose.optimized.yml down

# Remove only postgres volume
docker volume rm image-search-app_postgres-data

# Restart (database will reinitialize)
docker-compose -f docker-compose.optimized.yml up -d
```

### Slow Performance

**Check resource limits:**
```bash
docker stats

# If memory is maxed out, increase limits in docker-compose.optimized.yml
```

---

## Cleanup

### Remove Stopped Containers
```bash
docker-compose -f docker-compose.optimized.yml down
```

### Remove Volumes (Deletes Data!)
```bash
docker-compose -f docker-compose.optimized.yml down -v
```

### Remove Images
```bash
docker rmi imagesearch-java-backend:latest
docker rmi imagesearch-java-search:latest
docker rmi imagesearch-frontend:latest
```

### Full Cleanup
```bash
docker-compose -f docker-compose.optimized.yml down -v --rmi all
docker system prune -a
```

---

## Performance Tips

### Faster Rebuilds
1. Only change one service at a time
2. Use `--build [service]` to rebuild specific service
3. Keep Docker Desktop running (daemon warm)

### Reduce Memory Usage
1. Stop unused services:
   ```bash
   docker-compose -f docker-compose.optimized.yml stop elasticsearch
   ```

2. Reduce Elasticsearch heap (in docker-compose.optimized.yml):
   ```yaml
   environment:
     ES_JAVA_OPTS: -Xms256m -Xmx512m
   ```

### Faster Startup
Run warmup script immediately after `docker-compose up -d`

---

## Interview Preparation Checklist

- [ ] Build all services successfully
- [ ] Start services and verify all are healthy
- [ ] Access frontend at http://localhost:3000
- [ ] Test user registration
- [ ] Test image upload
- [ ] Test search functionality
- [ ] Check `docker stats` shows reasonable resource usage
- [ ] Know how to view logs quickly
- [ ] Can explain multi-stage build benefits
- [ ] Can explain resource limit choices
- [ ] Have DOCKER_OPTIMIZATIONS.md ready to reference

---

## Key Files Reference

| File | Purpose |
|------|---------|
| `docker-compose.optimized.yml` | Main orchestration file |
| `Dockerfile.java-backend` | Java backend multi-stage build |
| `Dockerfile.java-search-service` | Search service with ONNX models |
| `frontend/Dockerfile.frontend` | React + Nginx multi-stage build |
| `scripts/warmup.sh` | Service readiness checker |
| `scripts/init-postgres.sql` | PostgreSQL tuning settings |
| `.env` | Environment variables |
| `DOCKER_OPTIMIZATIONS.md` | Detailed optimization documentation |

---

## Service Endpoints

| Service | URL | Purpose |
|---------|-----|---------|
| Frontend | http://localhost:3000 | React UI |
| Java Backend | http://localhost:8080 | REST API |
| Java Search | http://localhost:5001 | CLIP search API |
| Elasticsearch | http://localhost:9200 | Search engine |
| PostgreSQL | localhost:5432 | Database |

---

## Common Commands Cheat Sheet

```bash
# Build
docker-compose -f docker-compose.optimized.yml build

# Start (detached)
docker-compose -f docker-compose.optimized.yml up -d

# Stop
docker-compose -f docker-compose.optimized.yml down

# Logs (follow)
docker-compose -f docker-compose.optimized.yml logs -f [service]

# Restart service
docker-compose -f docker-compose.optimized.yml restart [service]

# Rebuild single service
docker-compose -f docker-compose.optimized.yml build [service]

# Check status
docker-compose -f docker-compose.optimized.yml ps

# Monitor resources
docker stats

# Execute command in container
docker-compose -f docker-compose.optimized.yml exec [service] [command]

# View service health
./scripts/warmup.sh
```

---

## Success Indicators

After running `docker-compose up -d && ./scripts/warmup.sh`, you should see:

✅ All services show "healthy" in `docker ps`
✅ http://localhost:3000 loads React frontend
✅ `docker stats` shows ~2-3GB total memory usage
✅ First search request completes in <2 seconds
✅ No errors in `docker-compose logs`

**If all indicators pass → Ready for interview! 🎉**
