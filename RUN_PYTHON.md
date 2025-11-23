# Quick Start: Run with Python Backend

Follow these steps to run the application with the **Python FastAPI monolithic** backend.

## Prerequisites

Make sure you have:
- ✅ Python 3.12+ installed (`python3 --version`)
- ✅ Node.js 18+ installed (`node --version`)

## Step 1: Start Python Backend

Open **Terminal 1**:

```bash
cd backend

# Create virtual environment
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# Install dependencies (first time only - takes 5-10 minutes)
pip install -r ../requirements.txt

# Start the backend
uvicorn api:app --reload --port 8000
```

✅ **Wait for:**
- `Loading CLIP model...`
- `Application startup complete`
- `Uvicorn running on http://127.0.0.1:8000`

## Step 2: Start React Frontend

Open **Terminal 2**:

```bash
cd frontend

# Install dependencies (first time only)
npm install

# Start with Python backend
REACT_APP_BACKEND=python npm start
```

✅ **Browser opens automatically at** http://localhost:3000

---

## Verify Everything is Running

```bash
# Check Python backend
curl http://localhost:8000/
# Expected: {"message":"Welcome to Image Search API (Python FastAPI)"}

# Check frontend
curl http://localhost:3000
# Expected: HTML page
```

---

## Test the Application

1. **Register an account**: Click "Register" → Enter username/password
2. **Upload images**: Click "Upload Images" → Select folder name → Choose 2-3 images
3. **Wait 10-30 seconds**: CLIP embedding generation happens in background
4. **Search**: Type a query like "sunset" or description of your images
5. **Results**: See images ranked by similarity!

---

## Architecture

```
React (3000) → Python Backend (8000)
                      ↓
              SQLite + CLIP + FAISS
```

**Only 2 components needed!**

---

## Advantages of Python Backend

- ✅ Simpler setup (no database installation)
- ✅ Faster to start (2 services instead of 4)
- ✅ Lower resource usage
- ✅ All-in-one (no microservices complexity)
- ✅ Good for development and testing

---

## Switching to Java Backend

To use the Java backend instead:

```bash
# Terminal 1: Keep Python backend running (for search microservice)
cd search-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python app.py

# Terminal 2: Start Java backend
cd java-backend
export DB_USERNAME=imageuser DB_PASSWORD=imagepass123
./gradlew bootRun

# Terminal 3: Start frontend (without REACT_APP_BACKEND)
cd frontend
npm start  # Defaults to Java backend
```

See [RUN_JAVA.md](RUN_JAVA.md) for full Java backend instructions.

---

## Stop the Application

In each terminal:
1. Press `Ctrl+C` to stop the service
2. Terminals will close automatically

---

For more details, see [BACKEND_COMPARISON.md](BACKEND_COMPARISON.md)
