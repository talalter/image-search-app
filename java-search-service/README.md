# Java Search Service

Semantic image search service using CLIP embeddings and Lucene vector storage.

## Architecture

```
┌──────────────────────────────────────┐
│   Java Backend (Port 8080)           │
│   OR                                  │
│   Python Backend (Port 8000)         │
└────────────┬─────────────────────────┘
             │ HTTP
             ▼
┌──────────────────────────────────────┐
│   Java Search Service (Port 5001)   │
│   - CLIP Embeddings (Python proxy)   │
│   - Lucene Vector Search             │
└────────────┬─────────────────────────┘
             │
        ┌────┴────┐
        ▼         ▼
  ┌──────────┐  ┌────────────┐
  │ Python   │  │   Lucene   │
  │  CLIP    │  │  Indexes   │
  │ (5000)   │  │            │
  └──────────┘  └────────────┘
```

## Features

- **Semantic Search**: Text-to-image search using CLIP embeddings
- **Lucene Vector Storage**: Fast cosine similarity search
- **Plugin Architecture**: Can use Python CLIP service or future ONNX implementation
- **RESTful API**: Compatible with existing backend interface
- **Per-Folder Indexes**: Organized by user and folder

## Quick Start

### 1. Build the Service

```bash
cd java-search-service
./gradlew clean build
```

### 2. Start Python Search Service (Required for now)

```bash
cd ../python-search-service
python3 app.py
# Running on http://localhost:5000
```

### 3. Start Java Search Service

```bash
cd ../java-search-service
./gradlew bootRun
# Running on http://localhost:5001
```

### 4. Configure Backend to Use Java Search Service

```bash
# For Java Backend
export SEARCH_SERVICE_URL=http://localhost:5001
cd ../java-backend
./gradlew bootRun

# For Python Backend
export SEARCH_SERVICE_URL=http://localhost:5001
cd ../python-backend
python3 app.py
```

## API Endpoints

### Health Check
```bash
GET /health

Response:
{
  "status": "healthy",
  "service": "java-search-service",
  "timestamp": "2024-11-26T10:30:00Z",
  "python_service_available": true
}
```

### Create Index
```bash
POST /api/create-index
Content-Type: application/json

{
  "user_id": 1,
  "folder_id": 5
}

Response:
{
  "message": "Index created successfully",
  "user_id": 1,
  "folder_id": 5
}
```

### Embed Images
```bash
POST /api/embed-images
Content-Type: application/json

{
  "user_id": 1,
  "folder_id": 5,
  "images": [
    {"image_id": 10, "file_path": "data/uploads/images/1/5/image1.jpg"},
    {"image_id": 11, "file_path": "data/uploads/images/1/5/image2.jpg"}
  ]
}

Response:
{
  "message": "Embeddings generated successfully",
  "count": 2,
  "failed": 0
}
```

### Search
```bash
POST /api/search
Content-Type: application/json

{
  "user_id": 1,
  "query": "sunset over mountains",
  "folder_ids": [5, 7],
  "top_k": 5
}

Response:
{
  "results": [
    {"image_id": 10, "score": 0.95, "folder_id": 5},
    {"image_id": 15, "score": 0.87, "folder_id": 7}
  ],
  "total": 2
}
```

### Delete Index
```bash
DELETE /api/delete-index/{userId}/{folderId}

Response:
{
  "message": "Index deleted successfully",
  "user_id": 1,
  "folder_id": 5
}
```

### Get Index Info
```bash
GET /api/index-info/{userId}/{folderId}

Response:
{
  "exists": true,
  "size": 42,
  "user_id": 1,
  "folder_id": 5
}
```

## Configuration

### Environment Variables

- `PYTHON_SEARCH_SERVICE_URL`: Python CLIP service URL (default: `http://localhost:5000`)
- `LUCENE_INDEX_BASE_PATH`: Base path for Lucene indexes (default: `./data/lucene-indexes`)

### application.yml

```yaml
server:
  port: 5001

python:
  search:
    service:
      url: ${PYTHON_SEARCH_SERVICE_URL:http://localhost:5000}

lucene:
  index:
    base-path: ./data/lucene-indexes
```

## Technology Stack

- **Spring Boot 3.2.0** - Web framework
- **Apache Lucene 9.9.1** - Vector search engine
- **Java 17** - Programming language
- **CLIP** - Semantic embedding model (via Python delegation)

## Implementation Details

### Current: Python CLIP Delegation

The service currently delegates embedding generation to the Python search service for immediate functionality. This allows:
- ✅ Immediate use with mature Python CLIP library
- ✅ Same embedding quality as Python backend
- ✅ Easy testing and development

### Future: ONNX Direct Inference

Planned enhancement to use ONNX Runtime for direct Java inference:
- [ ] Download CLIP ONNX model (openai/clip-vit-base-patch32)
- [ ] Implement custom tokenizer for text
- [ ] Implement image preprocessing (resize, normalize)
- [ ] Use ONNX Runtime Java API for inference
- [ ] Remove Python dependency

### Lucene Vector Storage

- **Index Organization**: `./data/lucene-indexes/{userId}/{folderId}/`
- **Vector Format**: 512 floats stored as binary (2048 bytes)
- **Search Algorithm**: Brute-force cosine similarity
- **Normalization**: L2-normalized vectors (cosine similarity = dot product)

## Testing

```bash
# Run tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

## Interview Talking Points

1. **Pure Java Vector Search**: Implemented from scratch using Lucene
2. **Plugin Architecture**: Configurable embedding service (Python now, ONNX later)
3. **Microservices Design**: Clean separation of concerns
4. **Production Ready**: Health checks, error handling, logging
5. **Scalability**: Per-folder indexes, efficient binary storage

## Differences from Python Search Service

| Feature | Python Service | Java Service |
|---------|---------------|--------------|
| Embedding Generation | PyTorch CLIP | Delegated to Python (ONNX planned) |
| Vector Storage | FAISS | Lucene |
| Search Algorithm | FAISS approximate | Brute-force cosine similarity |
| Performance | Very fast (GPU) | Fast (CPU, good for <10K images) |
| Dependencies | Python, PyTorch, FAISS | Java only (+ temp Python) |
| Deployment | Requires Python runtime | JVM only (after ONNX) |

## Roadmap

- [x] Basic service structure
- [x] Lucene vector storage
- [x] Python CLIP delegation
- [x] RESTful API
- [ ] ONNX CLIP implementation
- [ ] Comprehensive testing
- [ ] Performance benchmarking
- [ ] Docker deployment
- [ ] Horizontal scaling support

## License

Same as parent project.
