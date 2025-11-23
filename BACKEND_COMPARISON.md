# Backend Comparison: Java vs Python

This project supports **TWO interchangeable backends** that implement the **same REST API**. You can run the React frontend with either backend.

## JSON Field Naming Convention ✅

**Both backends now use snake_case for JSON fields** (e.g., `folder_name`, `user_id`, `is_owner`) to ensure frontend compatibility.

**Java Configuration:**
```yaml
# java-backend/src/main/resources/application.yml
spring:
  jackson:
    property-naming-strategy: SNAKE_CASE
```

This ensures the Java backend returns JSON fields identical to Python, so the React frontend works with both backends without modification.

## Quick Comparison

| Feature | Java (Spring Boot) | Python (FastAPI) |
|---------|-------------------|------------------|
| **Framework** | Spring Boot 3.2 | FastAPI 0.104 |
| **Language** | Java 17 | Python 3.12 |
| **Database** | PostgreSQL + JPA/Hibernate | SQLite + direct SQL |
| **Port** | 8080 | 8000 |
| **JSON Format** | snake_case (configured) | snake_case (default) |
| **Architecture** | Microservices (calls Python search service) | Monolithic (CLIP + FAISS embedded) |
| **Password Hashing** | BCrypt | PBKDF2-HMAC-SHA256 |
| **Build Tool** | Gradle 8.5 | pip + requirements.txt |
| **Best For** | Demonstrating Java enterprise skills | Fast prototyping, AI/ML integration |

---

## How to Switch Backends

### Option 1: Use Java Backend (Default)

```bash
# Terminal 1: Python Search Microservice
cd search-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python app.py  # Runs on port 5000

# Terminal 2: Java Backend
cd java-backend
export DB_USERNAME=imageuser DB_PASSWORD=imagepass123
./gradlew bootRun  # Runs on port 8080

# Terminal 3: React Frontend
cd frontend
npm start  # Uses Java backend by default
```

Frontend opens at http://localhost:3000 → Calls Java at :8080 → Calls Python search at :5000

### Option 2: Use Python Backend (Monolithic)

```bash
# Terminal 1: Python Backend (has CLIP + FAISS built-in)
cd python-backend
python3 -m venv venv && source venv/bin/activate
pip install -r ../requirements.txt
uvicorn api:app --reload --port 8000  # Runs on port 8000

# Terminal 2: React Frontend
cd frontend
REACT_APP_BACKEND=python npm start  # Switches to Python backend
```

Frontend opens at http://localhost:3000 → Calls Python at :8000 (Python handles everything)

---

## API Compatibility

Both backends implement the **exact same RESTful API endpoints**:

### User Management
- `POST /api/users/register` - Create user
- `POST /api/users/login` - Login and get token
- `POST /api/users/logout` - Logout
- `DELETE /api/users/account` - Delete account

### Folder Management
- `GET /api/folders?token=xxx` - Get all accessible folders
- `DELETE /api/folders` - Delete folders (with DB + FAISS cleanup)
- `POST /api/folders/share` - Share folder with user
- `GET /api/folders/shared?token=xxx` - Get shared folders

### Image Operations
- `POST /api/images/upload` - Upload images (multipart/form-data)
- `GET /api/images/search?query=xxx&token=xxx` - Semantic search

---

## Architecture Comparison

### Java Backend Architecture (Microservices)

```
React Frontend (3000)
     ↓ HTTP
Java Backend (8080)
     ├── Controllers (REST endpoints)
     ├── Services (business logic)
     ├── Repositories (data access)
     ├── Entities (JPA models)
     ↓ PostgreSQL (5432)
     ↓ HTTP
Python Search Service (5000)
     └── CLIP + FAISS
```

**Pros:**
- ✅ Professional microservices architecture
- ✅ Clear separation of concerns
- ✅ Production-ready database (PostgreSQL)
- ✅ Strong typing with JPA entities
- ✅ Demonstrates enterprise Java skills
- ✅ Scalable (Python service can scale independently)

**Cons:**
- ❌ More complex setup (3 services to run)
- ❌ Higher resource usage
- ❌ Network latency for search requests

