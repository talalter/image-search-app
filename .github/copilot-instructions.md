# CLAUDE.md



This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
My main focus now is on java-backend using python-search-service. I want to focus on that. 
Semantic image search application using CLIP embeddings and vector similarity search. Features **two interchangeable backend implementations** (Java Spring Boot and Python FastAPI) that share the same PostgreSQL database. The Java backend supports **dual search service architecture** with automatic failover for high availability.

**Core Technology Stack:**
- **Backends:** Java Spring Boot 3.2 (port 8080) OR Python FastAPI (port 8000) - choose one (PREFER JAVA)
- **Search Services:** 
  - Java Search Service (port 5001) - Elasticsearch + ONNX CLIP [Primary for Java backend]
  - Python Search Service (port 5000) - FAISS + PyTorch CLIP [Backup for Java backend, or standalone]
- **Frontend:** React 18 (port 3000)
- **Database:** PostgreSQL 15+ (port 5432)
- **Vector Search:** Elasticsearch (port 9200) for Java search service, FAISS for Python search service
- **AI/ML:** OpenAI CLIP for embeddings

## Architecture Overview

### Three-Tier Microservices Architecture with Dual Search Services

```
React Frontend (3000)
    ↓
Backend (choose one):
├─→ Java Spring Boot (8080)    [Recommended - with circuit breaker failover]
└─→ Python FastAPI (8000)      [Alternative implementation]
    ↓
PostgreSQL Database (5432)
    ↓
Search Services (for Java backend - Active-Passive Failover):
├─→ Java Search Service (5001)   [Primary - Elasticsearch + ONNX CLIP]
└─→ Python Search Service (5000) [Backup - FAISS + PyTorch CLIP]
    
OR (for Python backend):
Python Search Service (5000)     [Direct integration - FAISS + PyTorch CLIP]
```

**Key Architectural Principle:** 

1. **Backend Interchangeability:** Both Java and Python backends implement identical REST APIs and share the same database. Frontend switches via `REACT_APP_BACKEND` environment variable.

2. **Java Backend Resilience:** When using Java backend, it employs a **circuit breaker pattern** to automatically failover between two search service implementations:
   - **Primary:** Java Search Service (port 5001) - Elasticsearch for vector search, ONNX for CLIP embeddings
   - **Backup:** Python Search Service (port 5000) - FAISS for vector search, PyTorch for CLIP embeddings

3. **Python Backend Simplicity:** Python backend directly integrates with Python Search Service (no failover).

### Circuit Breaker Failover Behavior (Java Backend Only)

**Normal Operation (Circuit CLOSED):**
- All search requests route to Java Search Service (Elasticsearch)
- Circuit breaker monitors health (failure rate, slow requests)

**Failover Triggered (Circuit OPEN):**
- Java Search Service fails (down/slow/errors exceed 50% threshold)
- Circuit breaker immediately routes all requests to Python Search Service (FAISS)
- **Trade-off:** Images uploaded during failover won't be searchable in Elasticsearch until re-indexed

**Recovery (Circuit HALF_OPEN → CLOSED):**
- After 60 seconds, circuit breaker sends 5 test requests to Java Search Service
- If tests succeed → gradually switches back to Java Search Service
- If tests fail → stays on Python Search Service

**Key Configuration:**
```yaml
# java-backend/src/main/resources/application.yml
resilience4j:
  circuitbreaker:
    instances:
      javaSearchService:
        slidingWindowSize: 100
        failureRateThreshold: 50           # Open if 50% fail
        slowCallRateThreshold: 50          # Open if 50% slow (>10s)
        waitDurationInOpenState: 60s       # Test recovery after 60s
        permittedNumberOfCallsInHalfOpenState: 5
```

### Critical Data Flow

**1. Image Upload (Java Backend - Normal Operation with Java Search Service):**
- User uploads → Backend saves to `data/uploads/{userId}/{folderId}/`
- Backend calls Java Search Service → Generates CLIP embeddings via ONNX
- Java Search Service stores vectors in Elasticsearch index `images-{userId}-{folderId}`

