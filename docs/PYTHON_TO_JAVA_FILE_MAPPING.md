# Python Backend to Java Backend File Mapping

This document maps each Python backend file to its equivalent Java backend file(s).

## Core Application Files

| Python Backend | Java Backend | Notes |
|----------------|--------------|-------|
| `python-backend/api.py` | `java-backend/src/main/java/com/imagesearch/ImageSearchApplication.java` | Main application entry point |

## Route/Controller Files

| Python Backend | Java Backend | Notes |
|----------------|--------------|-------|
| `python-backend/routes/user_routes.py` | `java-backend/src/main/java/com/imagesearch/controller/UserController.java` | User authentication, registration, deletion |
| `python-backend/routes/images_routes.py` | `java-backend/src/main/java/com/imagesearch/controller/ImageController.java`<br>`java-backend/src/main/java/com/imagesearch/controller/FolderController.java` | Image operations split into Image and Folder controllers |
| `python-backend/routes/sharing_routes.py` | `java-backend/src/main/java/com/imagesearch/controller/FolderController.java` | Sharing functionality integrated into FolderController |
| `python-backend/routes/__init__.py` | N/A | Not needed in Java (Spring Boot auto-discovers controllers) |

## Service Layer Files

| Python Backend | Java Backend | Notes |
|----------------|--------------|-------|
| `python-backend/database.py` | `java-backend/src/main/java/com/imagesearch/service/UserService.java`<br>`java-backend/src/main/java/com/imagesearch/service/ImageService.java`<br>`java-backend/src/main/java/com/imagesearch/service/FolderService.java`<br>`java-backend/src/main/java/com/imagesearch/service/SessionService.java` | Database logic distributed across service classes |
| `python-backend/routes/session_store.py` | `java-backend/src/main/java/com/imagesearch/service/SessionService.java` | Session management |
| `python-backend/aws_handler.py` | `java-backend/src/main/java/com/imagesearch/service/ImageService.java` | AWS S3 operations integrated into ImageService |
| `python-backend/faiss_handler.py` | `java-backend/src/main/java/com/imagesearch/service/SearchService.java`<br>`java-backend/src/main/java/com/imagesearch/client/PythonSearchClient.java` | Search/FAISS operations delegated to Python search service |
| `python-backend/utils.py` | `java-backend/src/main/java/com/imagesearch/service/ImageService.java`<br>`java-backend/src/main/java/com/imagesearch/service/UserService.java` | Utility functions distributed across services |
| `python-backend/security.py` | `java-backend/src/main/java/com/imagesearch/service/UserService.java` | Password hashing/verification in UserService |

## Model/Entity Files

| Python Backend | Java Backend | Notes |
|----------------|--------------|-------|
| `python-backend/pydantic_models.py` | **Request DTOs:**<br>`java-backend/src/main/java/com/imagesearch/model/dto/request/LoginRequest.java`<br>`java-backend/src/main/java/com/imagesearch/model/dto/request/RegisterRequest.java`<br>`java-backend/src/main/java/com/imagesearch/model/dto/request/SearchRequest.java`<br>`java-backend/src/main/java/com/imagesearch/model/dto/request/ShareFolderRequest.java`<br>`java-backend/src/main/java/com/imagesearch/model/dto/request/DeleteFoldersRequest.java`<br><br>**Response DTOs:**<br>`java-backend/src/main/java/com/imagesearch/model/dto/response/LoginResponse.java`<br>`java-backend/src/main/java/com/imagesearch/model/dto/response/RegisterResponse.java`<br>`java-backend/src/main/java/com/imagesearch/model/dto/response/SearchResponse.java`<br>`java-backend/src/main/java/com/imagesearch/model/dto/response/FolderResponse.java`<br>`java-backend/src/main/java/com/imagesearch/model/dto/response/UploadResponse.java`<br>`java-backend/src/main/java/com/imagesearch/model/dto/response/MessageResponse.java`<br>`java-backend/src/main/java/com/imagesearch/model/dto/response/ErrorResponse.java`<br><br>**Database Entities:**<br>`java-backend/src/main/java/com/imagesearch/model/entity/User.java`<br>`java-backend/src/main/java/com/imagesearch/model/entity/Image.java`<br>`java-backend/src/main/java/com/imagesearch/model/entity/Folder.java`<br>`java-backend/src/main/java/com/imagesearch/model/entity/Session.java`<br>`java-backend/src/main/java/com/imagesearch/model/entity/FolderShare.java` | Pydantic models split into request DTOs, response DTOs, and JPA entities |

