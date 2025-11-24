# 🔍 Semantic Image Search Platform

Enterprise-grade image search application using AI-powered semantic search. Built with microservices architecture, featuring Java Spring Boot, Python AI services (CLIP + FAISS), React frontend, and PostgreSQL database.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.12-blue.svg)](https://www.python.org/)
[![React](https://img.shields.io/badge/React-18-61dafb.svg)](https://reactjs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791.svg)](https://www.postgresql.org/)

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

**Microservices Architecture** demonstrating production-ready design patterns with **two interchangeable backend implementations**:

```
React Frontend (Port 3000)
        ↓
Backend ← Choose ONE:
   ├─→ Java Spring Boot (Port 8080)
   └─→ Python FastAPI (Port 9999)
        │
        ├─→ PostgreSQL Database (Port 5432)
        └─→ Python Search Service (Port 5000)
               ├─→ CLIP (AI Embeddings)
               └─→ FAISS (Vector Search)
```

Both backends provide **identical REST API functionality** - choose based on your preference. Both use the same PostgreSQL database and delegate AI/ML operations to the dedicated search service.

### Technology Stack

**Backend Options (Choose One):**

**Java Spring Boot Backend (Port 8080)**
- RESTful API design with layered architecture
- Spring Data JPA + Hibernate ORM
- WebClient for microservice communication
- Transaction management and global exception handling
- Comprehensive test suite (JUnit + Mockito)

**Python FastAPI Backend (Port 9999)**
- Modern async FastAPI framework
- Direct psycopg2 for PostgreSQL operations
- HTTP client for search service communication
- Structured exception handling with custom error types
- Comprehensive test suite (pytest + mocking)

**Search Service (Python FastAPI - Port 5000)**
- OpenAI CLIP model for image embeddings
- FAISS for high-performance vector similarity search
- Batch processing to prevent concurrency issues
- Async API for non-blocking operations

**Frontend (React)**
- Modern React 18 with hooks
- Responsive design with drag-and-drop upload
- Real-time search results with similarity scores

**Database (PostgreSQL)**
- Normalized schema with foreign key constraints
- Multi-tenant data isolation
- Session management with sliding window expiration

## Quick Start

### Prerequisites

- PostgreSQL 15+
- Python 3.12+
- Node.js 18+
- **For Java Backend:** Java 17+
- 2GB+ RAM (CLIP model runs on CPU)

### 1. Setup Database

```bash
# Create PostgreSQL database
sudo -u postgres psql
CREATE DATABASE imagesearch;
CREATE USER imageuser WITH PASSWORD 'yourpassword';
GRANT ALL PRIVILEGES ON DATABASE imagesearch TO imageuser;
\q
```

### 2. Start Python Search Service

```bash
cd search-service
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
python app.py  # Runs on http://localhost:5000
```

### 3. Start Backend (Choose ONE)

#### Option A: Java Spring Boot Backend

```bash
cd java-backend
export DB_USERNAME=imageuser
export DB_PASSWORD=yourpassword
./gradlew bootRun  # Runs on http://localhost:8080
```

#### Option B: Python FastAPI Backend

```bash
cd python-backend
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r ../requirements.txt
export DB_USERNAME=imageuser
export DB_PASSWORD=yourpassword
uvicorn api:app --host 0.0.0.0 --port 9999  # Runs on http://localhost:9999
```

**Note:** Configure frontend to point to the correct backend port (8080 for Java, 9999 for Python).

### 4. Start React Frontend

```bash
cd frontend
npm install
# For Java backend:
REACT_APP_API_URL=http://localhost:8080 npm start
# For Python backend:
REACT_APP_API_URL=http://localhost:9999 npm start
# Opens http://localhost:3000
```

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
- Java backend orchestrates Python AI service via HTTP
- Async communication with WebClient (non-blocking)
- Graceful error handling across service boundaries

### AI/ML Integration
- **CLIP Model**: Converts images and text into 512-dimensional vectors
- **FAISS IndexFlatIP**: Performs cosine similarity search
- **Batch Processing**: Prevents race conditions during concurrent uploads

### Enterprise Patterns
- **Layered Architecture**: Controller → Service → Repository → Entity
- **DTO Pattern**: Separate API contracts from domain models
- **Global Exception Handling**: Centralized @ControllerAdvice
- **Transaction Management**: @Transactional for data consistency

### Security
- BCrypt password hashing (salt + iterations)
- Session tokens (32-byte random, 12-hour expiration)
- Sliding window session refresh
- SQL injection prevention via JPA parameterized queries

## Project Structure

```
image-search-app/
├── java-backend/              # Spring Boot application
│   ├── src/main/java/com/imagesearch/
│   │   ├── controller/       # REST endpoints
│   │   ├── service/          # Business logic
│   │   ├── repository/       # JPA repositories
│   │   ├── model/            # Entities & DTOs
│   │   └── client/           # Microservice clients
│   └── src/test/             # JUnit tests
│
├── search-service/            # Python FastAPI service
│   ├── app.py                # Main application
│   ├── embedding_service.py  # CLIP model
│   └── search_handler.py     # FAISS operations
│
├── frontend/                  # React application
│   └── src/
│       ├── components/       # UI components
│       └── utils/            # API client
│
└── data/
    ├── uploads/              # Image storage
    └── indexes/              # FAISS indexes
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

```bash
# Java backend tests
cd java-backend
./gradlew test

# Python service tests
cd python-backend
pytest tests/
```

## Docker Deployment

```bash
# Java backend with search service
docker-compose -f docker-compose.java.yml up

# Or Python backend (alternative implementation)
docker-compose -f docker-compose.python.yml up
```

See [DOCKER.md](DOCKER.md) for detailed deployment instructions.

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