**2. Image Upload (Java Backend - Failover with Python Search Service):**
- User uploads → Backend saves to `data/uploads/{userId}/{folderId}/`
- Circuit breaker detects Java Search Service down → Routes to Python Search Service
- Python Search Service generates CLIP embeddings → Stores FAISS index in `data/indexes/{userId}/{folderId}/`
- **Note:** Images uploaded during failover won't be in Elasticsearch until re-indexed

**3. Image Upload (Python Backend - Direct Integration):**
- User uploads → Backend saves to `data/uploads/{userId}/{folderId}/`
- Backend calls Python Search Service → Generates CLIP embeddings
- Python Search Service stores FAISS index in `data/indexes/{userId}/{folderId}/`

**4. Search (Java Backend - Normal Operation):**
- User enters text query → Backend forwards to Java Search Service
- Java Search Service embeds query via ONNX CLIP → Elasticsearch returns top-K similar images
- Backend enriches with metadata from PostgreSQL

**5. Search (Java Backend - Failover):**
- Circuit breaker routes to Python Search Service
- Python Search Service embeds query via PyTorch CLIP → FAISS returns top-K similar images
- Backend enriches with metadata from PostgreSQL

**6. Search (Python Backend):**
- User enters text query → Backend forwards to Python Search Service
- Python Search Service embeds query via PyTorch CLIP → FAISS returns top-K similar images
- Backend enriches with metadata from PostgreSQL

### Search Service Comparison

| Feature | Java Search Service (Primary) | Python Search Service (Backup/Standalone) |
|---------|-------------------------------|-------------------------------------------|
| **Port** | 5001 | 5000 |
| **Vector Store** | Elasticsearch | FAISS (file-based) |
| **CLIP Model** | ONNX Runtime | PyTorch |
| **Performance** | Faster startup, production-ready | Slower startup, lightweight |
| **Index Location** | Elasticsearch indices | `data/indexes/{userId}/{folderId}/` |
| **Scalability** | Horizontal scaling via Elasticsearch | Single-node file-based |
| **Used By** | Java backend (primary) | Java backend (backup), Python backend (direct) |
| **Dependencies** | Elasticsearch (9200) | None (self-contained) |
| **Best For** | Production, high availability | Development, backup, simple deployments |

### Unified Data Directory (November 2024 Refactor)

All persistent data lives in `data/`:
```
data/
├── uploads/          # All uploaded images (all backends write here)
│   └── {userId}/{folderId}/
└── indexes/          # FAISS indexes (Python search service only)
    └── {userId}/{folderId}/
```

**Note:** Java Search Service stores vectors in Elasticsearch (not file-based), so it doesn't use `data/indexes/`.

**Never use old paths:** `images/`, `backend/images/`, `python-backend/faisses_indexes/` (legacy, gitignored)

## Common Development Commands

### Running the Application

**Option 1: Java Backend with Python Search Service (Default - No Elasticsearch)**
```bash
# Terminal 1: Python Search Service (FAISS + PyTorch CLIP)
cd python-search-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python app.py

# Terminal 2: Java Backend (uses Python search service)
./scripts/run-java.sh
# OR manually:
cd java-backend
export DB_USERNAME=imageuser DB_PASSWORD=imagepass123
export SEARCH_BACKEND=python  # Use Python search service directly
./gradlew bootRun

# Terminal 3: React Frontend
cd frontend
npm start
```

**Option 2: Java Backend with Dual Search Services (High Availability - Requires Elasticsearch)**
```bash
# Terminal 1: Elasticsearch (required for Java search service)
sudo systemctl start elasticsearch
# OR with Docker: docker run -d -p 9200:9200 -e "discovery.type=single-node" elasticsearch:8.11.0

# Terminal 2: Java Search Service (Elasticsearch + ONNX CLIP)
cd java-search-service
./gradlew bootRun

# Terminal 3: Python Search Service (FAISS + PyTorch CLIP - backup)
cd python-search-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python app.py

# Terminal 4: Java Backend (with circuit breaker routing)
./scripts/run-java.sh
# OR manually:
cd java-backend
export DB_USERNAME=imageuser DB_PASSWORD=imagepass123
export SEARCH_BACKEND=java  # Use Java search service as primary
./gradlew bootRun

# Terminal 5: React Frontend
cd frontend
npm start
```

