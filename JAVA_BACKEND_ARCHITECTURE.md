# Java Backend - Comprehensive Architecture Documentation

**Generated:** 2025-12-01
**Version:** 1.0
**Technology:** Spring Boot 3.2, Java 17, PostgreSQL 15

---

## Table of Contents

1. [Overview](#overview)
2. [Class Organization](#class-organization)
3. [Entity Lifecycle](#entity-lifecycle)
4. [Request Flow Analysis](#request-flow-analysis)
5. [Spring Beans & Dependency Injection](#spring-beans--dependency-injection)
6. [Object Instantiation Patterns](#object-instantiation-patterns)
7. [Configuration Files](#configuration-files)
8. [Background Tasks & Async Operations](#background-tasks--async-operations)
9. [Error Handling & Resilience](#error-handling--resilience)
10. [Security Implementation](#security-implementation)
11. [Architectural Patterns](#architectural-patterns)

---

## Overview

The Java backend is a **Spring Boot microservices application** that orchestrates image search functionality by:
- Managing user authentication and authorization
- Handling image uploads and metadata storage
- Delegating AI/ML operations to Python search service
- Coordinating folder sharing and permissions

**Key Statistics:**
- **51 total classes** across 7 packages
- **5 JPA entities** with PostgreSQL persistence
- **4 REST controllers** exposing RESTful API
- **5 service classes** implementing business logic
- **2 search client implementations** (Python/Java backends)
- **Singleton pattern** for all Spring-managed beans

---

## Class Organization

### Package Structure

```
com.imagesearch/
├── ImageSearchApplication.java          [Main Entry Point]
├── config/                              [6 Configuration Classes]
│   ├── AsyncConfig.java
│   ├── CorsConfig.java
│   ├── DotenvConfig.java
│   ├── SearchBackendConfig.java
│   ├── StaticResourceConfig.java
│   └── WebClientConfig.java
├── controller/                          [4 REST Controllers]
│   ├── FolderController.java
│   ├── HealthController.java
│   ├── ImageController.java
│   └── UserController.java
├── service/                             [5 Service Classes]
│   ├── FolderService.java
│   ├── ImageService.java
│   ├── SearchService.java
│   ├── SessionService.java
│   └── UserService.java
├── repository/                          [5 JPA Repositories]
│   ├── FolderRepository.java
│   ├── FolderShareRepository.java
│   ├── ImageRepository.java
│   ├── SessionRepository.java
│   └── UserRepository.java
├── model/
│   ├── entity/                          [5 JPA Entities]
│   │   ├── Folder.java
│   │   ├── FolderShare.java
│   │   ├── Image.java
│   │   ├── Session.java
│   │   └── User.java
│   └── dto/                             [13 DTOs]
│       ├── request/
│       │   ├── DeleteFoldersRequest.java
│       │   ├── LoginRequest.java
│       │   ├── RegisterRequest.java
│       │   ├── SearchRequest.java
│       │   └── ShareFolderRequest.java
│       └── response/
│           ├── ErrorResponse.java
│           ├── FolderResponse.java
│           ├── LoginResponse.java
│           ├── MessageResponse.java
│           ├── RegisterResponse.java
│           ├── SearchResponse.java
│           ├── SearchServiceResponse.java
│           └── UploadResponse.java
├── client/                              [7 Client Classes]
│   ├── SearchClient.java                [Interface]
│   ├── PythonSearchClientImpl.java
│   ├── JavaSearchClientImpl.java
│   └── dto/
│       ├── EmbedImagesRequest.java
│       ├── SearchServiceRequest.java
│       ├── SearchServiceResponse.java
│       └── SearchResult.java
└── exception/                           [7 Exception Classes]
    ├── GlobalExceptionHandler.java
    ├── BadRequestException.java
    ├── DuplicateResourceException.java
    ├── ForbiddenException.java
    ├── ResourceNotFoundException.java
    └── UnauthorizedException.java
```

---

## Entity Lifecycle

### JPA Entities and Relationships

| Entity | Primary Key | Relationships | Lifecycle Hooks | Cascade Behavior |
|--------|-------------|---------------|-----------------|------------------|
| **User** | `id` (Long) | 1:N → Folder<br>1:N → Session<br>1:N → FolderShare | `@PrePersist` sets `createdAt` | Sessions cascade delete on user deletion |
| **Folder** | `id` (Long) | N:1 → User (LAZY)<br>1:N → Image<br>1:N → FolderShare | `@PrePersist` sets `createdAt` | Images cascade delete on folder deletion |
| **Image** | `id` (Long) | N:1 → User (LAZY)<br>N:1 → Folder (LAZY) | `@PrePersist` sets `uploadedAt` | None (manual cleanup) |
| **Session** | `token` (String) | N:1 → User | `@PrePersist` sets `createdAt`<br>`@PreUpdate` updates `lastSeen` | None |
| **FolderShare** | `id` (Long) | N:1 → Folder<br>N:1 → User (owner)<br>N:1 → User (sharedWith) | `@PrePersist` sets `createdAt` | None |

### Entity Constraints

**User:**
- `username` - UNIQUE, NOT NULL
- `password` - BCrypt hashed, NOT NULL
- Composite unique: N/A

**Folder:**
- Composite unique: `(user_id, folder_name)`
- Prevents duplicate folder names per user

**FolderShare:**
- Composite unique: `(folder_id, shared_with_user_id)`
- Prevents duplicate sharing of same folder to same user

**Image:**
- `filepath` - NOT NULL, relative path
- No unique constraints (multiple images can have same filename in different folders)

**Session:**
- `token` - PRIMARY KEY (UUID format)
- `expires_at` - Indexed for cleanup queries

---

## Request Flow Analysis

### 1. Upload Flow (POST /api/images/upload)

```
┌─────────────────────────────────────────────────────────────────┐
│ HTTP Request (multipart/form-data)                             │
│ - token: "abc123..."                                            │
│ - folderName: "Vacation Photos"                                │
│ - files: [photo1.jpg, photo2.jpg, photo3.jpg]                  │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ ImageController.uploadImages()                                  │
│ - Validates multipart request                                   │
│ - Extracts parameters                                           │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ SessionService.validateTokenAndGetUserId(token)                 │
│ - Query: SELECT * FROM sessions WHERE token = ? AND expires_at >│
│ - Update lastSeen timestamp                                     │
│ - Return userId                                                 │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ ImageService.uploadImages(userId, folderName, files)            │
│                                                                 │
│ Step 1: Get or Create Folder                                   │
│   FolderService.createOrGetFolder(userId, folderName)           │
│   ├─ Query: SELECT * FROM folders WHERE user_id = ? AND        │
│   │          folder_name = ?                                    │
│   ├─ If exists: return folder                                  │
│   └─ If not:                                                   │
│       ├─ INSERT INTO folders (user_id, folder_name, created_at) │
│       └─ SearchClient.createIndex(userId, folderId)            │
│           └─ HTTP POST to Python service /api/create-index     │
│                                                                 │
│ Step 2: Save Files to Filesystem                               │
│   For each file:                                               │
│   ├─ Validate file type (.jpg, .jpeg, .png)                    │
│   ├─ Sanitize filename (remove path traversal)                 │
│   ├─ Create directory: data/uploads/{userId}/{folderId}/       │
│   ├─ Write file to disk                                        │
│   └─ Generate filepath: "images/{userId}/{folderId}/{filename}"│
│                                                                 │
│ Step 3: Save Metadata to Database                              │
│   For each file:                                               │
│   └─ INSERT INTO images (user_id, folder_id, filepath,         │
│                          uploaded_at)                           │
│                                                                 │
│ Step 4: Trigger Async Embedding (non-blocking)                 │
│   @Async embedImagesInBatches()                                │
│   ├─ Split into 50-image batches                               │
│   ├─ For each batch:                                           │
│   │   ├─ Create EmbedImagesRequest                             │
│   │   ├─ POST to Python service /api/embed-images              │
│   │   │   └─ Python generates CLIP embeddings                  │
│   │   │   └─ Python adds to FAISS index                        │
│   │   └─ Sleep 1 second (rate limiting)                        │
│   └─ Log completion                                            │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ Return UploadResponse                                           │
│ {                                                               │
│   "message": "Successfully uploaded 3 images",                  │
│   "folder_id": 456,                                             │
│   "uploaded_count": 3                                           │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

**Timing:**
- Upload returns **immediately** (~100-500ms)
- Embedding requests sent **asynchronously** to Python search service in background thread pool
- Python search service performs actual CLIP embedding and FAISS indexing

**Key Points:**
- `@Transactional` ensures all database writes succeed or none
- File validation prevents security vulnerabilities (path traversal, malware)
- Async HTTP requests to search service allow user to continue without waiting
- Circuit breaker on search client prevents cascade failures
- **Java backend orchestrates** - Python search service executes AI operations

---

### 2. Search Flow (GET /api/images/search)

```
┌─────────────────────────────────────────────────────────────────┐
│ HTTP Request                                                    │
│ GET /api/images/search?token=abc&query=sunset&folder_ids=1,2,5 │
│                        &top_k=5                                 │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ ImageController.searchImages()                                  │
│ - Parse comma-separated folder_ids → [1, 2, 5]                 │
│ - Validate token                                                │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ SearchService.searchImages(userId, query, folderIds, topK)      │
│                                                                 │
│ Step 1: Determine Accessible Folders                           │
│   If folderIds == null:                                        │
│   ├─ Query: SELECT f.* FROM folders f                          │
│   │         LEFT JOIN folder_shares fs ON f.id = fs.folder_id  │
│   │         WHERE f.user_id = ? OR fs.shared_with_user_id = ?  │
│   │   (Returns all owned + shared folders)                     │
│   └─ Use all accessible folders                                │
│   Else:                                                        │
│   └─ For each folder_id:                                       │
│       └─ FolderService.checkFolderAccess(userId, folderId)     │
│           ├─ Query: SELECT * FROM folders WHERE id = ?         │
│           ├─ Check if user_id == userId (owns)                 │
│           ├─ If not, check folder_shares table                 │
│           └─ Throw ForbiddenException if no access             │
│                                                                 │
│ Step 2: Build Folder Ownership Map                             │
│   folderOwnerMap = { folder_id → owner_user_id }               │
│   (Needed for Python service to find correct FAISS index)      │
│                                                                 │
│ Step 3: Call Python Search Service                             │
│   SearchClient.search(SearchServiceRequest)                    │
│   └─ POST to http://localhost:5000/api/search                  │
│       Request: {                                               │
│         "user_id": 123,                                        │
│         "query": "sunset beach",                               │
│         "folder_ids": [1, 2, 5],                               │
│         "folder_owner_map": {"1": 123, "2": 123, "5": 124},    │
│         "top_k": 5                                             │
│       }                                                        │
│   └─ Python service:                                           │
│       ├─ Embeds query with CLIP                                │
│       ├─ Searches FAISS indexes (uses owner map for paths)     │
│       └─ Returns: [                                            │
│             {image_id: 42, score: 0.95, folder_id: 1},         │
│             {image_id: 7, score: 0.91, folder_id: 5},          │
│             ...                                                │
│           ]                                                    │
│                                                                 │
│ Step 4: Enrich Results with Metadata                           │
│   For each result:                                             │
│   ├─ ImageService.getImageById(image_id)                       │
│   │   └─ Query: SELECT * FROM images WHERE id = ?             │
│   ├─ Extract filepath: "images/123/456/photo.jpg"              │
│   └─ Build imageUrl: http://localhost:8080/images/123/456/     │
│                       photo.jpg                                │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ Return SearchResponse                                           │
│ {                                                               │
│   "results": [                                                  │
│     {                                                           │
│       "image_url": "http://localhost:8080/images/123/456/1.jpg",│
│       "similarity": 0.95                                        │
│     },                                                          │
│     ...                                                         │
│   ]                                                             │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

**Timing:**
- Authorization check: ~50ms
- Python search service call: ~200-500ms
- Database enrichment: ~50ms
- **Total: ~300-600ms**

**Key Points:**
- Authorization enforced at **two levels**: folder access + ownership
- `folder_owner_map` enables cross-user searches for shared folders
- Circuit breaker returns empty results if Python service fails
- Image URLs are generated (not stored in database)

---

### 3. Login Flow (POST /api/users/login)

```
┌─────────────────────────────────────────────────────────────────┐
│ HTTP Request                                                    │
│ POST /api/users/login                                           │
│ {                                                               │
│   "username": "alice",                                          │
│   "password": "secret123"                                       │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ UserController.login(@Valid LoginRequest request)               │
│ - @Valid triggers bean validation                              │
│ - @NotBlank on username/password                                │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ UserService.login(username, password)                           │
│                                                                 │
│ Step 1: Find User                                              │
│   Query: SELECT * FROM users WHERE username = ?                │
│   If not found: throw UnauthorizedException("Invalid...")      │
│                                                                 │
│ Step 2: Verify Password                                        │
│   BCryptPasswordEncoder.matches(plaintext, hashedPassword)     │
│   If mismatch: throw UnauthorizedException("Invalid...")       │
│                                                                 │
│ Step 3: Create Session                                         │
│   SessionService.createSession(user)                           │
│   ├─ Generate token: UUID.randomUUID().toString()              │
│   ├─ Calculate expiry: now + 12 hours                          │
│   └─ INSERT INTO sessions (token, user_id, expires_at,         │
│                            created_at, last_seen)               │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ Return LoginResponse                                            │
│ {                                                               │
│   "token": "a1b2c3d4-e5f6-...",                                 │
│   "user_id": 123,                                               │
│   "username": "alice",                                          │
│   "message": "Login successful"                                 │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

**Timing:** ~100-200ms

**Security:**
- Passwords never logged or returned in responses
- BCrypt hashing prevents rainbow table attacks
- Session tokens are cryptographically random (UUID v4)
- 12-hour sliding window expiration

---

### 4. Delete Account Flow (DELETE /api/users/delete)

```
┌─────────────────────────────────────────────────────────────────┐
│ HTTP Request                                                    │
│ DELETE /api/users/delete                                        │
│ { "token": "abc123..." }                                        │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ UserService.deleteAccount(userId)                               │
│                                                                 │
│ Step 1: Delete Search Indices (BEFORE database deletion)       │
│   Query: SELECT * FROM folders WHERE user_id = ?               │
│   For each folder:                                             │
│   └─ SearchClient.deleteIndex(userId, folderId)                │
│       └─ DELETE to Python service /api/delete-index/{}/{id}    │
│                                                                 │
│ Step 2: Invalidate All Sessions                                │
│   SessionService.invalidateAllUserSessions(userId)             │
│   └─ DELETE FROM sessions WHERE user_id = ?                    │
│                                                                 │
│ Step 3: Delete User (cascades to related entities)             │
│   UserRepository.delete(user)                                  │
│   └─ JPA cascades:                                             │
│       ├─ DELETE FROM sessions WHERE user_id = ?                │
│       ├─ DELETE FROM images WHERE user_id = ?                  │
│       ├─ DELETE FROM folder_shares WHERE owner_id = ? OR       │
│       │                  shared_with_user_id = ?                │
│       ├─ DELETE FROM folders WHERE user_id = ?                 │
│       └─ DELETE FROM users WHERE id = ?                        │
│                                                                 │
│ Step 4: Delete Physical Files (best-effort, no throw)          │
│   Path: data/uploads/{userId}/                                 │
│   ├─ Walk directory tree                                       │
│   └─ Delete all files and subdirectories                       │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ Return MessageResponse                                          │
│ { "message": "Account deleted successfully" }                   │
└─────────────────────────────────────────────────────────────────┘
```

**Critical Order:**
1. **FAISS indices deleted first** (requires folder query)
2. **Sessions invalidated** (logout everywhere)
3. **Database cascade delete** (ensures referential integrity)
4. **Physical files deleted last** (best-effort, soft-fails)

**Timing:** ~500ms - 2s (depending on file count)

---

## Spring Beans & Dependency Injection

### Singleton Beans (Created at Startup)

| Bean Type | Bean Name | Scope | Created When | Dependencies |
|-----------|-----------|-------|--------------|--------------|
| **Configuration** | `WebClientConfig` | Singleton | Startup | None |
| | `CorsConfig` | Singleton | Startup | None |
| | `AsyncConfig` | Singleton | Startup | None |
| | `StaticResourceConfig` | Singleton | Startup | None |
| **Repository** | `UserRepository` | Singleton | Startup (lazy) | DataSource |
| | `FolderRepository` | Singleton | Startup (lazy) | DataSource |
| | `ImageRepository` | Singleton | Startup (lazy) | DataSource |
| | `SessionRepository` | Singleton | Startup (lazy) | DataSource |
| | `FolderShareRepository` | Singleton | Startup (lazy) | DataSource |
| **Service** | `UserService` | Singleton | Startup | UserRepository, SessionService, FolderRepository, SearchClient |
| | `SessionService` | Singleton | Startup | SessionRepository |
| | `FolderService` | Singleton | Startup | FolderRepository, FolderShareRepository, ImageRepository, UserRepository, SearchClient |
| | `ImageService` | Singleton | Startup | ImageRepository, UserRepository, FolderService, SearchClient |
| | `SearchService` | Singleton | Startup | SearchClient, FolderService, ImageService |
| **Client** | `PythonSearchClientImpl` | Singleton | Startup (conditional) | WebClient |
| | `JavaSearchClientImpl` | Singleton | Startup (conditional) | WebClient |
| **Controller** | `UserController` | Singleton | Startup | UserService, SessionService |
| | `FolderController` | Singleton | Startup | FolderService, SessionService |
| | `ImageController` | Singleton | Startup | ImageService, SearchService, SessionService |
| | `HealthController` | Singleton | Startup | None |
| **Exception Handler** | `GlobalExceptionHandler` | Singleton | Startup | None |

### Bean Factory Methods

**WebClientConfig:**
```java
@Bean
public WebClient.Builder webClientBuilder() {
    return WebClient.builder()
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
}
```

**AsyncConfig:**
```java
@Bean(name = "taskExecutor")
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("async-embedding-");
    executor.initialize();
    return executor;
}
```

**CorsConfig:**
```java
@Bean
public CorsFilter corsFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:3001"));
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(Arrays.asList("*"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return new CorsFilter(source);
}
```

### Conditional Beans

**SearchClient Implementations (only ONE loaded at runtime):**

```java
@Component
@ConditionalOnProperty(name = "search.backend.type", havingValue = "python", matchIfMissing = true)
public class PythonSearchClientImpl implements SearchClient {
    // Loaded when SEARCH_BACKEND=python (default)
}

@Component
@ConditionalOnProperty(name = "search.backend.type", havingValue = "java")
public class JavaSearchClientImpl implements SearchClient {
    // Loaded when SEARCH_BACKEND=java
}
```

**Environment Property Resolution:**
1. System environment variables (highest priority)
2. .env file (via DotenvConfig)
3. application.yml (default values)

---

## Object Instantiation Patterns

### Startup Sequence

```
1. Spring Boot Main Method
   └─ ImageSearchApplication.main()
       └─ SpringApplication.run()

2. ApplicationContextInitializer Phase
   └─ DotenvConfig.initialize()
       ├─ Load .env file from project root
       ├─ Convert to system properties
       └─ Override application.yml values

3. Bean Definition Phase
   └─ Component scan: com.imagesearch.*
       ├─ Register @Configuration classes
       ├─ Register @Component/@Service/@Repository classes
       └─ Register @RestController classes

4. Bean Creation Phase (Dependency Order)
   └─ Create configuration beans
       ├─ WebClientConfig → WebClient.Builder bean
       ├─ CorsConfig → CorsFilter bean
       └─ AsyncConfig → TaskExecutor bean
   └─ Create repositories (JPA proxies)
   └─ Create ONE search client (@ConditionalOnProperty)
       └─ PythonSearchClientImpl OR JavaSearchClientImpl
   └─ Create services (constructor injection)
       └─ Dependencies resolved via constructor parameters
   └─ Create controllers (constructor injection)
   └─ Create exception handler

5. Post-Initialization
   └─ SearchBackendConfig.logActiveBackend()
       └─ Logs which search client is active

6. Ready to Accept Requests
   └─ Embedded Tomcat starts on port 8080
```

### Per-Request Lifecycle

```
HTTP Request arrives
├─ DispatcherServlet (singleton, reused)
├─ Match @RequestMapping
├─ Call controller method (singleton, reused)
│   └─ Parameters injected: @RequestBody, @RequestParam, @PathVariable
├─ Controller calls service (singleton, reused)
├─ Service calls repository (singleton, reused)
│   └─ @Transactional creates new Hibernate session
│       ├─ Session opened
│       ├─ Execute queries
│       ├─ Commit transaction
│       └─ Session closed
├─ Return ResponseEntity
└─ GlobalExceptionHandler catches exceptions (if thrown)
```

### Async Task Lifecycle

```
ImageService.embedImagesInBatches() called
├─ Method annotated with @Async
├─ Spring intercepts call
├─ Submits task to TaskExecutor thread pool
│   └─ Thread pool: 2 core, 5 max threads
├─ Returns immediately to caller (CompletableFuture)
└─ Task executes on thread pool thread:
    ├─ Split images into 50-image batches
    ├─ For each batch:
    │   ├─ Call SearchClient.embedImages()
    │   │   └─ WebClient makes HTTP POST to Python service
    │   └─ Sleep 1 second (rate limiting)
    └─ Log completion
```

---

## Configuration Files

### application.yml

**Location:** `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: image-search-backend

  datasource:
    url: jdbc:postgresql://localhost:5432/imagesearch
    username: ${DB_USERNAME:imageuser}
    password: ${DB_PASSWORD:imagepass123}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update  # Auto-schema management
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  jackson:
    property-naming-strategy: SNAKE_CASE  # Python compatibility

server:
  port: 8080

search:
  backend:
    type: ${SEARCH_BACKEND:python}  # "python" or "java"

search-service:
  base-url: ${SEARCH_SERVICE_URL:http://localhost:5000}
  timeout-seconds: 120  # Large batch support

java-search-service:
  base-url: ${JAVA_SEARCH_SERVICE_URL:http://localhost:5001}
  enabled: ${JAVA_SEARCH_SERVICE_ENABLED:false}

storage:
  backend: local  # Future: could be "s3", "azure", etc.

session:
  token-expiry-hours: 12

# Circuit Breaker Configuration (Resilience4j)
resilience4j:
  circuitbreaker:
    instances:
      pythonSearchService:
        failure-rate-threshold: 50  # Open circuit at 50% failure
        wait-duration-in-open-state: 60s  # Stay open for 60s
        permitted-number-of-calls-in-half-open-state: 5
        sliding-window-size: 10

# Actuator Endpoints (Monitoring)
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,circuitbreakers
```

### .env File (Optional)

**Location:** Project root `.env`

```bash
# Database
DB_USERNAME=imageuser
DB_PASSWORD=secretpassword123

# Search Backend Selection
SEARCH_BACKEND=python  # or "java"

# Search Service URLs
SEARCH_SERVICE_URL=http://localhost:5000
JAVA_SEARCH_SERVICE_URL=http://localhost:5001
```

**Loading Mechanism:** `DotenvConfig` loads .env before Spring initializes

---

## Background Tasks & Async Operations

### Async Embedding Task

**Configuration:**

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);          // Min threads
        executor.setMaxPoolSize(5);           // Max threads
        executor.setQueueCapacity(100);       // Queue size
        executor.setThreadNamePrefix("async-embedding-");
        executor.initialize();
        return executor;
    }
}
```

**Implementation:**

```java
@Service
public class ImageService {

    @Async("taskExecutor")
    public CompletableFuture<Void> embedImagesInBatches(
            Long userId, Long folderId, List<String> filePaths, List<Long> imageIds) {

        int batchSize = 50;
        for (int i = 0; i < filePaths.size(); i += batchSize) {
            List<String> batchPaths = filePaths.subList(i, Math.min(i + batchSize, filePaths.size()));
            List<Long> batchIds = imageIds.subList(i, Math.min(i + batchSize, imageIds.size()));

            // Create request
            List<ImageInfo> images = new ArrayList<>();
            for (int j = 0; j < batchPaths.size(); j++) {
                images.add(new ImageInfo(batchIds.get(j), batchPaths.get(j)));
            }

            EmbedImagesRequest request = new EmbedImagesRequest(userId, folderId, images);

            // Call Python service (non-blocking)
            searchClient.embedImages(request);

            // Rate limiting
            Thread.sleep(1000);
        }

        return CompletableFuture.completedFuture(null);
    }
}
```

**Thread Pool Behavior:**

| Scenario | Behavior |
|----------|----------|
| 1-2 concurrent uploads | Use core threads (no queueing) |
| 3-5 concurrent uploads | Spawn additional threads (up to max 5) |
| 6+ concurrent uploads | Queue tasks (up to 100 in queue) |
| 100+ tasks in queue | Reject task (RejectedExecutionException) |

### Scheduled Tasks (Infrastructure Ready, Not Implemented)

**Configuration:**

```java
@SpringBootApplication
@EnableScheduling  // ← Annotation present
public class ImageSearchApplication {
    // No @Scheduled methods defined yet
}
```

**Potential Implementation (Future):**

```java
@Service
public class SessionService {

    @Scheduled(cron = "0 0 * * * *")  // Every hour
    public void cleanupExpiredSessions() {
        sessionRepository.deleteExpiredSessions(LocalDateTime.now());
        logger.info("Cleaned up expired sessions");
    }
}
```

---

## Error Handling & Resilience

### Exception Hierarchy

```
GlobalExceptionHandler (@ControllerAdvice)
├─ handleResourceNotFoundException(ResourceNotFoundException) → 404
├─ handleUnauthorizedException(UnauthorizedException) → 401
├─ handleForbiddenException(ForbiddenException) → 403
├─ handleBadRequestException(BadRequestException) → 400
├─ handleDuplicateResourceException(DuplicateResourceException) → 409
├─ handleValidationException(MethodArgumentNotValidException) → 422
└─ handleGenericException(Exception) → 500
```

### Error Response Format

```json
{
  "timestamp": "2025-12-01T10:30:45.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "Folder not found with id: 123",
  "path": "/api/folders/123"
}
```

### Circuit Breaker Pattern (Resilience4j)

**Configuration:**

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pythonSearchService:
        failure-rate-threshold: 50      # Open at 50% failures
        wait-duration-in-open-state: 60s  # Stay open 60s
        permitted-number-of-calls-in-half-open-state: 5
        sliding-window-size: 10
```

**State Machine:**

```
CLOSED (normal operation)
├─ Success rate > 50% → stay CLOSED
└─ Failure rate ≥ 50% → transition to OPEN

OPEN (fail-fast)
├─ All calls immediately fail
├─ Wait 60 seconds
└─ Transition to HALF_OPEN

HALF_OPEN (testing recovery)
├─ Allow 5 test calls
├─ If all succeed → CLOSED
└─ If any fail → OPEN
```

**Implementation:**

```java
@Component
public class PythonSearchClientImpl implements SearchClient {

    @CircuitBreaker(name = "pythonSearchService", fallbackMethod = "searchFallback")
    public SearchServiceResponse search(SearchServiceRequest request) {
        // Make HTTP call to Python service
        return webClient.post()
            .uri("/api/search")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(SearchServiceResponse.class)
            .block();
    }

    // Fallback method (same signature + Throwable)
    private SearchServiceResponse searchFallback(SearchServiceRequest request, Throwable t) {
        logger.error("Search service unavailable, returning empty results", t);
        return new SearchServiceResponse(Collections.emptyList());
    }
}
```

**Fallback Strategies:**

| Method | Fallback Behavior |
|--------|-------------------|
| `search()` | Return empty results list |
| `embedImages()` | Log warning, skip embedding (images already in DB) |
| `createIndex()` | Log warning, skip index creation (can retry later) |
| `deleteIndex()` | Log warning, skip deletion (orphaned index cleanup later) |

---

## Security Implementation

### Password Hashing

**Algorithm:** BCrypt (Spring Security Crypto)

```java
@Service
public class UserService {
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User register(String username, String password) {
        String hashedPassword = passwordEncoder.encode(password);
        // Save user with hashed password
    }

    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        // Continue with login
    }
}
```

**Properties:**
- Salt: Auto-generated per password
- Work factor: 10 (default BCrypt strength)
- Hash length: 60 characters

### Session Token Management

**Token Generation:**

```java
@Service
public class SessionService {

    public Session createSession(User user) {
        String token = UUID.randomUUID().toString();  // 32-byte random
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(12);

        Session session = new Session();
        session.setToken(token);
        session.setUser(user);
        session.setExpiresAt(expiresAt);

        return sessionRepository.save(session);
    }
}
```

**Token Validation:**

```java
public Long validateTokenAndGetUserId(String token) {
    Session session = sessionRepository.findByToken(token)
        .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

    if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
        throw new UnauthorizedException("Token expired");
    }

    // Sliding window: refresh last_seen
    session.setLastSeen(LocalDateTime.now());
    sessionRepository.save(session);

    return session.getUser().getId();
}
```

**Security Properties:**
- Token length: 36 characters (UUID format)
- Expiration: 12 hours (configurable)
- Sliding window: `last_seen` updated on each request
- Revocation: Delete from database

### Authorization Enforcement

**Folder Access Control:**

```java
@Service
public class FolderService {

    public Folder checkFolderAccess(Long userId, Long folderId) {
        Folder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new ResourceNotFoundException("Folder", folderId));

        // Check ownership
        if (folder.getUser().getId().equals(userId)) {
            return folder;
        }

        // Check if shared
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        boolean hasShare = folderShareRepository.existsByFolderAndSharedWithUser(folder, user);

        if (!hasShare) {
            throw new ForbiddenException("You don't have access to this folder");
        }

        return folder;
    }
}
```

**SQL Injection Prevention:**

- All queries use **JPA parameterized queries**
- No raw SQL concatenation
- Custom queries use `@Query` with `:parameter` syntax

**CORS Configuration:**

```java
config.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",  // React dev server
    "http://localhost:3001"   // Alternative port
));
config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
config.setAllowCredentials(true);
```

---

## Architectural Patterns

### 1. Strategy Pattern (Search Client)

**Interface:**
```java
public interface SearchClient {
    SearchServiceResponse search(SearchServiceRequest request);
    void embedImages(EmbedImagesRequest request);
    void createIndex(Long userId, Long folderId);
    void deleteIndex(Long userId, Long folderId);
}
```

**Implementations:**
- `PythonSearchClientImpl` - FAISS backend
- `JavaSearchClientImpl` - Elasticsearch backend

**Selection:** `@ConditionalOnProperty(name = "search.backend.type")`

### 2. Repository Pattern (Spring Data JPA)

**Interface:**
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

**Advantage:** Abstraction over data access, easy testing with mocks

### 3. DTO Pattern

**Separation of API contracts from domain models:**

- Request DTOs: `LoginRequest`, `RegisterRequest`, `ShareFolderRequest`
- Response DTOs: `LoginResponse`, `SearchResponse`, `FolderResponse`
- Entity models: `User`, `Folder`, `Image`

**Advantage:** API evolution independent of database schema

### 4. Layered Architecture

```
Controller Layer (HTTP endpoints)
    ↓
Service Layer (business logic)
    ↓
Repository Layer (data access)
    ↓
Entity Layer (domain models)
```

### 5. Dependency Injection (Constructor Injection)

```java
@Service
public class UserService {

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final FolderRepository folderRepository;
    private final SearchClient searchClient;

    // Constructor injection (preferred)
    public UserService(
            UserRepository userRepository,
            SessionService sessionService,
            FolderRepository folderRepository,
            SearchClient searchClient) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.folderRepository = folderRepository;
        this.searchClient = searchClient;
    }
}
```

**Advantages:**
- Immutable dependencies
- Explicit contract
- Detects circular dependencies at startup
- Easy to test (no reflection)

### 6. Circuit Breaker Pattern

**Resilience4j integration:**

```java
@CircuitBreaker(name = "pythonSearchService", fallbackMethod = "searchFallback")
public SearchServiceResponse search(SearchServiceRequest request) {
    // Risky operation (HTTP call)
}

private SearchServiceResponse searchFallback(SearchServiceRequest request, Throwable t) {
    // Graceful degradation
    return new SearchServiceResponse(Collections.emptyList());
}
```

### 7. Unit of Work Pattern (@Transactional)

```java
@Transactional
public void deleteAccount(Long userId) {
    // All operations in single transaction
    // Commit at end, or rollback on exception
}
```

**Isolation Level:** PostgreSQL `READ_COMMITTED` (default)

### 8. Async Task Pattern

```java
@Async("taskExecutor")
public CompletableFuture<Void> embedImagesInBatches(...) {
    // Runs on thread pool, returns immediately to caller
}
```

---

## Summary

**Total Classes:** 51
**Total Packages:** 7
**Spring Beans:** 26 singletons
**REST Endpoints:** 15
**Database Tables:** 5
**Design Patterns:** 8

The Java backend demonstrates **professional enterprise architecture** with:
- Clean separation of concerns (layered architecture)
- Robust error handling with circuit breakers
- Comprehensive security (authentication, authorization, input validation)
- Scalability via async processing and thread pools
- Flexibility via strategy pattern (pluggable backends)
- Production-ready monitoring (Actuator endpoints)
- RESTful API design with proper HTTP semantics

**Deployment:** Docker-ready with externalized configuration via environment variables.
