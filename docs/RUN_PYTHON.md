# Quick Start: Run with Python Backend

Follow these steps to run the application with the **Python FastAPI** backend.

## Prerequisites

Make sure you have:
- ✅ Python 3.12+ installed (`python3 --version`)
- ✅ Node.js 18+ installed (`node --version`)
- ✅ PostgreSQL 15+ installed and running

## Step 1: Set Up PostgreSQL

```bash
# Start PostgreSQL
sudo systemctl start postgresql  # Linux
# OR
brew services start postgresql@15  # macOS

# Create database
sudo -u postgres psql
```

In PostgreSQL shell:
```sql
CREATE DATABASE imagesearch;
CREATE USER imageuser WITH ENCRYPTED PASSWORD 'imagepass123';
GRANT ALL PRIVILEGES ON DATABASE imagesearch TO imageuser;
\q
```

## Step 2: Start Python Search Service

Open **Terminal 1**:

```bash
cd search-service

# Create virtual environment
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# Install dependencies (first time only - takes 5-10 minutes)
pip install -r requirements.txt

# Start the service
python app.py
```

✅ **Wait for:**
- `Loading CLIP model...`
- `CLIP model loaded successfully`
- `Uvicorn running on http://0.0.0.0:5000`

## Step 3: Start Python Backend

Open **Terminal 2**:

```bash
cd python-backend

# Create virtual environment
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# Install dependencies (first time only - takes 5-10 minutes)
pip install -r requirements.txt

# Set database credentials
export DB_USERNAME=imageuser
export DB_PASSWORD=imagepass123
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=imagesearch

# Start the backend
uvicorn app:app --host 0.0.0.0 --port 8000
```

✅ **Wait for:**
- `Database connected successfully`
- `Application startup complete`
- `Uvicorn running on http://0.0.0.0:8000`

## Step 4: Start React Frontend

Open **Terminal 3**:

```bash
cd frontend

# Install dependencies (first time only)
npm install

# Start with Python backend
REACT_APP_API_URL=http://localhost:8000 npm start
```

✅ **Browser opens automatically at** [http://localhost:3000](http://localhost:3000)

---

## Verify Everything is Running

```bash
# Check Python backend
curl http://localhost:8000/
# Expected: {"message":"..."}

# Check Python search service
curl http://localhost:5000/
# Expected: {"service":"Image Search Microservice","status":"running"}

# Check frontend
curl http://localhost:3000
# Expected: HTML page
```

---

## Test the Application

1. **Register an account**: Click "Register" → Enter username/password
2. **Upload images**: Click "Upload Images" → Select folder name → Choose 2-3 images
3. **Wait 10-30 seconds**: Embedding generation happens in background
4. **Search**: Type a query like "sunset" or description of your images
5. **Results**: See images ranked by similarity!

---

## Architecture

```
React (3000) → Python Backend (8000) → Python Search Service (5000)
                      ↓
              PostgreSQL (5432)
```

**3 components needed!** (Frontend, Backend, Search Service)

---

## Advantages of Python Backend

- ✅ Python ecosystem integration
- ✅ FastAPI async performance
- ✅ Direct PostgreSQL operations with psycopg2
- ✅ Good for AI/ML-focused development
- ✅ Simpler than Java for data science workflows

---

## Switching to Java Backend

To use the Java backend instead:

```bash
# Terminal 1: Python Search Service (keep running)
cd search-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python app.py

# Terminal 2: Start Java backend
cd java-backend
export DB_USERNAME=imageuser DB_PASSWORD=imagepass123
./gradlew bootRun

# Terminal 3: Start frontend (without REACT_APP_API_URL)
cd frontend
npm start  # Defaults to Java backend on port 8080
```

See [RUN_JAVA.md](RUN_JAVA.md) for full Java backend instructions.

---

## Troubleshooting

### Database Connection Error

```bash
# Check PostgreSQL is running
sudo systemctl status postgresql

# Verify credentials
psql -U imageuser -d imagesearch

# Check environment variables
echo $DB_USERNAME $DB_PASSWORD
```

### Port Already in Use

```bash
# Find and kill process using port 8000
lsof -i :8000
kill -9 <PID>
```

### Module Not Found Error

```bash
# Ensure virtual environment is activated
cd python-backend
source venv/bin/activate

# Reinstall dependencies
pip install -r requirements.txt
```

### Search Not Working

- Wait 10-30 seconds after upload (embeddings processing)
- Check Terminal 1 for Python search service logs
- Verify search service is running on port 5000

---

## Stop the Application

In each terminal:
1. Press `Ctrl+C` to stop the service
2. Terminals will close automatically

---

For more details, see [BACKEND_COMPARISON.md](BACKEND_COMPARISON.md)