### Testing Circuit Breaker Failover (Java Backend)

**Manual Failover Test:**
```bash
# 1. Start both search services + Java backend + frontend (see Option 1 above)

# 2. Upload images and verify search works (should use Java Search Service)
curl -X POST http://localhost:8080/api/images/search \
  -H "Authorization: Bearer <token>" \
  -d '{"query":"cat","folder_ids":[1]}'

# 3. Check circuit breaker state (should be CLOSED)
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/circuitbreakers

# 4. Simulate Java Search Service failure - kill the process:
lsof -i :5001  # Find PID
kill -9 <PID>  # Kill Java Search Service

# 5. Try search again - should automatically failover to Python Search Service
curl -X POST http://localhost:8080/api/images/search \
  -H "Authorization: Bearer <token>" \
  -d '{"query":"cat","folder_ids":[1]}'

# 6. Check circuit breaker state (should be OPEN)
curl http://localhost:8080/actuator/circuitbreakers

# 7. Restart Java Search Service
cd java-search-service && ./gradlew bootRun

# 8. Wait 60 seconds - circuit breaker will test recovery (HALF_OPEN)
# If tests succeed, it will switch back to Java Search Service (CLOSED)

# 9. Monitor circuit breaker state transition
watch -n 5 'curl -s http://localhost:8080/actuator/circuitbreakers | jq'
```

**View Circuit Breaker Metrics:**
```bash
# Health check
curl http://localhost:8080/actuator/health

# Circuit breaker details
curl http://localhost:8080/actuator/circuitbreakers

# Circuit breaker events (recent state changes)
curl http://localhost:8080/actuator/circuitbreakerevents
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

# Java search service
cd java-search-service
./gradlew build
./gradlew bootJar

# Frontend production build
cd frontend
npm run build                     # Creates build/ directory
```

### Docker Deployment

**Default: Python Search Service Only (No Elasticsearch)**
```bash
# Start Java backend with Python search service
docker-compose -f docker-compose.java.yml up --build -d

# View logs
docker-compose -f docker-compose.java.yml logs -f
```

**Optional: Add Java Search Service with Elasticsearch**
```bash
# Start main stack first
docker-compose -f docker-compose.java.yml up -d

# Then start Java search service + Elasticsearch
./scripts/start-java-search.sh
# OR manually:
docker-compose -f docker-compose.java-search.yml up -d

# View all logs
docker-compose -f docker-compose.java.yml logs -f
docker-compose -f docker-compose.java-search.yml logs -f elasticsearch
docker-compose -f docker-compose.java-search.yml logs -f java-search-service
```

**Python Stack (Monolithic)**
```bash
docker-compose -f docker-compose.python.yml up --build -d
```

## Backend-Specific Patterns

### Java Backend (Spring Boot) - Search Service Integration

**Strategy Pattern for Search Service Selection:**

The Java backend uses Spring's `@ConditionalOnProperty` to select which search service implementation to use as primary, combined with Resilience4j circuit breaker for automatic failover.

**Key Files:**
- `src/main/java/com/imagesearch/client/SearchClient.java` - Interface for search operations
- `src/main/java/com/imagesearch/client/JavaSearchClientImpl.java` - Primary implementation (Elasticsearch)
- `src/main/java/com/imagesearch/client/PythonSearchClientImpl.java` - Backup implementation (FAISS)
- `src/main/resources/application.yml` - Configuration for search services and circuit breaker

**Configuration in `application.yml`:**
```yaml
# Search Backend Configuration
# Determines which search implementation to use as primary
search:
  backend:
    type: ${SEARCH_BACKEND:java}  # Primary: java, Backup: python

# Java Search Service Configuration (Elasticsearch + ONNX)
java-search-service:
  base-url: ${JAVA_SEARCH_SERVICE_URL:http://localhost:5001}
  timeout-seconds: 30
  enabled: true

# Python Search Service Configuration (FAISS + PyTorch)
search-service:
  base-url: ${SEARCH_SERVICE_URL:http://localhost:5000}
  timeout-seconds: 30

# Circuit Breaker Configuration (automatic failover)
resilience4j:
  circuitbreaker:
    instances:
      javaSearchService:
        slidingWindowSize: 100
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 10s
        minimumNumberOfCalls: 10
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 5
```

