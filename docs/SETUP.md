# Complete Setup Guide

This guide will help you run the Image Search application with the new **microservices architecture**.

## Architecture Overview

```
React (port 3000) → Java Backend (port 8080) → Python Search Service (port 5000)
                           ↓
                    PostgreSQL (port 5432)
```

## Prerequisites

Install these before starting:

- ✅ **Java 17+** - [Download OpenJDK](https://adoptium.net/)
- ✅ **PostgreSQL 15+** - See [POSTGRESQL_SETUP.md](POSTGRESQL_SETUP.md)
- ✅ **Python 3.12+** - [Download Python](https://www.python.org/downloads/)
- ✅ **Node.js 18+** - [Download Node](https://nodejs.org/)
- ✅ **Git** - For cloning the repository

## Step-by-Step Setup

### Step 1: Clone the Repository

```bash
git clone https://github.com/yourusername/image-search-app.git
cd image-search-app
```

### Step 2: Set Up PostgreSQL

```bash
# Install PostgreSQL
sudo apt install postgresql postgresql-contrib  # Ubuntu/Debian
# OR
brew install postgresql@15  # macOS

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

### Step 3: Start Python Search Microservice

```bash
# Terminal 1
cd search-service

# Create virtual environment
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# Install dependencies (takes 5-10 minutes first time - downloads PyTorch, CLIP)
pip install -r requirements.txt

# Start the service
python app.py
```

✅ **Verify**: You should see `Uvicorn running on http://0.0.0.0:5000`

### Step 4: Start Java Backend

```bash
# Terminal 2
cd java-backend

# Option A: Configure database in application.yml
# Edit src/main/resources/application.yml
# Set:
#   spring.datasource.username: imageuser
#   spring.datasource.password: imagepass123

# Option B: Use environment variables (recommended)
export DB_USERNAME=imageuser
export DB_PASSWORD=imagepass123

# Download Gradle wrapper (first time only)
./gradlew wrapper --gradle-version 8.5

# Run the backend
./gradlew bootRun
```

✅ **Verify**: You should see:
- `Started ImageSearchApplication in X seconds`
- `Tomcat started on port(s): 8080`
- Hibernate creating database tables

### Step 5: Start React Frontend

```bash
# Terminal 3
cd frontend

# Install dependencies (first time only)
npm install

# Start development server
npm start
```

✅ **Verify**: Browser opens automatically at http://localhost:3000

## Testing the Application

### 1. Create an Account
- Click "Register"
- Username: `testuser`
- Password: `password123`

### 2. Create a Folder
- Click "Upload Images"
- Enter folder name: `vacation`
- Select 2-3 images
- Click Upload

### 3. Search Images
- In search box, type: `sunset` (or description of your images)
- Results appear with similarity scores

### 4. Share a Folder
- Go to "My Folders"
- Click Share icon
- Enter another username
- Choose permission (view/edit)

## Troubleshooting

### Java Backend Won't Start

**Error**: `Cannot create PoolableConnectionFactory`
**Solution**: PostgreSQL not running or wrong credentials
```bash
# Check PostgreSQL is running
sudo systemctl status postgresql

# Verify connection
psql -U imageuser -d imagesearch
```

**Error**: `Port 8080 already in use`
**Solution**: Kill the process using port 8080
```bash
# Find process
lsof -i :8080

# Kill it
kill -9 <PID>
```

### Python Service Won't Start

**Error**: `No module named 'torch'`
**Solution**: Virtual environment not activated
```bash
source venv/bin/activate
pip install -r requirements.txt
```

**Error**: `Port 5000 already in use`
**Solution**: Kill the process
```bash
lsof -i :5000
kill -9 <PID>
```

### Frontend Issues

**Error**: `Cannot GET /api/users/login`
**Solution**: Java backend not running or wrong port
- Check Java backend is running on port 8080
- Check [api.js](frontend/src/utils/api.js) has `API_BASE_URL = 'http://localhost:8080'`

**Error**: CORS errors
**Solution**: Check [CorsConfig.java](java-backend/src/main/java/com/imagesearch/config/CorsConfig.java) allows `http://localhost:3000`

### Search Not Working

**Error**: Search returns no results
**Possible causes**:
1. Python service not running → Check Terminal 1
2. Images not embedded yet → Wait 10-30 seconds after upload
3. FAISS index missing → Re-upload images

**Check Python service logs**:
```bash
# In Terminal 1, you should see:
# "Embed request: userId=1, folderId=1, count=3"
# "Successfully embedded 3 images"
```

## Verifying Everything Works

### Check All Services

```bash
# Terminal 4 - Run these checks

# 1. Python service health
curl http://localhost:5000/
# Expected: {"service":"Image Search Microservice","status":"running"}

# 2. Java backend health
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# 3. Database connection
psql -U imageuser -d imagesearch -c "\dt"
# Expected: List of tables (users, folders, images, sessions, folder_shares)

# 4. Frontend
curl http://localhost:3000
# Expected: HTML page
```

### Check Logs

**Java Backend:**
```bash
cd java-backend
./gradlew bootRun
# Look for:
# - "Hibernate: create table users..."
# - "PythonSearchClient initialized with base URL: http://localhost:5000"
# - "Started ImageSearchApplication"
```

**Python Service:**
```bash
cd search-service
python app.py
# Look for:
# - "Loading CLIP model 'ViT-B/32' on device: cuda/cpu"
# - "CLIP model loaded successfully"
# - "Uvicorn running on http://0.0.0.0:5000"
```

## Development Workflow

### Making Changes to Java Backend

1. Edit code in `java-backend/src/main/java/com/imagesearch/`
2. Gradle auto-recompiles (if using `--continuous` flag)
3. Or restart: `./gradlew bootRun`

### Making Changes to React Frontend

1. Edit code in `frontend/src/`
2. React hot-reloads automatically (no restart needed)

### Making Changes to Python Service

1. Edit code in `search-service/`
2. Stop service (Ctrl+C)
3. Restart: `python app.py`

## Production Deployment

For production deployment, consider:

1. **Database**: Use managed PostgreSQL (AWS RDS, Google Cloud SQL)
2. **Java Backend**: Package as JAR and deploy to cloud (AWS Elastic Beanstalk, Heroku)
3. **Python Service**: Containerize with Docker and deploy separately
4. **Frontend**: Build and serve static files with Nginx or CDN
5. **Environment Variables**: Use secrets manager, never commit passwords

Example build commands:
```bash
# Java backend
cd java-backend
./gradlew clean build
java -jar build/libs/image-search-backend-1.0.0.jar

# React frontend
cd frontend
npm run build
# Serve the 'build' folder with Nginx
```

## Next Steps

- ✅ Run all three services
- ✅ Create an account
- ✅ Upload some images
- ✅ Test semantic search
- ✅ Try sharing folders
- ✅ Check the [CV_BULLET_POINTS.md](CV_BULLET_POINTS.md) for interview prep

## Getting Help

If you encounter issues:
1. Check the troubleshooting section above
2. Review logs in each terminal
3. Verify all prerequisites are installed
4. Check [POSTGRESQL_SETUP.md](POSTGRESQL_SETUP.md) for database issues

Happy coding! 🚀
