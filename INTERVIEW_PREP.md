# Applied Materials Interview Prep - Backend Developer

## 1. Tell me about a server-side system you built end-to-end

**Answer:** I built a semantic image search platform with microservices architecture. The system has three tiers:

- **Java Spring Boot backend** (REST API, business logic, PostgreSQL integration)
- **Python AI service** (CLIP embeddings, FAISS vector search)
- **React frontend** with drag-and-drop image upload

Key components I implemented:
- RESTful API with layered architecture (Controller → Service → Repository)
- Session-based authentication with BCrypt password hashing
- Microservice communication using WebClient for async HTTP calls
- FAISS index management for similarity search across thousands of images
- Docker containerization with multi-stage builds

The system handles concurrent uploads, batch processing to prevent race conditions, and serves search results in milliseconds using vector similarity.

## 2. How do you design a scalable distributed system?

**Answer:** Based on my image search app:

**Horizontal Scaling:**
- Stateless backend services behind load balancer
- Database connection pooling to handle concurrent requests
- Separate AI service that can scale independently

**Async Processing:**
- Batch embedding generation to prevent concurrency issues
- Non-blocking HTTP calls with WebClient/async frameworks

**Data Partitioning:**
- Per-user, per-folder FAISS indexes for isolation
- Hierarchical storage: `data/uploads/{userId}/{folderId}/`

**Caching Strategy:**
- CLIP model loaded once in memory
- Session tokens cached in database with 12-hour expiration

**Monitoring:**
- Structured logging across services
- Health check endpoints for orchestration

## 3. What design patterns have you used in production?

**Answer:**

**Repository Pattern:** JPA repositories abstract database access, making it easy to switch between PostgreSQL and H2 for testing.

**DTO Pattern:** Separate API contracts from domain entities. Example: `UploadResponse` DTO maps to multiple `Image` entities.

**Service Layer Pattern:** Business logic isolated in `@Service` classes with `@Transactional` boundaries.

**Client Pattern:** `PythonSearchClient` encapsulates microservice communication with error handling and retry logic.

**Factory Pattern:** FAISS index creation logic centralized in search service.

## 4. Dockerize an application from scratch - walk me through it

**Answer:** From my project:

```dockerfile
# Multi-stage build for Java backend
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle build -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Key decisions:**
- Multi-stage build reduces image size (build dependencies not in final image)
- `.dockerignore` excludes `build/`, `.git/`, `*.log`
- Named volumes for persistent data: `app-data:/app/data`
- Health checks in docker-compose for orchestration
- Environment variables for config (DB credentials, service URLs)

## 5. How do you integrate Python components into a Java backend?

**Answer:** My architecture demonstrates this:

**Communication:** Java backend calls Python search service via HTTP REST API using Spring WebClient (non-blocking).

**Data Flow:**
1. Java receives image upload
2. Saves to filesystem and PostgreSQL
3. Calls Python service: `POST /embed-images` with image paths
4. Python returns embeddings, stores FAISS index
5. Java confirms success to frontend

**Error Handling:**
- Timeout configuration (30s for embedding generation)
- Graceful degradation if AI service is down
- Structured error responses across language boundaries

**Shared Contracts:**
- JSON API with snake_case naming convention
- Shared data directory mounted in both containers
- Consistent user/folder ID formats

## 6. Debugging non-reproducible issues

**Answer:**

**Systematic approach:**

1. **Gather context:** Logs, timestamps, user actions, environment
2. **Reproduce locally:** Try same data, same sequence of operations
3. **Add instrumentation:** Strategic logging at decision points
4. **Check concurrency:** Race conditions? Use batch processing or locks
5. **Database state:** Inspect actual data, not assumptions
6. **Network issues:** Timeouts, retries, partial failures

**Example from my project:** Inconsistent search results were caused by concurrent uploads creating incomplete FAISS indexes. Fixed with batch queue processing.

## 7. CI/CD pipeline design

**Answer:**

```yaml
# GitHub Actions example
Build → Test → Containerize → Deploy

Stages:
1. Checkout code
2. Build: ./gradlew build (includes unit tests)
3. Test: ./gradlew test jacocoTestReport
4. Docker: docker build --tag app:$VERSION
5. Push: docker push registry/app:$VERSION
6. Deploy: kubectl apply -f k8s/ (rolling update)
7. Health check: curl /actuator/health
8. Rollback: if health fails, revert to previous version
```

**Key practices:**
- Separate staging and production environments
- Automated rollback on failure
- Artifact versioning with git commit SHA
- Secrets management (not in repo)

## 8. High-resolution image processing pipeline design

**Answer:** For GB-scale wafer images:

**Ingestion:**
- Stream processing (don't load entire image in memory)
- Chunked upload with multipart/form-data
- Async queue (RabbitMQ/Kafka) for decoupling upload from processing

**Storage:**
- Object storage (S3/MinIO) for raw images
- Metadata in PostgreSQL (location, size, timestamp)
- Hierarchical partitioning by date/tool/wafer

**Processing:**
- Dedicated worker pool for CPU-intensive tasks
- Batch processing to control memory usage
- Intermediate results cached

**Classification:**
- Extract features → Vector embeddings
- FAISS/ANN for similarity search
- Results stored in DB with image references

**Monitoring:**
- Track pipeline stages (ingested → processed → classified)
- Alert on failures or backlog buildup

## 9. Agentic coding with LLMs - when useful vs dangerous?

**Answer:**

**Useful scenarios:**
- Boilerplate code generation (DTOs, REST endpoints)
- Test case generation from specifications
- Documentation from code comments
- Refactoring suggestions with safety checks

**Dangerous scenarios:**
- Security-sensitive code (authentication, encryption)
- Performance-critical algorithms (profiling needed)
- Database migrations (data loss risk)
- Blind copy-paste without understanding

**My guidelines:**
1. **Always review generated code** - understand before merging
2. **Test thoroughly** - unit tests for AI-generated functions
3. **Security audit** - scan for SQL injection, XSS, etc.
4. **Performance validation** - profile if it's in hot path
5. **Use for learning** - ask "why this approach?" to improve

**Example:** Used LLM to generate JUnit test templates, but manually filled business logic and edge cases. Result: 80% faster test coverage, but with human verification.

## 10. Full ownership example

**Answer:** I designed and implemented the entire folder sharing feature:

**Planning:**
- Analyzed requirements: view vs edit permissions
- Designed database schema: `folder_shares` table with foreign keys
- Defined REST API contracts

**Implementation:**
- Backend: Share/unshare endpoints with permission validation
- Frontend: Share modal with user search
- Security: Prevent privilege escalation, validate ownership

**Testing:**
- Unit tests for permission logic
- Integration tests for API workflows
- Manual testing with multiple users

**Deployment:**
- Dockerized and deployed to staging
- Monitored logs for errors
- Iterated based on feedback

**Outcome:** Feature used by multiple users for collaboration, zero security incidents.