**Circuit Breaker Implementation Example:**

```java
// filepath: java-backend/src/main/java/com/imagesearch/client/JavaSearchClientImpl.java

@Component
@ConditionalOnProperty(name = "search.backend.type", havingValue = "java")
public class JavaSearchClientImpl implements SearchClient {
    
    @Autowired
    private PythonSearchClientImpl pythonSearchClient;  // Backup service
    
    /**
     * Circuit Breaker Applied:
     * - Name: "javaSearchService" (configured in application.yml)
     * - Fallback: searchFallback() → routes to Python Search Service
     * - Opens circuit if 50% of requests fail or are slow (>10s)
     * - Protects against Java Search Service downtime
     * 
     * Interview talking point:
     * "We use circuit breaker pattern for automatic failover. When Elasticsearch
     * service fails, we immediately route to FAISS backup service instead of 
     * timing out. This prevents thread pool exhaustion and provides better UX."
     */
    @Override
    @CircuitBreaker(name = "javaSearchService", fallbackMethod = "searchFallback")
    public SearchServiceResponse search(SearchServiceRequest request) {
        logger.info("Calling Java Search Service (Elasticsearch): query='{}'", 
                   request.getQuery());
        
        SearchServiceResponse response = webClient.post()
                .uri("/api/search")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SearchServiceResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
        
        logger.info("Java Search Service returned {} results", 
                   response.getResults().size());
        return response;
    }
    
    /**
     * Fallback method: Route to Python Search Service when Java service fails.
     * 
     * This is called when:
     * - Circuit breaker is OPEN (too many failures)
     * - Java Search Service is down or slow
     * - Prevents waiting for timeout (fail fast)
     * 
     * Trade-off: Recent uploads during Java service downtime won't be 
     * searchable in Elasticsearch until re-indexed.
     */
    public SearchServiceResponse searchFallback(SearchServiceRequest request, 
                                               Exception exception) {
        logger.warn("Java Search Service failed, failing over to Python Search Service. " +
                   "Error: {}", exception.getMessage());
        return pythonSearchClient.search(request);
    }
}
```

**Layered Architecture:**
```
Controller → Service → SearchClient (interface) → Search Service
    ↓          ↓              ↓                         ↓
  DTOs    Business    JavaSearchClientImpl      Elasticsearch (5001)
          Logic         (with circuit breaker)
                             ↓ (fallback)
                      PythonSearchClientImpl    FAISS (5000)
```

**Other Key Files:**
- `src/main/resources/application.yml` - Configuration (database, search service URLs, ports)
- `src/main/java/com/imagesearch/`
  - `controller/` - REST endpoints with `@RestController`, `@GetMapping`, `@PostMapping`
  - `service/` - Business logic with `@Service`, `@Transactional`
  - `repository/` - JPA repositories extending `JpaRepository<Entity, ID>`
  - `model/entity/` - JPA entities with `@Entity`, `@Table`, `@Id`
  - `model/dto/` - Request/Response DTOs (snake_case JSON via Jackson config)
  - `exception/GlobalExceptionHandler.java` - `@ControllerAdvice` for error handling

**Important Conventions:**
- **JSON Naming:** Uses snake_case via `spring.jackson.property-naming-strategy: SNAKE_CASE`
- **Database Path:** Uses `data/uploads/` via `StaticResourceConfig.java`
- **Search Service Routing:** Primary service via `SEARCH_BACKEND` env var, automatic failover via circuit breaker
- **Testing:** JUnit 5 + Mockito, tests in `src/test/java/com/imagesearch/`

