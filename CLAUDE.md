# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Semantic image search application using CLIP embeddings and FAISS vector similarity. Features **two interchangeable backend implementations** (Java Spring Boot and Python FastAPI) that share the same PostgreSQL database and delegate AI operations to a dedicated Python search microservice.

**Core Technology Stack:**
- **Backends:** Java Spring Boot 3.2 (port 8080) OR Python FastAPI (port 8000) - choose one
- **Search Service:** Python FastAPI with CLIP + FAISS (port 5000) - shared by both backends
- **Frontend:** React 18 (port 3000)
- **Database:** PostgreSQL 15+ (port 5432)
- **AI/ML:** OpenAI CLIP for embeddings, FAISS for vector search

## Architecture Overview

### Three-Tier Microservices Architecture

```
React Frontend (3000)
    ↓
Backend (choose one):
├─→ Java Spring Boot (8080)    [Recommended for interviews/production]
└─→ Python FastAPI (8000)      [Alternative implementation]
    ↓
PostgreSQL Database (5432)
    ↓
Python Search Service (5000)   [Shared by both backends]
    ├─→ CLIP Model (image/text embeddings)
    └─→ FAISS (vector similarity search)
```

**Key Architectural Principle:** Both backends implement identical REST APIs and share the same database schema. The frontend can switch between backends without code changes by setting `REACT_APP_BACKEND` environment variable.

### Critical Data Flow

1. **Image Upload:** User uploads → Backend saves to `data/uploads/{userId}/{folderId}/` → Calls search service to generate CLIP embeddings → Search service stores FAISS index in `data/indexes/{userId}/{folderId}/`
2. **Search:** User enters text query → Backend forwards to search service → CLIP embeds query → FAISS returns top-K similar images → Backend enriches with metadata from PostgreSQL

### Unified Data Directory (November 2024 Refactor)

All persistent data lives in `data/`:
```
data/
├── uploads/          # All uploaded images (both backends write here)
│   └── {userId}/{folderId}/
└── indexes/          # All FAISS indexes (only search service writes)
    └── {userId}/{folderId}/
```

**Never use old paths:** `images/`, `backend/images/`, `python-backend/faisses_indexes/` (legacy, gitignored)

## Common Development Commands

### Running the Application

**Option 1: Java Backend Stack**
```bash
# Terminal 1: Python search service
cd python-search-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python app.py

# Terminal 2: Java backend
./scripts/run-java.sh
# OR manually:
cd java-backend
export DB_USERNAME=imageuser DB_PASSWORD=imagepass123
./gradlew bootRun

# Terminal 3: React frontend (points to Java backend)
cd frontend
npm start
# OR explicitly: REACT_APP_BACKEND=java npm start
```

**Option 2: Python Backend Stack (Quick Development)**
```bash
# Terminal 1: Python backend (with search service integration)
cd python-backend
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
export DB_USERNAME=imageuser DB_PASSWORD=imagepass123
uvicorn api:app --reload --port 8000

# Terminal 2: Python search service
cd python-search-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python app.py

# Terminal 3: React frontend (points to Python backend)
cd frontend
REACT_APP_BACKEND=python npm start
```

### Database Setup (One-time)

```bash
# Create PostgreSQL database
sudo -u postgres psql
CREATE DATABASE imagesearch;
CREATE USER imageuser WITH PASSWORD 'imagepass123';
GRANT ALL PRIVILEGES ON DATABASE imagesearch TO imageuser;
\q

# OR use script:
./scripts/setup-postgres.sh
```

### Testing

```bash
# Java backend tests
cd java-backend
./gradlew test                    # Run all tests
./gradlew test --tests UserControllerTest  # Run specific test
./gradlew jacocoTestReport        # Generate coverage report (build/reports/jacoco/test/html/)

# Python backend tests
cd python-backend
pytest tests/                     # Run all tests
pytest tests/test_user_routes.py  # Run specific test file

# API integration tests
./scripts/test-api.sh java        # Test Java backend
./scripts/test-api.sh python      # Test Python backend
```