## Repository/Data Access Files

| Python Backend | Java Backend | Notes |
|----------------|--------------|-------|
| `python-backend/database.py` (SQL queries) | `java-backend/src/main/java/com/imagesearch/repository/UserRepository.java`<br>`java-backend/src/main/java/com/imagesearch/repository/ImageRepository.java`<br>`java-backend/src/main/java/com/imagesearch/repository/FolderRepository.java`<br>`java-backend/src/main/java/com/imagesearch/repository/SessionRepository.java`<br>`java-backend/src/main/java/com/imagesearch/repository/FolderShareRepository.java` | Spring Data JPA repositories (no SQL needed for basic operations) |

## Exception Handling Files

| Python Backend | Java Backend | Notes |
|----------------|--------------|-------|
| `python-backend/exceptions.py` | `java-backend/src/main/java/com/imagesearch/exception/BadRequestException.java`<br>`java-backend/src/main/java/com/imagesearch/exception/UnauthorizedException.java`<br>`java-backend/src/main/java/com/imagesearch/exception/ForbiddenException.java`<br>`java-backend/src/main/java/com/imagesearch/exception/ResourceNotFoundException.java`<br>`java-backend/src/main/java/com/imagesearch/exception/DuplicateResourceException.java` | Custom exception classes |
| `python-backend/exception_handlers.py` | `java-backend/src/main/java/com/imagesearch/exception/GlobalExceptionHandler.java` | Centralized exception handling |

## Configuration Files

| Python Backend | Java Backend | Notes |
|----------------|--------------|-------|
| `python-backend/logging_config.py` | `java-backend/src/main/resources/application.properties` | Logging configured in properties file |
| N/A | `java-backend/src/main/java/com/imagesearch/config/CorsConfig.java` | CORS configuration (inline in Python) |
| N/A | `java-backend/src/main/java/com/imagesearch/config/WebClientConfig.java` | HTTP client configuration for search service |
| N/A | `java-backend/src/main/java/com/imagesearch/config/StaticResourceConfig.java` | Static resource handling |

## External Service Client Files

| Python Backend | Java Backend | Notes |
|----------------|--------------|-------|
| `python-backend/faiss_handler.py` (calls to search service) | `java-backend/src/main/java/com/imagesearch/client/PythonSearchClient.java`<br>`java-backend/src/main/java/com/imagesearch/client/dto/SearchServiceRequest.java`<br>`java-backend/src/main/java/com/imagesearch/client/dto/SearchServiceResponse.java`<br>`java-backend/src/main/java/com/imagesearch/client/dto/EmbedImagesRequest.java` | HTTP client for Python search service |

## Utility/Cleanup Files

| Python Backend | Java Backend | Notes |
|----------------|--------------|-------|
| `python-backend/cleanup_all.py` | N/A | Standalone cleanup script (not part of main application) |

## Key Architectural Differences

1. **Layered Architecture**: Java backend follows strict MVC pattern with separate Controller, Service, Repository layers
2. **Model Separation**: Python's Pydantic models are split into:
   - Request DTOs (input validation)
   - Response DTOs (output formatting)
   - JPA Entities (database mapping)
3. **Dependency Injection**: Java uses Spring's dependency injection vs Python's manual instantiation
4. **Data Access**: Java uses Spring Data JPA repositories vs Python's raw SQL queries
5. **Configuration**: Java uses annotations and properties files vs Python's code-based configuration
6. **Exception Handling**: Java uses custom exception classes + global handler vs Python's FastAPI exception handlers

## Quick Reference

**Python Function → Java Method Location:**
- User authentication → `UserController` + `UserService`
- Image upload → `ImageController` + `ImageService`
- Folder operations → `FolderController` + `FolderService`
- Search operations → `ImageController` + `SearchService` + `PythonSearchClient`
- Sharing operations → `FolderController` + `FolderService`
- Session management → `SessionService`
- Database queries → `*Repository` interfaces