**Common Tasks:**
```bash
# Run with Java Search Service as primary (with failover to Python)
SEARCH_BACKEND=java ./gradlew bootRun

# Run with Python Search Service only (no failover)
SEARCH_BACKEND=python ./gradlew bootRun

# Run with custom database credentials
DB_USERNAME=user DB_PASSWORD=pass ./gradlew bootRun

# Debug mode
./gradlew bootRun --debug-jvm
```

### Python Backend (FastAPI)

**Modular Architecture:**
```
api.py (FastAPI app) → routes/ → database.py → PostgreSQL
                    ↓
                search_client.py → Python Search Service (5000)
```

**Key Files:**
- `api.py` - FastAPI app, CORS, router registration
- `database.py` - PostgreSQL connection using psycopg2, connection pooling
- `routes/` - Endpoint modules (user_routes.py, images_routes.py, sharing_routes.py)
- `search_client.py` - HTTP client for Python search service
- `pydantic_models.py` - Request/response models with Pydantic
- `exceptions.py` - Custom exception classes
- `exception_handlers.py` - FastAPI exception handlers

**Important Conventions:**
- **Database Connection:** Uses environment variables `DB_USERNAME`, `DB_PASSWORD`, `DB_HOST`, `DB_NAME`
- **Data Directory:** Resolves to `data/uploads/` via `aws_handler.py:get_path_to_save()`
- **Search Service:** Directly integrates with Python Search Service (no failover)
- **Response Models:** Always use `response_model` parameter on endpoints
- **File Uploads:** Use `Form(...)` + `File(...)` for multipart/form-data

**Common Tasks:**
```bash
# Run with auto-reload
uvicorn api:app --reload --port 8000

# Run with custom database
DB_USERNAME=user DB_PASSWORD=pass uvicorn api:app --port 8000

# Run tests with coverage
pytest tests/ --cov=. --cov-report=html
```

### Java Search Service (Elasticsearch + ONNX)

**Port:** 5001  
**Purpose:** Production-grade search service using Elasticsearch for vector similarity

**Key Features:**
- Elasticsearch for scalable vector search (kNN with cosine similarity)
- ONNX Runtime for fast CLIP inference (CPU optimized)
- Index per folder: `images-{userId}-{folderId}`
- RESTful API matching Python search service interface

**Dependencies:**
- Elasticsearch 8.x running on port 9200
- ONNX CLIP model: `clip-vit-base-patch32.onnx` in `models/` directory

**Key Files:**
- `src/main/java/com/searchservice/`
  - `service/EmbeddingService.java` - ONNX CLIP model inference
  - `service/ElasticsearchService.java` - Elasticsearch index management
  - `service/SearchService.java` - Orchestrates embedding + search
  - `controller/SearchController.java` - REST endpoints

**Elasticsearch Index Structure:**
```json
{
  "mappings": {
    "properties": {
      "image_id": { "type": "long" },
      "image_path": { "type": "keyword" },
      "embedding": {
        "type": "dense_vector",
        "dims": 512,
        "index": true,
        "similarity": "cosine"
      }
    }
  }
}
```

**Endpoints:**
- `POST /api/search` - Text query → cosine similarity search
- `POST /api/embed-images` - Generate embeddings for uploaded images
- `POST /api/create-index` - Create Elasticsearch index for folder
- `DELETE /api/delete-index` - Delete Elasticsearch index

**Configuration (`application.yml`):**
```yaml
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URL:http://localhost:9200}
    connection-timeout: 10s
    socket-timeout: 60s

clip:
  model:
    path: ${CLIP_MODEL_PATH:models/clip-vit-base-patch32.onnx}
```

**Common Tasks:**
```bash
# Run with custom Elasticsearch
ELASTICSEARCH_URL=http://localhost:9200 ./gradlew bootRun

# Check Elasticsearch health
curl http://localhost:9200/_cluster/health

# View all indices
curl http://localhost:9200/_cat/indices?v

# Delete specific index
curl -X DELETE http://localhost:9200/images-1-5
```

### Python Search Service (FAISS + PyTorch)

**Port:** 5000  
**Purpose:** Lightweight search service using FAISS for vector similarity

