# Docker Quick Start Guide

This guide explains how to easily switch between Java and Python backends using Docker.

## Architecture Overview

The application is split into three Docker Compose files:

1. **`docker-compose.shared.yml`** - Shared services (PostgreSQL + Search Service)
2. **`docker-compose.java.yml`** - Java backend + Frontend
3. **`docker-compose.python.yml`** - Python backend + Frontend

## Quick Start

### 1. Start Shared Services (Once)

Start the database and search service that both backends use:

```bash
docker-compose -f docker-compose.shared.yml up -d
```

This starts:
- PostgreSQL database (port 5432)
- Search service with CLIP + FAISS (port 5000)

**Leave these running!** They are shared by both backends.

### 2. Start Java Backend

```bash
docker-compose -f docker-compose.java.yml up -d
```

This starts:
- Java Spring Boot backend (port 8080)
- React frontend (port 3000)

Access the app at: http://localhost:3000

### 3. Switch to Python Backend

Stop the Java backend first:

```bash
docker-compose -f docker-compose.java.yml down
```

Then start the Python backend:

```bash
docker-compose -f docker-compose.python.yml up -d
```

This starts:
- Python FastAPI backend (port 8000)
- React frontend (port 3000)

Access the app at: http://localhost:3000

### 4. Switch Back to Java Backend

Stop the Python backend:

```bash
docker-compose -f docker-compose.python.yml down
```

Start the Java backend:

```bash
docker-compose -f docker-compose.java.yml up -d
```

## Complete Shutdown

To stop everything including shared services:

```bash
# Stop backend (Java or Python)
docker-compose -f docker-compose.java.yml down
# OR
docker-compose -f docker-compose.python.yml down

# Stop shared services
docker-compose -f docker-compose.shared.yml down
```

## Port Summary

| Service | Port | Shared? |
|---------|------|---------|
| PostgreSQL | 5432 | ✅ Yes |
| Search Service | 5000 | ✅ Yes |
| Java Backend | 8080 | No |
| Python Backend | 8000 | No |
| Frontend | 3000 | No |

## Data Persistence

All data is stored in Docker volumes that persist between runs:
- **postgres-data**: Database tables and data
- **app-data**: Uploaded images and FAISS indexes

Even when you stop containers, your data is safe!

## Useful Commands

### View Logs

```bash
# Shared services
docker-compose -f docker-compose.shared.yml logs -f

# Java backend
docker-compose -f docker-compose.java.yml logs -f

# Python backend
docker-compose -f docker-compose.python.yml logs -f
```

### Check Status

```bash
docker ps
```

### Rebuild After Code Changes

```bash
# Rebuild Java backend
docker-compose -f docker-compose.java.yml up -d --build

# Rebuild Python backend
docker-compose -f docker-compose.python.yml up -d --build

# Rebuild search service
docker-compose -f docker-compose.shared.yml up -d --build search-service
```

### Clean Everything (⚠️ Deletes all data!)

```bash
docker-compose -f docker-compose.java.yml down
docker-compose -f docker-compose.python.yml down
docker-compose -f docker-compose.shared.yml down -v
```

The `-v` flag removes volumes, which **deletes all database data and uploaded images**.

## Troubleshooting

### Port Already in Use

If you see "port is already allocated":
- Make sure you stopped the other backend with `docker-compose down`
- Check with `docker ps` to see what's running
- Or use `lsof -i :8080` (or 8000, 3000) to find what's using the port

### Network Not Found

If you see "network not found":
```bash
docker network create imagesearch-network
```

### Volume Not Found

If you see "volume not found":
```bash
docker volume create image-search-app_app-data
docker volume create image-search-app_postgres-data
```

### Backend Can't Connect to Database

Make sure shared services are running:
```bash
docker-compose -f docker-compose.shared.yml ps
```

Both postgres and search-service should show as "Up (healthy)".

## Tips

1. **Keep shared services running** between backend switches for faster switching
2. **Use `docker-compose logs -f <service>`** to debug issues
3. **Both backends share the same database**, so you can switch backends without losing data!
4. **Frontend is rebuilt for each backend** to use the correct API URL
