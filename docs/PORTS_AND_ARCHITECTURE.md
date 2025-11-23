# Ports and Architecture Guide

## Port Configuration

Your application uses **3 different ports** for different services:

| Service | Port | Purpose | Database |
|---------|------|---------|----------|
| **Java Backend** | 8080 | Main backend (Spring Boot) | PostgreSQL |
| **Python Backend** | 8000 | Alternative backend (FastAPI) | SQLite |
| **Python Search Service** | 5000 | AI microservice (CLIP + FAISS) | None |
| **React Frontend** | 3000 | User interface | None |

---

## Architecture Overview

### Option 1: Java Backend + Python Search Microservice (Recommended for Interview)

```
┌─────────────────┐
│  React Frontend │  Port 3000
│  (localhost)    │
└────────┬────────┘
         │ HTTP Requests
         ↓
┌─────────────────┐
│  Java Backend   │  Port 8080
│  Spring Boot    │  PostgreSQL Database
└────────┬────────┘
         │ HTTP Requests
         ↓
┌─────────────────┐
│ Python Search   │  Port 5000
│ Service         │  CLIP + FAISS
│ (Microservice)  │
└─────────────────┘
```

**How to run:**
```bash
# Terminal 1: Start Python Search Service
cd search-service
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python app.py

# Terminal 2: Start Java Backend
cd java-backend
../scripts/run-java.sh

# Terminal 3: Start React Frontend
cd frontend
REACT_APP_BACKEND=java npm start
```

---

### Option 2: Python Backend (Monolithic - No Microservices)

```
┌─────────────────┐
│  React Frontend │  Port 3000
│  (localhost)    │
└────────┬────────┘
         │ HTTP Requests
         ↓
┌─────────────────┐
│ Python Backend  │  Port 8000
│  FastAPI        │  SQLite Database
│  (Monolithic)   │  CLIP + FAISS Built-in
└─────────────────┘
```

**How to run:**
```bash
# Terminal 1: Start Python Backend
cd python-backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python -m uvicorn api:app --reload --port 8000

# Terminal 2: Start React Frontend
cd frontend
REACT_APP_BACKEND=python npm start
```

---

## Detailed Port Usage

### Java Backend (Port 8080)

**Configuration file:** `java-backend/src/main/resources/application.yml`

```yaml
server:
  port: 8080

search-service:
  base-url: http://localhost:5000  # Python search microservice
```

**Started by:**
- `./scripts/run-java.sh`
- OR `cd java-backend && ./gradlew bootRun`

**Endpoints:**
- `http://localhost:8080/api/users/register`
- `http://localhost:8080/api/users/login`
- `http://localhost:8080/api/images/upload`
- `http://localhost:8080/api/images/search`
- `http://localhost:8080/api/folders`
- `http://localhost:8080/images/1/4/photo.jpg` (static image files)

**Database:** PostgreSQL on port 5432

---

### Python Backend (Port 8000)

**Configuration:** Docker/manual uvicorn

```bash
uvicorn api:app --reload --port 8000
```

**Started by:**
- `cd python-backend && python -m uvicorn api:app --reload --port 8000`

**Endpoints:**
- `http://localhost:8000/api/users/register`
- `http://localhost:8000/api/users/login`
- `http://localhost:8000/api/images/upload`
- `http://localhost:8000/api/images/search`
- `http://localhost:8000/api/folders`
- `http://localhost:8000/images/1/4/photo.jpg` (static image files)

**Database:** PostgreSQL on port 5432

---

### Python Search Service (Port 5000)

**Configuration:** `search-service/app.py`

```python
uvicorn.run(app, host="0.0.0.0", port=5000)
```

**Started by:**
- `cd search-service && python app.py`

**Endpoints (called by Java backend):**
- `http://localhost:5000/search` - Perform semantic image search
- `http://localhost:5000/embed-images` - Generate CLIP embeddings
- `http://localhost:5000/create-index` - Create new FAISS index
- `http://localhost:5000/delete-index` - Delete FAISS index

**Database:** None (stores FAISS indexes in `faiss_indexes/` directory)

---

### React Frontend (Port 3000)

**Configuration:** `frontend/src/utils/api.js`

```javascript
const BACKEND = process.env.REACT_APP_BACKEND || 'java';

const API_BASE_URL = BACKEND === 'python'
  ? 'http://localhost:8000'   // Python backend
  : 'http://localhost:8080';  // Java backend (default)
```

**Started by:**
- `cd frontend && npm start`

**How to switch backends:**
```bash
# Use Java backend (default)
npm start

# Use Python backend
REACT_APP_BACKEND=python npm start
```

---

## Files That Reference Backend Ports

### Frontend Files

1. **`frontend/src/utils/api.js`** (Lines 25-36)
   - Switches between Java (8080) and Python (8000) backends
   - Controlled by `REACT_APP_BACKEND` environment variable

### Java Backend Files

2. **`java-backend/src/main/resources/application.yml`** (Lines 25-33)
   - Java backend listens on port **8080**
   - Calls Python search service on port **5000**

3. **`java-backend/src/main/java/com/imagesearch/client/PythonSearchClient.java`**
   - Uses `search-service.base-url` from application.yml
   - Defaults to `http://localhost:5000`

### Python Backend Files

4. **`backend/Dockerfile`** (Line 40)
   - Sets port **9999** for Docker (not used in local dev)
   - For local dev, run with `--port 8000`

### Python Search Service Files

5. **`search-service/app.py`** (Line 223)
   - Listens on port **5000**
   - `uvicorn.run(app, host="0.0.0.0", port=5000)`

---

## Quick Start Commands

### Interview Setup (Java + Microservices)

```bash
# Setup PostgreSQL (one time only)
./scripts/setup-postgres.sh

# Terminal 1: Python Search Service
cd search-service && python app.py

# Terminal 2: Java Backend
./scripts/run-java.sh

# Terminal 3: React Frontend
cd frontend && npm start
```

Open browser: `http://localhost:3000`

### Development Setup (Python Monolithic)

```bash
# Terminal 1: Python Backend
cd python-backend
python -m uvicorn api:app --reload --port 8000

# Terminal 2: React Frontend
cd frontend
REACT_APP_BACKEND=python npm start
```

Open browser: `http://localhost:3000`

---

## Port Conflicts

If you get "port already in use" errors:

```bash
# Check what's using a port
lsof -i :8080   # Java backend
lsof -i :8000   # Python backend
lsof -i :5000   # Search service
lsof -i :3000   # React frontend

# Kill process using a port
kill -9 <PID>

# Or use a different port
./gradlew bootRun --args='--server.port=8081'  # Java on different port
```

---

## Summary

✅ **Java backend (8080)** and **Python backend (8000)** run on **different ports**
✅ **Python search service (5000)** is a separate microservice
✅ **React frontend (3000)** can switch between Java and Python backends
✅ All backends use the **same RESTful API endpoints** (`/api/users/*`, `/api/folders/*`, `/api/images/*`)
✅ No port conflicts - everything runs simultaneously if needed

**For your interview:** Run Java backend + Python microservice to demonstrate full-stack and microservices architecture!