**Key Features:**
- FAISS for fast vector similarity (file-based, single-node)
- PyTorch CLIP for embeddings
- Index per folder: `data/indexes/{userId}/{folderId}.faiss`
- RESTful API matching Java search service interface

**Key Files:**
- `app.py` - FastAPI application entry point
- `embedding_service.py` - CLIP model loading and inference
- `search_handler.py` - FAISS index management and similarity search

**Endpoints:**
- `POST /search` - Text query → FAISS similarity search  
- `POST /embed-images` - Generate embeddings and save to FAISS
- `POST /create-index` - Create FAISS index for folder
- `DELETE /delete-index` - Delete FAISS index file

**Critical Implementation Details:**
- **FAISS Index Type:** `IndexFlatIP` (inner product) with **normalized vectors** for cosine similarity
- **Index Path:** `data/indexes/{userId}/{folderId}.faiss`
- **Image Path Resolution:** Converts `images/` prefix to `data/uploads/` automatically
- **Model Loading:** CLIP model loads on first import (~1-2s delay), runs on CPU

**Normalization Example:**
```python
# In search_handler.py
import faiss
import numpy as np

# Always normalize before adding to FAISS
faiss.normalize_L2(embeddings)  # L2 normalization for cosine similarity
index.add(embeddings)

# Always normalize query
faiss.normalize_L2(query_embedding)
distances, indices = index.search(query_embedding, top_k)
```

**Common Tasks:**
```bash
# Run with custom port
python app.py --port 5001

# Test embedding endpoint
curl -X POST http://localhost:5000/embed-images \
  -H "Content-Type: application/json" \
  -d '{"user_id":1,"folder_id":5,"image_paths":["data/uploads/1/5/cat.jpg"]}'

# Test search endpoint
curl -X POST http://localhost:5000/search \
  -H "Content-Type: application/json" \
  -d '{"query":"cat","user_id":1,"folder_id":5,"top_k":10}'
```

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
3. Ensure appropriate search service is running:
   - Java backend: Can use both search services (Java primary + Python backup) OR just one
   - Python backend: Requires Python search service only
4. Restart frontend with correct `REACT_APP_BACKEND` value

**No data migration needed** - both backends share the same PostgreSQL database.

### Search Service Failover Trade-offs (Java Backend)

**Known Limitation:** When using dual search services with circuit breaker failover, images uploaded during failover won't be immediately searchable in the primary service.

**Example Scenario:**
1. Java Search Service is healthy → images uploaded → vectors stored in Elasticsearch
2. Java Search Service crashes → circuit breaker opens → routes to Python Search Service  
3. User uploads more images → vectors stored in FAISS (not in Elasticsearch)
4. Java Search Service recovers → circuit breaker switches back
5. **Problem:** Images from step 3 are not in Elasticsearch yet

**Resolution Options:**
- **Accepted trade-off:** Images uploaded during failover temporarily unsearchable in primary service
- **Future enhancement:** Implement background re-indexing job when switching back
- **Alternative:** Use single search service (no failover) for simpler deployments

**Interview Talking Point:**
> "We chose eventual consistency over dual-write complexity. During failover, new uploads go to backup service. When primary recovers, we accept temporary inconsistency rather than adding dual-write overhead. For critical applications, we'd implement async re-indexing."

### JSON Field Naming Convention

Both backends return snake_case JSON fields (e.g., `folder_name`, `user_id`, `is_owner`).

**Java:** Configured via `spring.jackson.property-naming-strategy: SNAKE_CASE` in application.yml
**Python:** Native Pydantic behavior

This ensures frontend compatibility without code changes.

### FAISS Index Management (Python Search Service)

**Critical:** FAISS indexes use `IndexFlatIP` which requires **normalized vectors** for cosine similarity.

```python
# In search_handler.py
faiss.normalize_L2(embeddings)  # Always normalize before adding to index
faiss.normalize_L2(query_embedding)  # Always normalize query
```

**Index Structure:**
- One index per folder: `data/indexes/{userId}/{folderId}.faiss`
- Vector IDs map to PostgreSQL image IDs
- Indexes are deleted when folders are deleted (both backends handle this)

### Elasticsearch Index Management (Java Search Service)

