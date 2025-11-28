# Unified Java Stack Docker Container

This repository now contains a single Dockerfile that builds and runs the complete Java-based image search application stack in one container.

## What's Included

The unified container runs all these services together:
- **PostgreSQL Database** (port 5432)
- **Elasticsearch** (port 9200) 
- **Java Search Service** with ONNX CLIP models (port 5001)
- **Java Backend** Spring Boot API (port 8080)
- **React Frontend** served by Nginx (port 3000)

## Quick Start

### Build and Run

```bash
# Build and start the unified container
docker-compose up --build

# Or build and run manually
docker build -t imagesearch-java-stack .
docker run -p 3000:3000 -p 8080:8080 -p 5001:5001 imagesearch-java-stack
```

### Access the Application

- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **Search Service**: http://localhost:5001/actuator/health
- **Database**: localhost:5432 (user: imageuser, password: changeme123)
- **Elasticsearch**: http://localhost:9200

### Using the App

1. Open http://localhost:3000 in your browser
2. Register a new user account
3. Create folders and upload images
4. Search images using natural language queries
5. CLIP embeddings and vector search happen automatically

## Architecture

```
┌─────────────┐    ┌──────────────┐    ┌─────────────────┐
│   Nginx     │───▶│ Java Backend │───▶│ Java Search     │
│ (Frontend)  │    │ (Spring Boot)│    │ Service (ONNX)  │
│   :3000     │    │    :8080     │    │     :5001       │
└─────────────┘    └──────┬───────┘    └─────────┬───────┘
                          │                      │
                    ┌─────▼──────┐         ┌─────▼──────┐
                    │PostgreSQL  │         │Elasticsearch│
                    │   :5432    │         │    :9200   │ 
                    └────────────┘         └────────────┘
```

## Data Persistence

Data is persisted in Docker volumes:
- `app-data`: Uploaded images and search indexes
- `postgres-data`: Database files
- `elasticsearch-data`: Search index data

## Container Management

```bash
# View logs from all services
docker-compose logs -f

# View logs from specific service
docker exec -it imagesearch-java-unified tail -f /var/log/supervisor/java-backend.log

# Stop container
docker-compose down

# Remove all data (reset)
docker-compose down -v
docker system prune -a
```

## Development

If you need to modify the code:

1. Make changes to source files
2. Rebuild: `docker-compose up --build`
3. The container will compile and deploy changes automatically

## Troubleshooting

### Container won't start
- Check Docker has enough memory (recommended: 4GB+)
- Wait for full startup (can take 2-3 minutes first time)

### Services not responding
- Check health: `docker-compose ps`
- View service logs: `docker-compose logs [service-name]`
- Check all ports are available on host

### Upload/Search issues  
- Verify all services started: `docker exec -it imagesearch-java-unified supervisorctl status`
- Check Elasticsearch: `curl http://localhost:9200/_cluster/health`
- Check database: `docker exec -it imagesearch-java-unified psql -U imageuser -d imagesearch`

## Notes

- First startup downloads CLIP models (~200MB)
- CLIP model inference runs on CPU (no GPU required)
- All services run as supervised processes within single container
- Image embeddings are computed using ONNX Runtime + Elasticsearch for search