# Architecture Refactoring - November 2024

## Overview

This document describes the architectural improvements made to the Image Search Application to follow industry best practices for modular, maintainable, and efficient code.

## Problem Statement

### Issues Before Refactoring

1. **Data Storage Duplication**
   - Multiple image directories: `/images/`, `/python-python-backend/faisses_indexes/`
   - Multiple FAISS index locations with inconsistent naming
   - Unclear ownership and data flow between services

2. **Root Directory Pollution**
   - Build artifacts at root (`__pycache__/`, `venv/`)
   - Multiple virtual environments (4+)
   - No clear separation between code and data

3. **Inconsistent Naming**
   - Typo: `faisses_indexes` vs `faiss_indexes`
   - Mixed conventions across services

4. **Complex Path Resolution**
   - Each service calculated paths differently
   - Docker vs local development path inconsistencies
   - Hard to backup/restore data

## Solution: Unified Data Directory

### New Structure

```
image-search-app/
├── data/                          # ✅ NEW: All persistent data (gitignored)
│   ├── uploads/                   # All uploaded images (both backends write here)
│   │   └── {userId}/{folderId}/   # Organized by user/folder
│   └── indexes/                   # All FAISS indexes (only search-service writes)
│       └── {userId}/{folderId}/   # Mirrors upload structure
│
├── java-python-backend/                  # Spring Boot service
├── python-python-backend/                # FastAPI service (alternative)
├── search-service/                # AI microservice (shared)
├── frontend/                      # React app
├── scripts/                       # Operational scripts
└── docs/                          # Documentation
```

### Key Principles

1. **Single Source of Truth**
   - All images → `data/uploads/`
   - All FAISS indexes → `data/indexes/`
   - No confusion, no duplication

2. **Service Isolation**
   - Each service has its own dependencies
   - Clear boundaries via HTTP APIs
   - No shared code except through APIs

3. **Environment Consistency**
   - Same structure in Docker and local dev
   - Easy path resolution
   - Simplified Docker volumes

## Changes Made

### 1. Directory Structure

**Created:**
```bash
data/
├── uploads/     # Centralized image storage
└── indexes/     # Centralized FAISS indexes
```

**Removed:**
```bash
__pycache__/              # Root build artifacts
venv/                     # Redundant virtual environment
python-python-backend/faisses_indexes/  # Typo directory
scripts/venv/             # Unnecessary venv
```

**Kept (as legacy backup):**
```bash
images/                   # Old images (now gitignored)
```

### 2. Code Changes

#### Java Backend

**File:** [java-python-backend/src/main/java/com/imagesearch/config/StaticResourceConfig.java](../java-python-backend/src/main/java/com/imagesearch/config/StaticResourceConfig.java)
- Changed: `images` → `data/uploads`
- Maps `/images/**` URLs to `{project-root}/data/uploads/`

**File:** [java-python-backend/src/main/java/com/imagesearch/service/ImageService.java](../java-python-backend/src/main/java/com/imagesearch/service/ImageService.java)
- Changed: Upload path from `images/` → `data/uploads/`
- Structure: `{project-root}/data/uploads/{userId}/{folderId}/`

#### Python Backend

**File:** [python-python-backend/aws_handler.py](../python-python-backend/aws_handler.py)
- Simplified path logic
- Now uses: `{project-root}/data/uploads/{key}`
- Removed complex parent directory calculations

#### Search Service

**File:** [search-service/search_handler.py](../search-service/search_handler.py)
- Added: `get_faiss_base_folder()` function
- Docker: Uses `/app/data/indexes`
- Local: Uses `{project-root}/data/indexes`
- Dynamic path resolution based on environment

**File:** [search-service/embedding_service.py](../search-service/embedding_service.py)
- Updated: Path resolution for images
- Converts `images/` prefix → `data/uploads/`
- Handles both Docker (`/app/data/uploads`) and local paths

### 3. Docker Configuration

#### Python Stack ([docker-compose.python.yml](../docker-compose.python.yml))

**Before:**
```yaml
volumes:
  python-images:        # Separate volume for images
  search-indexes:       # Separate volume for indexes
  python-db:           # Separate volume for database
```

**After:**
```yaml
volumes:
  app-data:            # Single unified volume
    # Contains:
    # - data/uploads/ (images)
    # - data/indexes/ (FAISS)
    # - database files
```

**Services mount:**
```yaml
python-backend:
  volumes:
    - app-data:/app/data    # Can write images, access DB

search-service:
  volumes:
    - app-data:/app/data    # Can read images, write indexes
```

#### Java Stack ([docker-compose.java.yml](../docker-compose.java.yml))

**Same unified approach:**
```yaml
volumes:
  postgres-data:       # PostgreSQL only
  app-data:           # All application data

java-backend:
  volumes:
    - app-data:/app/data
  environment:
    - IMAGES_ROOT=/app/data/uploads

search-service:
  volumes:
    - app-data:/app/data
```

