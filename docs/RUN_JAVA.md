# Quick Start: Run with Java Backend

Follow these steps to run the application with the **Java Spring Boot + Python Microservices** architecture.

## Prerequisites

Make sure you have:
- ✅ Java 17+ installed (`java -version`)
- ✅ PostgreSQL installed and running
- ✅ Python 3.12+ installed (`python3 --version`)
- ✅ Node.js 18+ installed (`node --version`)

## Step 1: Set Up PostgreSQL Database

```bash
# Start PostgreSQL
sudo systemctl start postgresql  # Linux
# OR
brew services start postgresql@15  # macOS

# Create database and user
sudo -u postgres psql
```

In PostgreSQL shell:
```sql
CREATE DATABASE imagesearch;
CREATE USER imageuser WITH ENCRYPTED PASSWORD 'imagepass123';
GRANT ALL PRIVILEGES ON DATABASE imagesearch TO imageuser;
\q
```

## Step 2: Start Python Search Microservice

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

✅ **Wait for:** `Uvicorn running on http://0.0.0.0:5000`

## Step 3: Start Java Backend

Open **Terminal 2**:

```bash
cd java-backend

# Set database credentials
export DB_USERNAME=imageuser
export DB_PASSWORD=imagepass123

# Run with Gradle
./gradlew bootRun
```

✅ **Wait for:**
- `Hibernate: create table users...` (creates database tables)
- `Started ImageSearchApplication in X seconds`

## Step 4: Start React Frontend

Open **Terminal 3**:

```bash
cd frontend

# Install dependencies (first time only)
npm install

# Start development server
npm start
```

✅ **Browser opens automatically at** http://localhost:3000

---

## Verify Everything is Running

```bash
# Check Python microservice
curl http://localhost:5000/
# Expected: {"service":"Image Search Microservice","status":"running"}

# Check Java backend
curl http://localhost:8080/
# Expected: {"message":"..."}

# Check frontend
curl http://localhost:3000
# Expected: HTML page
```

---

## Test the Application

1. **Register an account**: Click "Register" → Enter username/password
2. **Upload images**: Click "Upload Images" → Select folder name → Choose 2-3 images
3. **Search**: Type a query like "sunset" or description of your images
4. **Results**: See images ranked by similarity!

---

## Troubleshooting

### PostgreSQL Connection Error

```bash
# Check PostgreSQL is running
sudo systemctl status postgresql

# Verify credentials
psql -U imageuser -d imagesearch
```

### Port Already in Use

```bash
# Find and kill process using port 8080
lsof -i :8080
kill -9 <PID>

# Or use different port in application.yml
server:
  port: 8081
```

### Search Not Working

- Wait 10-30 seconds after upload (embeddings processing in background)
- Check Terminal 1 for Python microservice logs
- Verify Python service is running on port 5000

---

## Stop the Application

In each terminal:
1. Press `Ctrl+C` to stop the service
2. Terminals will close automatically

---

## Architecture

```
React (3000) → Java Backend (8080) → Python Search (5000)
                      ↓
                PostgreSQL (5432)
```

**All 4 components must be running!**

---

For more details, see [SETUP.md](SETUP.md)