### Building

```bash
# Java backend
cd java-backend
./gradlew build                   # Compile + test
./gradlew clean build             # Clean build
./gradlew bootJar                 # Create executable JAR (build/libs/)

# Frontend production build
cd frontend
npm run build                     # Creates build/ directory
```

### Docker Deployment

```bash
# Java stack (microservices architecture)
docker-compose -f docker-compose.java.yml up --build -d

# Python stack (monolithic)
docker-compose -f docker-compose.python.yml up --build -d

# View logs
docker-compose -f docker-compose.java.yml logs -f
docker-compose -f docker-compose.java.yml logs -f java-backend
```

## Backend-Specific Patterns

### Java Backend (Spring Boot)

**Layered Architecture:**
```
Controller → Service → Repository → Entity
    ↓          ↓          ↓          ↓
  DTOs    Business    JPA Repos  Database
          Logic                   Models
```

**Key Files:**
- `src/main/resources/application.yml` - Configuration (database, search service URL, ports)
- `src/main/java/com/imagesearch/`
  - `controller/` - REST endpoints with `@RestController`, `@GetMapping`, `@PostMapping`
  - `service/` - Business logic with `@Service`, `@Transactional`
  - `repository/` - JPA repositories extending `JpaRepository<Entity, ID>`
  - `model/entity/` - JPA entities with `@Entity`, `@Table`, `@Id`
  - `model/dto/` - Request/Response DTOs (snake_case JSON via Jackson config)
  - `client/PythonSearchClient.java` - WebClient for calling search service
  - `exception/GlobalExceptionHandler.java` - `@ControllerAdvice` for error handling

**Important Conventions:**
- **JSON Naming:** Uses snake_case via `spring.jackson.property-naming-strategy: SNAKE_CASE` in application.yml to match Python backend
- **Database Path:** Uses `data/uploads/` via `StaticResourceConfig.java`
- **Search Service URL:** Configured in application.yml, defaults to `http://localhost:5000`, can be overridden with `SEARCH_SERVICE_URL` env var
- **Testing:** JUnit 5 + Mockito, tests in `src/test/java/com/imagesearch/`

**Common Tasks:**
```bash
# Run with custom database credentials
DB_USERNAME=user DB_PASSWORD=pass ./gradlew bootRun

# Run with different search service
SEARCH_SERVICE_URL=http://localhost:5001 ./gradlew bootRun

# Debug mode
./gradlew bootRun --debug-jvm
```

### Python Backend (FastAPI)

**Modular Architecture:**
```
api.py (FastAPI app) → routes/ → database.py → PostgreSQL
                    ↓
                search_client.py → Python Search Service
```