**Index Naming Convention:** `images-{userId}-{folderId}`

**Index Lifecycle:**
- Created automatically on first image upload to folder
- Deleted when folder is deleted via backend API
- Persisted in Elasticsearch cluster (survives service restarts)

**Monitoring:**
```bash
# List all indices
curl http://localhost:9200/_cat/indices?v

# Check specific index
curl http://localhost:9200/images-1-5

# Get index mapping
curl http://localhost:9200/images-1-5/_mapping

# Count documents in index
curl http://localhost:9200/images-1-5/_count
```

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

**Required Ports:**
- 3000: React frontend
- 8000: Python backend (if using Python stack)
- 8080: Java backend
- 5000: Python search service
- 5001: Java search service
- 5432: PostgreSQL
- 9200: Elasticsearch (for Java search service)

**If ports are in use:**
```bash
# Check what's using a port
lsof -i :8080
lsof -i :5001
lsof -i :9200

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
- Java Backend: `StaticResourceConfig.java` maps `/images/**` URLs to `data/uploads/`
- Python Backend: `aws_handler.py:get_path_to_save()` resolves to `data/uploads/`
- Python Search Service: `search_handler.py:get_faiss_base_folder()` resolves to `data/indexes/`
- Java Search Service: Uses Elasticsearch (no file paths for vectors)

### Docker vs Local Development

**Search service path resolution handles both:**
```python
# Python search service
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
- **Java Backend:** `src/test/java/com/imagesearch/` - JUnit 5 + Mockito, H2 in-memory DB
- **Java Search Service:** `src/test/java/com/searchservice/` - JUnit 5 + Mockito
- **Python Backend:** `tests/` - pytest with mocking
- **Python Search Service:** `tests/` - pytest with mocking

### Integration Tests
- Use `scripts/test-api.sh [java|python]` for full API workflow testing
- Tests: register → login → upload → search → share → delete

### Manual Testing
- Postman collection: `Image_Search_API.postman_collection.json`
- See `API_TESTING.md` and `QUICK_TEST_REFERENCE.md`

### Circuit Breaker Testing (Java Backend)
- Start both search services
- Upload images and verify search works
- Kill Java search service and verify automatic failover
- Monitor circuit breaker state via actuator endpoints
- Restart Java search service and verify gradual failback

## Common Pitfalls

1. **Forgetting to start search service** - Java/Python backends require at least one search service
2. **Wrong backend port in frontend** - Must set `REACT_APP_BACKEND` correctly
3. **Using old image paths** - Always use `data/uploads/`, never `images/`
4. **FAISS without normalization** - Always normalize vectors before FAISS operations
5. **PostgreSQL not running** - Check with `systemctl status postgresql`
6. **Elasticsearch not running** - Required for Java search service on port 9200
7. **Environment variables not set** - Java backend needs `DB_USERNAME`, `DB_PASSWORD`, `SEARCH_BACKEND`
8. **Only starting one search service** - For failover to work, both services must run
9. **Expecting instant failback** - Circuit breaker waits 60s before testing recovery
10. **Missing re-indexing after failover** - Images uploaded during failover need manual re-indexing

## Key Documentation Files

- [README.md](README.md) - Project overview and quick start
- [DOCKER.md](DOCKER.md) - Docker deployment guide
- [docs/ARCHITECTURE_REFACTORING.md](docs/ARCHITECTURE_REFACTORING.md) - Data directory refactoring details
- [docs/BACKEND_COMPARISON.md](docs/BACKEND_COMPARISON.md) - Java vs Python backend comparison
- [docs/HOW_TO_SWITCH_BACKENDS.md](docs/HOW_TO_SWITCH_BACKENDS.md) - Detailed switching instructions
- [docs/PORTS_AND_ARCHITECTURE.md](docs/PORTS_AND_ARCHITECTURE.md) - Port configuration reference
- [API_TESTING.md](API_TESTING.md) - API testing guide with Postman
- [ELASTICSEARCH_MIGRATION.md](ELASTICSEARCH_MIGRATION.md) - Elasticsearch setup for Java search service