### Python Backend Architecture (Monolithic)

```
React Frontend (3000)
     ↓ HTTP
Python Backend (8000)
     ├── FastAPI routes
     ├── Database (SQLite)
     ├── CLIP embeddings
     └── FAISS search
```

**Pros:**
- ✅ Simpler setup (2 services to run)
- ✅ Faster development iteration
- ✅ Lower resource usage
- ✅ No network overhead for search
- ✅ Good for prototyping

**Cons:**
- ❌ SQLite not ideal for production
- ❌ Tight coupling of concerns
- ❌ Harder to scale
- ❌ Doesn't demonstrate Java skills

---

## When to Use Which Backend

### Use Java Backend When:
- 📝 Preparing for Java backend developer interviews
- 🎯 Demonstrating microservices architecture knowledge
- 🏢 Showcasing enterprise patterns (Spring Boot, JPA, PostgreSQL)
- 📈 Building for production deployment
- 💼 Adding to your CV/portfolio as Java project

### Use Python Backend When:
- ⚡ Rapid prototyping
- 🔬 Experimenting with AI/ML features
- 🧪 Testing CLIP/FAISS functionality quickly
- 💻 Working on a single machine with limited resources
- 🚀 Quick demos without database setup

---

## Code Structure Comparison

### Java Backend
```
java-backend/
├── src/main/java/com/imagesearch/
│   ├── controller/      # REST endpoints
│   ├── service/         # Business logic
│   ├── repository/      # Data access (Spring Data JPA)
│   ├── model/
│   │   ├── entity/      # JPA entities
│   │   └── dto/         # Request/Response DTOs
│   ├── client/          # Python service HTTP client
│   ├── config/          # Spring configuration
│   └── exception/       # Global exception handling
└── src/main/resources/
    └── application.yml  # Configuration
```

### Python Backend
```
python-backend/
├── api.py               # FastAPI app + CORS
├── routes/
│   ├── user_routes.py   # User endpoints
│   ├── images_routes.py # Image endpoints
│   └── sharing_routes.py # Folder sharing
├── database.py          # PostgreSQL operations
├── faiss_handler.py     # FAISS indexing
├── utils.py             # CLIP embeddings
└── security.py          # Password hashing
```

---

## Database Migration (Python → Java)

If you have data in Python backend (SQLite) and want to move to Java backend (PostgreSQL):

```bash
# Export from SQLite
sqlite3 database.sqlite .dump > dump.sql

# Convert and import to PostgreSQL
# (Manual conversion needed due to schema differences)
# OR just start fresh with Java backend
```

---

## Performance Comparison

### Search Latency

**Java Backend:**
- HTTP call to Python service: ~10-50ms
- CLIP embedding + FAISS search: ~100-300ms
- **Total: ~110-350ms**

**Python Backend:**
- Direct CLIP embedding + FAISS search: ~100-300ms
- **Total: ~100-300ms** (slightly faster)

### Upload Throughput

**Both backends:** Similar performance (~same CLIP processing)
- Both use background tasks for embedding generation
- User gets immediate response before CLIP processing

---

## Environment Variables

### Java Backend

```bash
# Database
export DB_USERNAME=imageuser
export DB_PASSWORD=your_password

# Python search service
export SEARCH_SERVICE_URL=http://localhost:5000
```

### Python Backend

```bash
# Database location
export DB_DIR=./data

# Storage backend
export STORAGE_BACKEND=local  # or 's3'
```

### React Frontend

```bash
# Choose backend
export REACT_APP_BACKEND=java    # default
# or
export REACT_APP_BACKEND=python
```

---

## Summary

Both backends provide the **exact same functionality** to the frontend:
- ✅ User registration/login
- ✅ Image upload
- ✅ Semantic search
- ✅ Folder management
- ✅ Folder sharing

**Choose Java** if you want to demonstrate enterprise Java skills and microservices architecture.

**Choose Python** if you want a simpler, monolithic setup for quick development.

**The React frontend doesn't care** - it just calls the REST API!
