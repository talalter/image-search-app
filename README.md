# 🔍 Semantic Image Search Platform

Enterprise-grade image search application using AI-powered semantic search. Built with microservices architecture, featuring **Java Spring Boot backend**, **Python AI search service** (CLIP + FAISS), **React frontend**, and **PostgreSQL database**.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.12-blue.svg)](https://www.python.org/)
[![React](https://img.shields.io/badge/React-18-61dafb.svg)](https://reactjs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-24-2496ED.svg)](https://www.docker.com/)

## What This App Does

Upload your images and search them using **natural language** - no tags or keywords needed. Ask for "sunset over mountains" or "red sports car" and the AI finds matching images based on semantic meaning.

### Key Features

- **AI-Powered Search**: Uses OpenAI CLIP model to understand image content
- **Lightning Fast**: FAISS vector search across thousands of images in milliseconds
- **Organization**: Create folders and manage image collections
- **Secure**: BCrypt password hashing with session-based authentication
- **Collaboration**: Share folders with other users (view or edit permissions)
- **Bulk Upload**: Drag & drop multiple images with automatic AI processing

## Architecture

**Three-Tier Microservices Architecture** with clean separation of concerns:

```
React Frontend (Port 3000)
        ↓
Java Spring Boot Backend (Port 8080)
        ↓
PostgreSQL Database (Port 5432)
        ↓
Python Search Service (Port 5000)
   ├─→ CLIP (AI Embeddings)
   └─→ FAISS (Vector Search)
```

**Service Responsibilities:**
- **Java Backend**: REST API, business logic, authentication, file management
- **Python Search Service**: AI/ML operations (CLIP embeddings, FAISS vector search)
- **PostgreSQL**: User data, folder metadata, image references, sessions
- **React Frontend**: User interface, API client, state management

> **Note:** A Python FastAPI backend and Java search service (with ONNX and Elasticsearch) implementations are also available as an alternative (see `python-backend/`, `java-search-service/`). Both backends share the same database and search service.

### Technology Stack

**Java Spring Boot Backend (Port 8080)**
- RESTful API with layered architecture (Controller → Service → Repository → Entity)
- Spring Data JPA + Hibernate ORM for database operations
- WebClient for non-blocking microservice communication
- BCrypt password hashing with session-based authentication
- Transaction management with `@Transactional`
- Global exception handling with `@ControllerAdvice`
- Comprehensive test suite (JUnit 5 + Mockito)

**Python Search Service (Port 5000)**
- FastAPI framework with async endpoints
- OpenAI CLIP model for image/text embeddings (512-dimensional vectors)
- FAISS IndexFlatIP for cosine similarity search
- Batch processing to prevent race conditions during concurrent uploads
- Index management (create, search, delete per folder)

**React Frontend (Port 3000)**
- React 18 with modern hooks (useState, useEffect)
- Centralized API client (`utils/api.js`)
- Responsive design with drag-and-drop upload
- Real-time search with similarity scores
- Folder management and sharing UI

**PostgreSQL Database (Port 5432)**
- Normalized schema with foreign key constraints
- Multi-tenant data isolation (user_id on all resources)
- Session management with 12-hour sliding window expiration
- JPA/Hibernate auto-DDL schema generation

## Quick Start

### Prerequisites

- **Java 17+** (JDK)
- **Python 3.12+**
- **Node.js 18+**
- **PostgreSQL 15+**
- **2GB+ RAM** (CLIP model runs on CPU)
- **Docker & Docker Compose** (optional, for containerized deployment)

### Local Development Setup

#### 1. Setup Database

```bash
# Create PostgreSQL database
sudo -u postgres psql
CREATE DATABASE imagesearch;
CREATE USER imageuser WITH PASSWORD 'imagepass123';
GRANT ALL PRIVILEGES ON DATABASE imagesearch TO imageuser;
\q

# OR use the automated script:
./scripts/setup-postgres.sh
```

#### 2. Start Python Search Service

```bash
cd python-search-service
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
python app.py  # Runs on http://localhost:5000
```

**Note:** First run downloads the CLIP model (~1-2 second startup delay).

#### 3. Start Java Spring Boot Backend

```bash
cd java-backend
export DB_USERNAME=imageuser
export DB_PASSWORD=imagepass123
./gradlew bootRun  # Runs on http://localhost:8080

# OR use the convenience script:
./scripts/run-java.sh
```

#### 4. Start React Frontend

```bash
cd frontend
npm install
npm start  # Runs on http://localhost:3000
```

The frontend defaults to the Java backend at `http://localhost:8080`.

### Docker Deployment

**Recommended for production-like environments:**

```bash
# Build and start all services (Java backend + Python search + PostgreSQL + React)
docker-compose up --build

# OR use the existing docker-compose-simple.yml
docker-compose -f docker-compose-simple.yml up --build

# Run in detached mode
docker-compose up -d

# View logs
docker-compose logs -f
docker-compose logs -f java-backend  # Specific service

# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

**Services will be available at:**
- Frontend: http://localhost:3000
- Java Backend: http://localhost:8080
- Python Search Service: http://localhost:5000
- PostgreSQL: localhost:5432

See [DOCKER.md](DOCKER.md) for detailed Docker deployment instructions.

## How to Use

1. **Register**: Create an account at http://localhost:3000
2. **Create Folder**: Click "Manage Folders" → "Create Folder"
3. **Upload Images**: Select folder, drag & drop images (JPG/PNG)
4. **Search**: Enter queries like:
   - "sunset over ocean"
   - "person wearing red shirt"
   - "modern architecture"
5. **Share**: Click "Share Folder" to collaborate with others

## Key Technical Highlights

### Microservices Communication
- **Java → Python**: Backend orchestrates AI service via WebClient (Spring WebFlux)
- **Async HTTP**: Non-blocking communication for embedding/search operations
- **Error Handling**: Graceful degradation with service-specific error responses
- **Endpoint Examples**: `/embed-images`, `/search`, `/create-index`, `/delete-index`

### AI/ML Integration
- **CLIP Model**: OpenAI's vision-language model (ViT-B/32) for semantic understanding
- **Vector Embeddings**: 512-dimensional normalized vectors for images and text
- **FAISS IndexFlatIP**: Inner product search on normalized vectors (equivalent to cosine similarity)
- **Batch Processing**: Sequential embedding generation to prevent CLIP model race conditions
- **Index Management**: One FAISS index per folder in `data/indexes/{userId}/{folderId}.faiss`

### Enterprise Design Patterns
- **Layered Architecture**: Controller → Service → Repository → Entity (clean separation of concerns)
- **DTO Pattern**: Request/Response DTOs separate from JPA entities
- **Dependency Injection**: Spring IoC container manages all components
- **Global Exception Handling**: `@ControllerAdvice` with `@ExceptionHandler` for centralized error responses
- **Transaction Management**: `@Transactional` for ACID guarantees
- **Repository Pattern**: Spring Data JPA repositories with custom query methods

### Security
- **Password Hashing**: BCrypt with salt (Spring Security Crypto)
- **Session Management**: 32-byte random tokens with 12-hour expiration
- **Sliding Window Refresh**: Automatic session extension on activity
- **SQL Injection Prevention**: JPA parameterized queries and named parameters
- **Data Isolation**: User ID validation on all resource access

## Project Structure

```
image-search-app/
├── java-backend/                      # Spring Boot REST API
│   ├── src/main/java/com/imagesearch/
│   │   ├── controller/               # REST endpoints (@RestController)
│   │   │   ├── UserController.java   # Register, login, logout
│   │   │   ├── ImageController.java  # Upload, search, delete
│   │   │   └── FolderController.java # CRUD, sharing operations
│   │   ├── service/                  # Business logic (@Service)
│   │   │   ├── UserService.java      # User management, sessions
│   │   │   ├── ImageService.java     # Image operations, search orchestration
│   │   │   └── FolderService.java    # Folder operations, permissions
│   │   ├── repository/               # JPA repositories
│   │   │   ├── UserRepository.java   # extends JpaRepository<User, Long>
│   │   │   ├── ImageRepository.java
│   │   │   └── FolderRepository.java
│   │   ├── model/
│   │   │   ├── entity/               # JPA entities (@Entity)
│   │   │   └── dto/                  # Request/Response DTOs
│   │   ├── client/                   # External service clients
│   │   │   └── PythonSearchClient.java  # WebClient for search service
│   │   ├── config/                   # Spring configuration
│   │   │   ├── StaticResourceConfig.java  # Serves images from data/uploads/
│   │   │   └── WebClientConfig.java       # WebClient bean
│   │   └── exception/                # Error handling
│   │       └── GlobalExceptionHandler.java
│   ├── src/test/java/                # JUnit 5 + Mockito tests
│   ├── build.gradle                  # Gradle build configuration
│   └── Dockerfile                    # Multi-stage build
│
├── python-search-service/            # Python AI/ML service
│   ├── app.py                        # FastAPI application entry point
│   ├── embedding_service.py          # CLIP model loading and inference
│   ├── search_handler.py             # FAISS index creation and search
│   ├── requirements.txt              # Python dependencies
│   └── Dockerfile                    # Optimized for CLIP model
│
├── frontend/                         # React SPA
│   ├── src/
│   │   ├── App.jsx                   # Main component, routing logic
│   │   ├── components/               # UI components
│   │   │   ├── Login.jsx
│   │   │   ├── Register.jsx
│   │   │   ├── SearchImages.jsx
│   │   │   ├── UploadImages.jsx
│   │   │   └── FolderSharing.jsx
│   │   ├── utils/
│   │   │   └── api.js                # Centralized API client
│   │   └── styles/                   # CSS modules
│   ├── package.json
│   └── Dockerfile                    # Nginx production build
│
├── data/                             # Persistent data (gitignored)
│   ├── uploads/                      # Image files
│   │   └── {userId}/{folderId}/
│   └── indexes/                      # FAISS indexes
│       └── {userId}/{folderId}.faiss
│
├── scripts/                          # Utility scripts
│   ├── run-java.sh                   # Start Java backend with env vars
│   ├── setup-postgres.sh             # Initialize database
│   └── test-api.sh                   # Integration tests
│
├── docker-compose.yml                # Full stack orchestration
├── docker-compose-simple.yml         # Alternative compose file
└── CLAUDE.md                         # AI assistant instructions
```

## Database Schema

**Core Tables:**
- `users` - User accounts with BCrypt passwords
- `folders` - Image collections with ownership
- `images` - Metadata linking to filesystem
- `sessions` - Authentication tokens
- `folder_shares` - Collaboration permissions

**Key Relationships:**
- Users own folders and images (1:N)
- Folders contain images (1:N)
- Users share folders with permissions (N:M)

## Testing

### Unit Tests (Java Backend)

```bash
cd java-backend

# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests UserControllerTest

# Run with coverage report
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html

# Run tests in continuous mode
./gradlew test --continuous
```

**Test Coverage:**
- Controllers: Request/response validation, authentication
- Services: Business logic, error handling, transaction management
- Repositories: Custom query methods
- Uses H2 in-memory database for isolation

### Integration/API Testing

**Automated Testing (Recommended):**
```bash
# Full workflow test (register → login → upload → search → share → delete)
./scripts/test-api.sh java

# Or manually with curl
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "testpass123"}'
```

**Manual Testing with Postman:**
- Import `Image_Search_API.postman_collection.json` into Postman
- Collection includes all endpoints with example payloads
- See [API_TESTING.md](API_TESTING.md) for detailed testing guide
- See [QUICK_TEST_REFERENCE.md](QUICK_TEST_REFERENCE.md) for curl examples

## Docker Deployment

### Quick Start with Docker

```bash
# Start all services (Java + Python Search + PostgreSQL + React)
docker-compose up --build

# Run in detached mode
docker-compose up -d

# View logs
docker-compose logs -f
docker-compose logs -f java-backend  # Specific service

# Stop all services
docker-compose down

# Clean restart (remove volumes)
docker-compose down -v
docker-compose up --build
```

### Docker Architecture

**Services:**
- `java-backend`: Spring Boot app (port 8080)
- `python-search-service`: CLIP + FAISS service (port 5000)
- `postgres`: PostgreSQL database (port 5432)
- `frontend`: React app served by Nginx (port 3000)

**Volumes:**
- `app-data`: Persistent storage for `data/uploads/` and `data/indexes/`
- `postgres-data`: PostgreSQL database files

**Networks:**
- All services on `app-network` bridge network
- Inter-service communication via service names (e.g., `http://java-backend:8080`)

### Docker Images

```bash
# Build individual services
docker build -t image-search-java-backend ./java-backend
docker build -t image-search-python-service ./python-search-service
docker build -t image-search-frontend ./frontend

# Multi-platform builds (optional)
docker buildx build --platform linux/amd64,linux/arm64 -t image-search-java-backend ./java-backend
```

See [DOCKER.md](DOCKER.md) for detailed deployment instructions, troubleshooting, and production considerations.

## API Documentation

### Main Endpoints

**User Management**
- `POST /api/users/register` - Create account
- `POST /api/users/login` - Authenticate
- `POST /api/users/logout` - End session

**Image Operations**
- `POST /api/images/upload` - Upload images (multipart/form-data)
- `GET /api/images/search?query=sunset&token=xxx` - Semantic search

**Folder Management**
- `GET /api/folders?token=xxx` - List accessible folders
- `POST /api/folders/share` - Share with other users
- `DELETE /api/folders` - Delete folder + FAISS index

## Learning Highlights

This project demonstrates:

- **Full-stack development** across Java, Python, and JavaScript
- **Microservices architecture** with HTTP communication
- **AI/ML integration** with production models
- **Database design** with PostgreSQL and JPA
- **RESTful API design** following best practices
- **Test-driven development** with comprehensive test suites
- **Docker containerization** for reproducible deployments
- **Git workflow** with meaningful commits and branches



## License

MIT License - See [LICENSE](LICENSE) for details

## Author

**Tal Alter**
- GitHub: [@talalter](https://github.com/talalter)
- LinkedIn: [linkedin.com/in/tal-alter](https://linkedin.com/in/tal-alter)
- Email: talalter95900@gmail.com

---

**If this project helped you learn something new, please consider starring it!**

## Acknowledgments

Built with:
- [OpenAI CLIP](https://github.com/openai/CLIP) - Image understanding
- [FAISS](https://github.com/facebookresearch/faiss) - Similarity search
- [Spring Boot](https://spring.io/projects/spring-boot) - Java framework
- [FastAPI](https://fastapi.tiangolo.com/) - Python web framework
- [React](https://reactjs.org/) - UI library
