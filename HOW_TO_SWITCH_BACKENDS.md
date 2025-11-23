# How to Switch Between Java and Python Backends

## Quick Start Guide

### Option 1: Java Backend + Search Microservice (Recommended for Interview)

**Architecture:**
```
React (3000) → Java Backend (8080) → Python Search Service (5000)
```

**Run these commands in 3 separate terminals:**

```bash
# Terminal 1: Python Search Microservice
./run-search-service.sh

# Terminal 2: Java Backend
./run-java.sh

# Terminal 3: React Frontend (Java mode)
./run-frontend-java.sh
```

**Or manually:**
```bash
# Terminal 1
cd search-service
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python app.py

# Terminal 2
./run-java.sh

# Terminal 3
cd frontend
REACT_APP_BACKEND=java npm start
```

---

### Option 2: Python Backend Only (Monolithic)

**Architecture:**
```
React (3000) → Python Backend (8000)
```

**Run these commands in 2 separate terminals:**

```bash
# Terminal 1: Python Backend
./run-python-backend.sh

# Terminal 2: React Frontend (Python mode)
./run-frontend-python.sh
```

**Or manually:**
```bash
# Terminal 1
cd python-backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python -m uvicorn api:app --reload --port 8000

# Terminal 2
cd frontend
REACT_APP_BACKEND=python npm start
```

---

## Port Configuration Summary

| Service | Port | Used By |
|---------|------|---------|
| Java Backend | 8080 | Option 1 |
| Python Backend | 8000 | Option 2 |
| Search Service | 5000 | Option 1 (called by Java) |
| React Frontend | 3000 | Both options |

---

## How the Frontend Switches Backends

The frontend uses the **`REACT_APP_BACKEND`** environment variable to determine which backend to connect to.

### Configuration in `frontend/src/utils/api.js`:

```javascript
const BACKEND = process.env.REACT_APP_BACKEND || 'java';

const API_BASE_URL = BACKEND === 'python'
  ? 'http://localhost:8000'   // Python FastAPI backend
  : 'http://localhost:8080';  // Java Spring Boot backend (default)
```

### To use Java backend (default):
```bash
cd frontend
npm start
# OR
REACT_APP_BACKEND=java npm start
# OR
./run-frontend-java.sh
```

### To use Python backend:
```bash
cd frontend
REACT_APP_BACKEND=python npm start
# OR
./run-frontend-python.sh
```

---

## Troubleshooting

### Error: "Failed to fetch" or CORS errors

**Problem:** Backend is not running or wrong port

**Solution:**
1. Check which backend you're trying to use
2. Make sure it's running on the correct port
3. Check browser console for the exact error

```bash
# Check if Java backend is running
curl http://localhost:8080/api
# Should return: {"message":"Welcome to Image Search API"}

# Check if Python backend is running
curl http://localhost:8000/api
# Should return: {"message":"Welcome to Image Search API (Python FastAPI)"}

# Check if search service is running
curl http://localhost:5000/
# Should return: {"message":"Image Search Microservice"}
```

### Error: "Proxy error: Could not proxy request"

**Problem:** Old proxy configuration in package.json

**Solution:** Already fixed! The proxy has been removed from `frontend/package.json`

### Error: Port already in use

**Problem:** A service is already running on that port

**Solution:**
```bash
# Check what's using the port
lsof -i :8080
lsof -i :8000
lsof -i :5000

# Kill the process
kill -9 <PID>
```

### Python backend: ModuleNotFoundError

**Problem:** Virtual environment not activated or dependencies not installed

**Solution:**
```bash
cd python-backend  # or search-service
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### Java backend: Database connection error

**Problem:** PostgreSQL not set up

**Solution:**
```bash
./setup-postgres.sh
```

---

## Complete Startup Checklist

### For Java Backend Setup:

- [ ] PostgreSQL is installed and running
- [ ] Database is created: `./setup-postgres.sh`
- [ ] Search service is running: `./run-search-service.sh`
- [ ] Java backend is running: `./run-java.sh`
- [ ] Frontend is in Java mode: `./run-frontend-java.sh`
- [ ] Browser console shows: `🔌 Using JAVA backend at http://localhost:8080`

### For Python Backend Setup:

- [ ] Python backend is running: `./run-python-backend.sh`
- [ ] Frontend is in Python mode: `./run-frontend-python.sh`
- [ ] Browser console shows: `🔌 Using PYTHON backend at http://localhost:8000`

---

## Verify Which Backend You're Using

Open the browser console (F12) when the React app loads. You should see:

**Java mode:**
```
🔌 Using JAVA backend at http://localhost:8080
```

**Python mode:**
```
🔌 Using PYTHON backend at http://localhost:8000
```

---

## API Endpoints (Same for Both Backends)

Both backends use the **exact same RESTful API endpoints**:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/users/register` | POST | Register new user |
| `/api/users/login` | POST | Login user |
| `/api/users/logout` | POST | Logout user |
| `/api/users/account` | DELETE | Delete account |
| `/api/images/upload` | POST | Upload images |
| `/api/images/search` | GET | Search images |
| `/api/folders` | GET | Get user folders |
| `/api/folders` | DELETE | Delete folders |
| `/api/folders/share` | POST | Share folder |
| `/api/folders/shared` | GET | Get shared folders |

---

## Key Differences Between Backends

| Feature | Java Backend | Python Backend |
|---------|--------------|----------------|
| Framework | Spring Boot 3.2 | FastAPI 0.104 |
| Port | 8080 | 8000 |
| Database | PostgreSQL | SQLite |
| Search | Calls microservice (port 5000) | Built-in FAISS |
| Architecture | Microservices | Monolithic |
| Best for | Interviews, Production | Quick dev, Testing |

---

## Summary

### To Switch from Java to Python:

1. **Stop** Java backend (Ctrl+C)
2. **Stop** Search service (Ctrl+C)
3. **Stop** React frontend (Ctrl+C)
4. **Start** Python backend: `./run-python-backend.sh`
5. **Start** React in Python mode: `./run-frontend-python.sh`

### To Switch from Python to Java:

1. **Stop** Python backend (Ctrl+C)
2. **Stop** React frontend (Ctrl+C)
3. **Start** Search service: `./run-search-service.sh`
4. **Start** Java backend: `./run-java.sh`
5. **Start** React in Java mode: `./run-frontend-java.sh`

**That's it! No code changes needed - just different startup commands!** 🚀