**Key Files:**
- `api.py` - FastAPI app, CORS, router registration
- `database.py` - PostgreSQL connection using psycopg2, connection pooling
- `routes/` - Endpoint modules (user_routes.py, images_routes.py, sharing_routes.py)
- `search_client.py` - HTTP client for search service (similar to Java's PythonSearchClient)
- `pydantic_models.py` - Request/response models with Pydantic
- `exceptions.py` - Custom exception classes (DatabaseException, etc.)
- `exception_handlers.py` - FastAPI exception handlers

**Important Conventions:**
- **Database Connection:** Uses environment variables `DB_USERNAME`, `DB_PASSWORD`, `DB_HOST`, `DB_NAME`
- **Data Directory:** Resolves to `data/uploads/` via `aws_handler.py:get_path_to_save()`
- **Response Models:** Always use `response_model` parameter on endpoints for type safety
- **File Uploads:** Use `Form(...)` + `File(...)` for multipart/form-data, not Pydantic models

**Common Tasks:**
```bash
# Run with auto-reload
uvicorn api:app --reload --port 8000

# Run with custom database
DB_USERNAME=user DB_PASSWORD=pass uvicorn api:app --port 8000

# Run tests with coverage
pytest tests/ --cov=. --cov-report=html
```

### Python Search Service (Shared Microservice)

**Purpose:** Dedicated service for AI/ML operations, used by both Java and Python backends.

**Key Files:**
- `app.py` - FastAPI application entry point (port 5000)
- `embedding_service.py` - CLIP model loading and inference
- `search_handler.py` - FAISS index management and similarity search

**Endpoints:**
- `POST /embed-images` - Generate embeddings for uploaded images
- `POST /search` - Text query → FAISS similarity search
- `POST /create-index` - Create FAISS index for folder
- `DELETE /delete-index` - Delete FAISS index

**Critical Implementation Details:**
- **FAISS Index Type:** `IndexFlatIP` (inner product) with **normalized vectors** for cosine similarity
- **Index Path:** `data/indexes/{userId}/{folderId}.faiss` (Docker: `/app/data/indexes/`)
- **Image Path Resolution:** Converts `images/` prefix to `data/uploads/` automatically
- **Model Loading:** CLIP model loads on first import (~1-2s delay), runs on CPU by default

## Database Schema

**PostgreSQL with JPA/Hibernate (Java) or psycopg2 (Python):**

```sql
users (id, username, password, created_at)
  ↓ (user_id FK)
folders (id, user_id, folder_name, created_at)
  ↓ (folder_id FK)
images (id, user_id, folder_id, filepath, uploaded_at)

sessions (token, user_id, expires_at, last_seen, created_at)

folder_shares (id, folder_id, shared_with_user_id, permission, created_at)
```

**Key Relationships:**
- Users own folders (1:N)
- Folders contain images (1:N)
- Users share folders with permissions (N:M via folder_shares)
- Sessions cascade delete when user is deleted

**Schema Management:**
- **Java:** Hibernate auto-DDL (`spring.jpa.hibernate.ddl-auto: update`)
- **Python:** Manual schema management in `database.py:init_db()`

## Frontend Architecture

**React 18 without routing library - manual state management.**

**Key Files:**
- `src/App.jsx` - Main layout, auth state, folder selection state
- `src/utils/api.js` - **Centralized API layer** with all endpoints and error handling
- `src/components/` - UI components (Login, SearchImages, UploadImages, etc.)

**Backend Switching Logic (`src/utils/api.js`):**
```javascript
const BACKEND = process.env.REACT_APP_BACKEND || 'java';
const API_BASE_URL = BACKEND === 'python'
  ? 'http://localhost:8000'   // Python backend
  : 'http://localhost:8080';  // Java backend (default)
```

**State Management:**
- `user` state in App.jsx (contains userId, username, token)
- Token persisted in localStorage via `saveToken()` / `getToken()`
- Folder selection state passed via props drilling

**API Communication:**
- Use centralized functions from `utils/api.js`: `loginUser()`, `uploadImages()`, `searchImages()`
- All responses use snake_case field names (matches both backends)
- Error handling with `APIError` class and `HTTP_STATUS` constants

## Important Implementation Notes

### Switching Between Backends

The project is designed so both backends can coexist (on different ports) but only one should be used at a time by the frontend.

**To switch:**
1. Stop current backend
2. Start desired backend (Java on 8080 or Python on 8000)
3. Ensure search service is running (port 5000) - **required for both**
4. Restart frontend with correct `REACT_APP_BACKEND` value

**No data migration needed** - both backends share the same PostgreSQL database.

### JSON Field Naming Convention

Both backends return snake_case JSON fields (e.g., `folder_name`, `user_id`, `is_owner`).

**Java:** Configured via `spring.jackson.property-naming-strategy: SNAKE_CASE` in application.yml
**Python:** Native Pydantic behavior

This ensures frontend compatibility without code changes.

### FAISS Index Management

**Critical:** FAISS indexes use `IndexFlatIP` which requires **normalized vectors** for cosine similarity.

```python
# In search_handler.py
faiss.normalize_L2(embeddings)  # Always normalize before adding to index
faiss.normalize_L2(query_embedding)  # Always normalize query
```

**Index Structure:**
- One index per folder: `data/indexes/{userId}/{folderId}.faiss`
- Vector IDs map to SQLite/PostgreSQL image IDs
- Indexes are deleted when folders are deleted (both backends handle this)

### Security Considerations

**Password Hashing:**
- Java: BCrypt (Spring Security Crypto)
- Python: PBKDF2-HMAC-SHA256 with 390K iterations

**Note:** Passwords are NOT compatible between backends (different algorithms). If switching backends, users must re-register.

**Session Management:**
- 32-byte random tokens
- 12-hour expiration with sliding window refresh
- Tokens stored in database sessions table

### Port Conflicts

If ports are already in use:
```bash
# Check what's using a port
lsof -i :8080
lsof -i :8000
lsof -i :5000

# Kill process
kill -9 <PID>

# Or run on different port
./gradlew bootRun --args='--server.port=8081'
uvicorn api:app --port 9000
```

### Path Resolution Issues

**Always use the unified data directory structure:**
- Images: `data/uploads/{userId}/{folderId}/`
- FAISS indexes: `data/indexes/{userId}/{folderId}/`

**Never hardcode absolute paths.** Use path resolution:
- Java: `StaticResourceConfig.java` maps `/images/**` URLs to `data/uploads/`
- Python: `aws_handler.py:get_path_to_save()` resolves to `data/uploads/`
- Search Service: `search_handler.py:get_faiss_base_folder()` resolves to `data/indexes/`

### Docker vs Local Development

**Search service path resolution handles both:**
```python
def get_faiss_base_folder():
    if os.path.exists("/app/data/indexes"):  # Docker
        return "/app/data/indexes"
    else:  # Local development
        return os.path.join(project_root, "data/indexes")
```

**Docker volume mapping:**
- `app-data:/app/data` contains both `uploads/` and `indexes/`
- Single volume shared by all services in the stack

## Testing Strategy

### Unit Tests
- **Java:** `src/test/java/com/imagesearch/` - JUnit 5 + Mockito, H2 in-memory DB
- **Python:** `tests/` - pytest with mocking

### Integration Tests
- Use `scripts/test-api.sh [java|python]` for full API workflow testing
- Tests: register → login → upload → search → share → delete

### Manual Testing
- Postman collection: `Image_Search_API.postman_collection.json`
- See `API_TESTING.md` and `QUICK_TEST_REFERENCE.md`

## Common Pitfalls

1. **Forgetting to start search service** - Both backends require Python search service on port 5000
2. **Wrong backend port in frontend** - Must set `REACT_APP_BACKEND` correctly
3. **Using old image paths** - Always use `data/uploads/`, never `images/`
4. **FAISS without normalization** - Always normalize vectors before FAISS operations
5. **PostgreSQL not running** - Check with `systemctl status postgresql` or start with `./scripts/setup-postgres.sh`
6. **Environment variables not set** - Java backend needs `DB_USERNAME` and `DB_PASSWORD`

## Key Documentation Files

- [README.md](README.md) - Project overview and quick start
- [DOCKER.md](DOCKER.md) - Docker deployment guide
- [docs/ARCHITECTURE_REFACTORING.md](docs/ARCHITECTURE_REFACTORING.md) - Data directory refactoring details
- [docs/BACKEND_COMPARISON.md](docs/BACKEND_COMPARISON.md) - Java vs Python backend comparison
- [docs/HOW_TO_SWITCH_BACKENDS.md](docs/HOW_TO_SWITCH_BACKENDS.md) - Detailed switching instructions
- [docs/PORTS_AND_ARCHITECTURE.md](docs/PORTS_AND_ARCHITECTURE.md) - Port configuration reference
- [API_TESTING.md](API_TESTING.md) - API testing guide with Postman