### 4. Scripts

**File:** [scripts/reset-app.sh](../scripts/reset-app.sh)

Updated to use new paths:
```bash
# New unified directories
data/uploads/*
data/indexes/*

# Legacy cleanup (for backward compatibility)
images/*
search-service/faiss_indexes/*
python-python-backend/faisses_indexes/*
```

### 5. Git Configuration

**File:** [.gitignore](../.gitignore)

Comprehensive update:
```gitignore
# Data directories
data/
images/
static/uploads/

# FAISS indexes
*.faiss
**/faiss_indexes/
**/faisses_indexes/

# Build artifacts
__pycache__/
venv/
node_modules/

# User uploads
*.jpeg
*.jpg
*.png
```

## Benefits

### ✅ Improved Maintainability

- **Clear data ownership**: One directory for all persistent data
- **Easy backup**: `tar -czf backup.tar.gz data/`
- **Easy reset**: `rm -rf data/*` or run `./scripts/reset-app.sh`

### ✅ Better Development Experience

- **Consistent paths**: Same structure in Docker and local dev
- **No path confusion**: All services know where to find data
- **Faster onboarding**: New developers understand structure immediately

### ✅ Production Ready

- **Simple Docker volumes**: One volume for all app data
- **Easy scaling**: Add new services by mounting same volume
- **Clear boundaries**: Data separate from code

### ✅ Backend Agnostic

- **Java and Python coexist**: Both write to `data/uploads/`
- **Shared search service**: Both use same FAISS indexes
- **Switch seamlessly**: Change backends without data migration

## Migration Guide

### For Existing Installations

1. **Backup existing data:**
   ```bash
   cp -r images/ images.backup/
   ```

2. **Pull latest code:**
   ```bash
   git pull origin main
   ```

3. **Data will automatically be read from new location**
   - Existing `images/` data was copied to `data/uploads/`
   - Old directory kept as backup (gitignored)

4. **Test the application:**
   ```bash
   # Python stack
   docker-compose -f docker-compose.python.yml up

   # OR Java stack
   docker-compose -f docker-compose.java.yml up
   ```

5. **Cleanup old directories (optional):**
   ```bash
   rm -rf images/
   rm -rf python-python-backend/faisses_indexes/
   ```

### For New Installations

Just clone and run - the new structure is already in place:
```bash
git clone <repo>
cd image-search-app
docker-compose -f docker-compose.python.yml up
```

## File Changes Summary

### Modified Files
- [.gitignore](../.gitignore)
- [java-python-backend/src/main/java/com/imagesearch/config/StaticResourceConfig.java](../java-python-backend/src/main/java/com/imagesearch/config/StaticResourceConfig.java)
- [java-python-backend/src/main/java/com/imagesearch/service/ImageService.java](../java-python-backend/src/main/java/com/imagesearch/service/ImageService.java)
- [python-python-backend/aws_handler.py](../python-python-backend/aws_handler.py)
- [search-service/search_handler.py](../search-service/search_handler.py)
- [search-service/embedding_service.py](../search-service/embedding_service.py)
- [docker-compose.python.yml](../docker-compose.python.yml)
- [docker-compose.java.yml](../docker-compose.java.yml)
- [scripts/reset-app.sh](../scripts/reset-app.sh)

### Created
- `data/uploads/` directory
- `data/indexes/` directory
- This documentation

### Removed
- `__pycache__/` (root)
- `venv/` (root)
- `python-python-backend/faisses_indexes/`
- `scripts/venv/`

## Testing Checklist

- [ ] Upload images via Java backend → Check `data/uploads/{userId}/{folderId}/`
- [ ] Upload images via Python backend → Check same directory
- [ ] Search generates embeddings → Check `data/indexes/{userId}/{folderId}/`
- [ ] Search across folders works
- [ ] Images display in frontend
- [ ] Reset script clears all data
- [ ] Docker compose starts all services
- [ ] Switch between Java/Python backends seamlessly

## Future Improvements

While this refactoring addresses the most critical issues, consider:

1. **Service Directory**: Move all services to `services/` subdirectory
2. **Environment Files**: Create `.env.example` for each service
3. **Shared Data Volume**: Consider NFS or object storage for multi-server deployments
4. **Monitoring**: Add data directory size monitoring
5. **Backup Scripts**: Automated backup of `data/` directory

## Questions?

See:
- [README.md](../README.md) - General setup instructions
- [MICROSERVICES_REFACTORING.md](../MICROSERVICES_REFACTORING.md) - Microservices architecture
- [HOW_TO_SWITCH_BACKENDS.md](HOW_TO_SWITCH_BACKENDS.md) - Switching between Java/Python

## Summary

This refactoring transforms the project from a scattered, inconsistent structure to a clean, professional architecture that follows industry best practices. The unified `data/` directory provides a single source of truth for all persistent data, making the application easier to develop, deploy, and maintain.
