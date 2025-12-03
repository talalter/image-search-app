# Python Search Service - Comprehensive Architecture Documentation

**Generated:** 2025-12-01
**Version:** 1.0
**Technology:** FastAPI, OpenAI CLIP, FAISS, Python 3.12

---

## Table of Contents

1. [Overview](#overview)
2. [File Structure](#file-structure)
3. [Class and Function Definitions](#class-and-function-definitions)
4. [Object Lifecycle](#object-lifecycle)
5. [API Endpoints](#api-endpoints)
6. [Request Flow Analysis](#request-flow-analysis)
7. [CLIP Model Management](#clip-model-management)
8. [FAISS Index Management](#faiss-index-management)
9. [Global State & Singletons](#global-state--singletons)
10. [Performance Optimization](#performance-optimization)
11. [Error Handling](#error-handling)

---

## Overview

The Python Search Service is a **dedicated AI/ML microservice** that provides:
- **CLIP embeddings** for images and text queries
- **FAISS vector similarity search** across image collections
- **Index management** (create, update, delete FAISS indexes)

**Key Statistics:**
- **3 Python files** (~680 lines total)
- **6 API endpoints** (FastAPI)
- **1 AI model** (OpenAI CLIP ViT-B/32, ~1GB)
- **Singleton pattern** for model caching
- **Thread pool** (8 workers for parallel I/O)

**Architecture Pattern:** Microservice delegated by Java/Python backends

---

## File Structure

```
python-search-service/
├── app.py                    (291 lines) - FastAPI application, REST endpoints
├── embedding_service.py      (174 lines) - CLIP model management
├── search_handler.py         (219 lines) - FAISS index operations
├── requirements.txt          - Python dependencies
└── README.md                 - Service documentation
```

### Dependencies (requirements.txt)

```
fastapi==0.104.1              # Web framework
uvicorn[standard]==0.24.0     # ASGI server
pydantic==2.5.0               # Data validation
torch==2.1.1                  # PyTorch (CLIP backend)
torchvision==0.16.1           # Image transformations
git+https://github.com/openai/CLIP.git  # OpenAI CLIP model
faiss-cpu==1.7.4              # Vector similarity search
Pillow==10.1.0                # Image loading
numpy==1.24.3                 # Numerical operations
python-dotenv==1.0.0          # Environment variables
```

---

## Class and Function Definitions

### File 1: app.py (FastAPI Application)

**Purpose:** HTTP API layer exposing REST endpoints for Java backend

#### Pydantic Models (Request/Response Validation)

| Model | Fields | Purpose |
|-------|--------|---------|
| `SearchRequest` | `user_id: int`<br>`query: str`<br>`folder_ids: List[int]`<br>`folder_owner_map: Dict[str, int]`<br>`top_k: int = 5` | Search query parameters |
| `SearchResult` | `image_id: int`<br>`score: float`<br>`folder_id: int` | Single search result |
| `SearchResponse` | `results: List[SearchResult]` | Search response wrapper |
| `ImageInfo` | `image_id: int`<br>`file_path: str` | Image metadata for embedding |
| `EmbedImagesRequest` | `user_id: int`<br>`folder_id: int`<br>`images: List[ImageInfo]` | Batch embedding request |
| `CreateIndexRequest` | `user_id: int`<br>`folder_id: int` | Index creation request |

#### Global Singletons (Lines 49-51)

```python
embedding_service = EmbeddingService()           # CLIP model singleton
search_handler = SearchHandler(embedding_service=embedding_service)
```

**Lifecycle:** Created **once** at module import, lives for entire application lifetime

#### Exception Handlers

```python
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    # Logs invalid request body, returns 422
```

#### API Endpoints (6 Total)

| Endpoint | Method | Purpose | Lines |
|----------|--------|---------|-------|
| `/` | GET | Root health check | 114-121 |
| `/health` | GET | Docker health check | 123-129 |
| `/api/search` | POST | Semantic image search | 131-174 |
| `/api/embed-images` | POST | Batch CLIP embedding | 176-213 |
| `/api/create-index` | POST | Create FAISS index | 215-239 |
| `/api/delete-index/{user_id}/{folder_id}` | DELETE | Delete FAISS index | 241-264 |

#### CORS Configuration (Lines 37-47)

```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:8080",  # Java backend
        "http://localhost:8000",  # Python backend
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

---

### File 2: embedding_service.py (CLIP Model Management)

**Purpose:** Manages OpenAI CLIP model loading and inference

#### Class: EmbeddingService

**Instance Variables:**

```python
self.device = "cuda" if torch.cuda.is_available() else "cpu"
self.model = None           # CLIP model (ViT-B/32)
self.preprocess = None      # Image preprocessing pipeline
self.max_workers = 8        # Thread pool size for image I/O
```

**Constructor (`__init__`, lines 21-36):**

```python
def __init__(self, model_name="ViT-B/32"):
    self.device = "cuda" if torch.cuda.is_available() else "cpu"
    self.model, self.preprocess = clip.load(model_name, self.device)
    self.model.eval()  # Set to evaluation mode (no gradients)
    self.max_workers = 8
```

**Object Instantiation:**
- **When:** First import of `EmbeddingService` (module-level singleton in `app.py`)
- **Duration:** ~1-2 seconds (downloads model if first time)
- **Memory:** ~700MB RAM, ~1GB disk cache
- **Lifecycle:** Lives for application lifetime, never reloaded

**Methods:**

| Method | Input | Output | Purpose | Lines |
|--------|-------|--------|---------|-------|
| `embed_image(pil_image)` | PIL Image | numpy (1, 512) | Single image embedding | 38-51 |
| `embed_images_batch(pil_images, batch_size=32)` | List[PIL Image] | numpy (N, 512) | Batch image embeddings | 53-86 |
| `embed_image_files_batch(filepaths, batch_size=32)` | List[str] | numpy (N, 512) | Load + embed images in parallel | 114-148 |
| `embed_text(text)` | str | numpy (1, 512) | Text query embedding | 150-173 |
| `_load_single_image(filepath)` | str | PIL Image | Helper: Load image from disk | 88-91 |
| `_resolve_image_path(filepath)` | str | str | Helper: Convert relative to absolute path | 93-112 |

**Key Implementation Details:**

**Batch Processing (lines 53-86):**
```python
def embed_images_batch(self, pil_images, batch_size=32):
    all_embeddings = []
    for i in range(0, len(pil_images), batch_size):
        batch = pil_images[i:i + batch_size]

        # Stack into tensor
        image_inputs = torch.stack([self.preprocess(img) for img in batch]).to(self.device)

        # CLIP inference (no gradients)
        with torch.no_grad():
            embeddings = self.model.encode_image(image_inputs)

        # Convert to numpy
        all_embeddings.append(embeddings.cpu().numpy())

    return np.vstack(all_embeddings)
```

**Parallel Image Loading (lines 114-148):**
```python
def embed_image_files_batch(self, filepaths, batch_size=32):
    # Parallel load with ThreadPoolExecutor (8 threads)
    with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
        pil_images = list(executor.map(self._load_single_image, filepaths))

    # Batch CLIP inference
    return self.embed_images_batch(pil_images, batch_size)
```

**Performance:** 100 images ~2 seconds (8 thread I/O + 32-batch CLIP)

---

### File 3: search_handler.py (FAISS Index Management)

**Purpose:** Manages FAISS vector indexes for similarity search

#### Module-Level Globals

```python
FAISS_FOLDER = get_faiss_base_folder()  # Computed at import time
```

**Function: `get_faiss_base_folder()` (lines 18-27):**

```python
def get_faiss_base_folder():
    # Docker environment
    if os.path.exists("/app/data/indexes"):
        return "/app/data/indexes"

    # Local development
    project_root = Path(__file__).parent.parent
    return os.path.join(project_root, "data/indexes")
```

**Called once** at module import, result cached in `FAISS_FOLDER`

#### Class: SearchHandler

**Constructor (`__init__`, lines 34-38):**

```python
def __init__(self, base_folder=None, embedding_service=None):
    self.base_folder = base_folder or FAISS_FOLDER
    self.embedding_service = embedding_service
    os.makedirs(self.base_folder, exist_ok=True)
```

**Methods:**

| Method | Input | Output | Purpose | Lines |
|--------|-------|--------|---------|-------|
| `_get_folder_path(user_id, folder_id)` | int, int | str | Get FAISS index file path | 40-42 |
| `_normalize(vectors)` | numpy array | numpy array | L2 normalization for cosine similarity | 44-48 |
| `create_faiss_index(user_id, folder_id, dimension=512)` | int, int, int | None | Create empty FAISS index | 50-66 |
| `add_vector_to_faiss(user_id, folder_id, vector, vector_id)` | int, int, array, int | None | Add single vector (DEPRECATED) | 68-92 |
| `add_vectors_batch(user_id, folder_id, vectors, vector_ids)` | int, int, List, List | None | Add multiple vectors (batch) | 94-129 |
| `search_with_ownership(query, folder_ids, folder_owner_map, k=5)` | str, List[int], dict, int | tuple | Multi-folder search | 131-201 |
| `delete_faiss_index(user_id, folder_id)` | int, int | None | Delete FAISS index file | 203-218 |

**Critical Implementation: Normalization (lines 44-48)**

```python
def _normalize(self, vectors):
    # L2 normalization for cosine similarity with IndexFlatIP
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    norms[norms == 0] = 1e-10  # Avoid division by zero
    return vectors / norms
```

**Why Critical:** FAISS `IndexFlatIP` (Inner Product) requires **normalized vectors** for cosine similarity

**Multi-Folder Search Algorithm (lines 131-201):**

```python
def search_with_ownership(self, query, folder_ids, folder_owner_map, k=5):
    # Embed query
    query_embedding = self.embedding_service.embed_text(query)
    query_embedding = self._normalize(query_embedding)

    # Min-heap for top-k across folders
    heap = []

    for folder_id in folder_ids:
        # Use owner's path (handles shared folders)
        owner_user_id = folder_owner_map[folder_id]
        index_path = self._get_folder_path(owner_user_id, folder_id)

        # Load index and search
        index = faiss.read_index(index_path)
        distances, indices = index.search(query_embedding, k)

        # Add to heap (merge results)
        for dist, idx in zip(distances[0], indices[0]):
            if idx != -1:  # -1 means no result
                if len(heap) < k:
                    heapq.heappush(heap, (dist, int(idx), folder_id))
                else:
                    heapq.heappushpop(heap, (dist, int(idx), folder_id))

    # Extract top-k
    top_results = heapq.nlargest(k, heap, key=lambda x: x[0])
    return (distances, indices, folder_ids)
```

**Complexity:** O(N*k*log(k)) where N = number of folders

---

## Object Lifecycle

### Startup Sequence

```
1. Python Interpreter Starts
   └─ Import app.py

2. Module-Level Imports
   ├─ Import FastAPI, Pydantic, etc.
   ├─ Import embedding_service.py
   │   └─ Import torch, clip (lazy-loaded)
   └─ Import search_handler.py
       └─ Import faiss, numpy

3. Module-Level Initialization
   ├─ search_handler.py:
   │   └─ FAISS_FOLDER = get_faiss_base_folder()  [ONCE]
   │
   └─ app.py:
       ├─ Line 50: embedding_service = EmbeddingService()  [CLIP LOADS HERE]
       │   ├─ Detect device (cuda/cpu)
       │   ├─ clip.load("ViT-B/32", device)  [1-2 seconds]
       │   │   └─ Download from OpenAI CDN if first time (~350MB)
       │   │   └─ Cache at ~/.cache/clip/
       │   └─ model.eval()  [Set to inference mode]
       │
       └─ Line 51: search_handler = SearchHandler(embedding_service)

4. FastAPI Application Ready
   └─ Uvicorn starts listening on port 5000
```

**Total Startup Time:** ~2-3 seconds (includes CLIP model loading)

---

### Per-Request Lifecycle

```
HTTP Request arrives (e.g., POST /api/search)
├─ FastAPI router matches endpoint
├─ Pydantic validates request body
│   └─ If invalid: RequestValidationError → 422
├─ Call endpoint function (e.g., search_images)
│   ├─ Use GLOBAL embedding_service (singleton)
│   ├─ Use GLOBAL search_handler (singleton)
│   └─ NO new object creation (reuse singletons)
├─ Execute business logic:
│   ├─ Load FAISS index from disk (per-request)
│   ├─ CLIP inference (uses cached model)
│   └─ Unload index (garbage collected)
└─ Return response (Pydantic serializes to JSON)
```

**Key Point:** CLIP model is **loaded once, used by all requests** (thread-safe)

---

### Object Creation Timeline

| Object | Created When | Lives Until | Reused? |
|--------|--------------|-------------|---------|
| **FastAPI app** | Module import | Application shutdown | Yes (singleton) |
| **EmbeddingService** | `app.py` line 50 | Application shutdown | Yes (singleton) |
| **CLIP model** | EmbeddingService `__init__` | Application shutdown | Yes (singleton, thread-safe) |
| **SearchHandler** | `app.py` line 51 | Application shutdown | Yes (singleton) |
| **FAISS index** | Per-request (load from disk) | End of request | No (garbage collected) |
| **PIL Images** | Per-request (parallel load) | After CLIP encoding | No (garbage collected) |
| **Embeddings (numpy)** | Per-request (CLIP output) | After FAISS add/search | No (garbage collected) |
| **ThreadPoolExecutor** | Per embed request | End of request | No (context manager) |

---

## API Endpoints

### 1. GET / (Root Health Check)

**Request:**
```
GET http://localhost:5000/
```

**Response (200 OK):**
```json
{
  "service": "Image Search Microservice",
  "status": "running",
  "version": "1.0.0"
}
```

**Purpose:** Basic connectivity test

**Implementation:** `app.py` lines 114-121

---

### 2. GET /health (Docker Health Check)

**Request:**
```
GET http://localhost:5000/health
```

**Response (200 OK):**
```json
{
  "status": "healthy",
  "service": "python-search-service"
}
```

**Purpose:** Container orchestration health monitoring

**Implementation:** `app.py` lines 123-129

---

### 3. POST /api/search (Semantic Image Search)

**Request:**
```json
POST http://localhost:5000/api/search
Content-Type: application/json

{
  "user_id": 123,
  "query": "sunset beach",
  "folder_ids": [1, 2, 5],
  "folder_owner_map": {
    "1": 123,
    "2": 123,
    "5": 124
  },
  "top_k": 5
}
```

**Response (200 OK):**
```json
{
  "results": [
    {"image_id": 1, "score": 0.9521, "folder_id": 1},
    {"image_id": 7, "score": 0.9312, "folder_id": 5},
    {"image_id": 21, "score": 0.9102, "folder_id": 2},
    {"image_id": 42, "score": 0.8833, "folder_id": 1},
    {"image_id": 5, "score": 0.8721, "folder_id": 2}
  ]
}
```

**Parameters:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | int | Yes | User requesting search |
| `query` | str | Yes | Text query (e.g., "sunset beach") |
| `folder_ids` | List[int] | Yes | Folders to search |
| `folder_owner_map` | Dict[str, int] | Yes | Maps folder_id → owner_user_id |
| `top_k` | int | No | Number of results (default 5) |

**Key Feature:** `folder_owner_map` enables cross-user searches for shared folders

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 200 | Success (even if 0 results) |
| 422 | Validation error (invalid JSON) |
| 500 | FAISS index not found, embedding error, etc. |

**Implementation:** `app.py` lines 131-174

**Timing:** ~200-500ms (depends on folder count and index size)

---

### 4. POST /api/embed-images (Batch CLIP Embedding)

**Request:**
```json
POST http://localhost:5000/api/embed-images
Content-Type: application/json

{
  "user_id": 123,
  "folder_id": 456,
  "images": [
    {"image_id": 1, "file_path": "images/123/456/photo1.jpg"},
    {"image_id": 2, "file_path": "images/123/456/photo2.jpg"},
    {"image_id": 3, "file_path": "images/123/456/photo3.jpg"}
  ]
}
```

**Response (200 OK):**
```json
{
  "message": "Successfully embedded 3 images"
}
```

**Parameters:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | int | Yes | User who owns images |
| `folder_id` | int | Yes | Folder containing images |
| `images` | List[ImageInfo] | Yes | List of image metadata |
| `images[].image_id` | int | Yes | Database image ID |
| `images[].file_path` | str | Yes | Relative or absolute path |

**Process:**

1. Parallel image loading (8 threads)
2. Batch CLIP inference (batch_size=32)
3. L2 normalization
4. Add to FAISS index (single write)

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 200 | Success |
| 404 | Image file not found |
| 422 | Validation error |
| 500 | FAISS index doesn't exist, CLIP error, etc. |

**Implementation:** `app.py` lines 176-213

**Timing:** ~2 seconds for 100 images

---

### 5. POST /api/create-index (Create FAISS Index)

**Request:**
```json
POST http://localhost:5000/api/create-index
Content-Type: application/json

{
  "user_id": 123,
  "folder_id": 456
}
```

**Response (200 OK):**
```json
{
  "message": "Index created successfully"
}
```

**Parameters:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | int | Yes | User who owns folder |
| `folder_id` | int | Yes | Folder to create index for |

**Process:**

1. Create directory: `data/indexes/{user_id}/`
2. Create empty FAISS index: `IndexIDMap(IndexFlatIP(512))`
3. Write to disk: `{user_id}/{folder_id}.faiss`

**Index Type:**
- **IndexFlatIP:** Inner Product (for cosine similarity on normalized vectors)
- **IndexIDMap:** Wrapper allowing custom integer IDs

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 200 | Success |
| 422 | Validation error |
| 500 | Permission error, disk full, etc. |

**Implementation:** `app.py` lines 215-239

**Timing:** ~10ms

---

### 6. DELETE /api/delete-index/{user_id}/{folder_id} (Delete Index)

**Request:**
```
DELETE http://localhost:5000/api/delete-index/123/456
```

**Response (200 OK):**
```json
{
  "message": "Index deleted successfully"
}
```

**Parameters:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | int | Yes (path param) | User who owns folder |
| `folder_id` | int | Yes (path param) | Folder to delete index for |

**Process:**

1. Resolve path: `data/indexes/{user_id}/{folder_id}.faiss`
2. Delete file if exists
3. Return success (even if file didn't exist)

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 200 | Success (or already doesn't exist) |
| 422 | Invalid path parameters |

**Note:** Best-effort cleanup - doesn't fail if index doesn't exist

**Implementation:** `app.py` lines 241-264

**Timing:** ~5ms

---

## Request Flow Analysis

### Flow 1: POST /api/embed-images (Upload + Embed)

```
┌─────────────────────────────────────────────────────────────────┐
│ HTTP Request                                                    │
│ POST /api/embed-images                                          │
│ {                                                               │
│   "user_id": 123,                                               │
│   "folder_id": 456,                                             │
│   "images": [                                                   │
│     {"image_id": 1, "file_path": "images/123/456/photo1.jpg"},  │
│     {"image_id": 2, "file_path": "images/123/456/photo2.jpg"},  │
│     {"image_id": 3, "file_path": "images/123/456/photo3.jpg"}   │
│   ]                                                             │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ app.py: embed_images(EmbedImagesRequest)                        │
│ - Pydantic validates request                                    │
│ - Extract file_paths = ["images/123/456/photo1.jpg", ...]      │
│ - Extract image_ids = [1, 2, 3]                                │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ embedding_service.embed_image_files_batch(file_paths, batch=32) │
│                                                                 │
│ Step 1: Parallel Image Loading (8 threads)                     │
│   ThreadPoolExecutor(max_workers=8):                           │
│   ├─ Thread 1: _load_single_image("images/123/456/photo1.jpg") │
│   │   ├─ _resolve_image_path() → absolute path                 │
│   │   │   └─ Docker: /app/data/uploads/images/123/456/photo1.jpg│
│   │   │   └─ Local: {project_root}/data/uploads/.../photo1.jpg │
│   │   └─ Image.open() → PIL.Image (RGB)                        │
│   ├─ Thread 2: Load photo2.jpg                                 │
│   └─ Thread 3: Load photo3.jpg                                 │
│   (executor.map() preserves order)                             │
│                                                                 │
│   pil_images = [<PIL Image>, <PIL Image>, <PIL Image>]         │
│                                                                 │
│ Step 2: Batch CLIP Inference                                   │
│   embed_images_batch(pil_images, batch_size=32):               │
│   └─ Single batch (3 < 32):                                    │
│       ├─ Preprocess: [self.preprocess(img) for img in batch]   │
│       ├─ Stack: torch.stack([...]) → tensor (3, 3, 224, 224)   │
│       ├─ Send to device: tensor.to(self.device)  [CPU]         │
│       ├─ CLIP forward pass (no gradients):                     │
│       │   with torch.no_grad():                                │
│       │       embeddings = self.model.encode_image(tensor)     │
│       └─ Convert to numpy: embeddings.cpu().numpy()            │
│                                                                 │
│   embeddings = numpy array (3, 512)                            │
│   [                                                             │
│     [0.123, 0.456, ..., 0.789],  # photo1 embedding            │
│     [0.234, 0.567, ..., 0.890],  # photo2 embedding            │
│     [0.345, 0.678, ..., 0.901]   # photo3 embedding            │
│   ]                                                             │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ search_handler.add_vectors_batch(user_id=123, folder_id=456,   │
│                                  vectors=embeddings, ids=[1,2,3])│
│                                                                 │
│ Step 1: Prepare Vectors                                        │
│   vectors_array = np.vstack([                                  │
│     np.array(embeddings[0]).reshape(1, -1),                    │
│     np.array(embeddings[1]).reshape(1, -1),                    │
│     np.array(embeddings[2]).reshape(1, -1)                     │
│   ])  # Shape: (3, 512)                                        │
│                                                                 │
│ Step 2: Normalize for Cosine Similarity                        │
│   norms = ||vectors_array||₂ for each row                      │
│   vectors_array = vectors_array / norms  # L2 normalization    │
│   (All vectors now have unit length)                           │
│                                                                 │
│ Step 3: Load FAISS Index                                       │
│   index_path = ".../data/indexes/123/456.faiss"                │
│   index = faiss.read_index(index_path)                         │
│   (IndexIDMap wrapping IndexFlatIP)                            │
│                                                                 │
│ Step 4: Add Vectors to Index                                   │
│   ids_array = np.array([1, 2, 3], dtype='int64')               │
│   index.add_with_ids(vectors_array, ids_array)                 │
│   (FAISS stores: ID 1 → embedding[0], ID 2 → embedding[1], ...) │
│                                                                 │
│ Step 5: Save Index to Disk                                     │
│   faiss.write_index(index, index_path)                         │
│   (Binary FAISS format, ~512 bytes per image)                  │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ Return Response                                                 │
│ {                                                               │
│   "message": "Successfully embedded 3 images"                   │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

**Timing Breakdown:**
- Image loading (parallel): ~500ms (3 images, 8 threads)
- CLIP embedding: ~100ms (3 images, batch 32)
- FAISS add+save: ~50ms
- **Total: ~650ms**

---

### Flow 2: POST /api/search (Multi-Folder Search)

```
┌─────────────────────────────────────────────────────────────────┐
│ HTTP Request                                                    │
│ POST /api/search                                                │
│ {                                                               │
│   "user_id": 123,                                               │
│   "query": "sunset beach",                                      │
│   "folder_ids": [1, 2, 5],                                      │
│   "folder_owner_map": {                                         │
│     "1": 123,  # Folder 1 owned by User 123                    │
│     "2": 123,  # Folder 2 owned by User 123                    │
│     "5": 124   # Folder 5 owned by User 124 (shared to User 123)│
│   },                                                            │
│   "top_k": 5                                                    │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ app.py: search_images(SearchRequest)                            │
│ - Pydantic validates request                                    │
│ - Convert folder_owner_map keys: str → int                     │
│   folder_owner_map = {1: 123, 2: 123, 5: 124}                  │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ search_handler.search_with_ownership(query="sunset beach",      │
│                          folder_ids=[1,2,5],                    │
│                          folder_owner_map={1:123, 2:123, 5:124},│
│                          k=5)                                   │
│                                                                 │
│ Step 1: Embed Query Text                                       │
│   embedding_service.embed_text("sunset beach"):                │
│   ├─ Tokenize: clip.tokenize(["sunset beach"])                 │
│   │   └─ Convert to tokens (max 77 tokens)                     │
│   ├─ Send to device: tokens.to(self.device)  [CPU]             │
│   ├─ CLIP text encoder:                                        │
│   │   with torch.no_grad():                                    │
│   │       embedding = self.model.encode_text(tokens)           │
│   └─ Return: numpy array (1, 512)                              │
│                                                                 │
│   query_embedding = [0.111, 0.222, ..., 0.999]  # 512-dim      │
│                                                                 │
│ Step 2: Normalize Query                                        │
│   query_embedding = query_embedding / ||query_embedding||₂     │
│                                                                 │
│ Step 3: Initialize Results Heap                                │
│   heap = []  # Min-heap for top-k results                      │
│                                                                 │
│ Step 4: Search Each Folder                                     │
│   For folder_id in [1, 2, 5]:                                  │
│                                                                 │
│   ┌─ Folder 1 (owner: 123) ─────────────────────────────────┐  │
│   │ owner = folder_owner_map[1] = 123                        │  │
│   │ index_path = ".../data/indexes/123/1.faiss"              │  │
│   │ index = faiss.read_index(index_path)                     │  │
│   │                                                           │  │
│   │ Search:                                                   │  │
│   │   distances, indices = index.search(query_embedding, k=5)│  │
│   │   # Returns:                                              │  │
│   │   #   distances: [[0.95, 0.88, 0.82, 0.79, 0.75]]        │  │
│   │   #   indices:   [[1,    42,   17,   99,   3]]           │  │
│   │                                                           │  │
│   │ Add to heap:                                              │  │
│   │   heappush(heap, (0.95, 1, 1))   # (score, img_id, folder)│ │
│   │   heappush(heap, (0.88, 42, 1))                          │  │
│   │   heappush(heap, (0.82, 17, 1))                          │  │
│   │   heappush(heap, (0.79, 99, 1))                          │  │
│   │   heappush(heap, (0.75, 3, 1))                           │  │
│   └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│   ┌─ Folder 2 (owner: 123) ─────────────────────────────────┐  │
│   │ index_path = ".../data/indexes/123/2.faiss"              │  │
│   │ distances, indices = index.search(query_embedding, k=5)  │  │
│   │   # [[0.91, 0.87, 0.81, ...]]                            │  │
│   │   # [[21,   5,    18, ...]]                              │  │
│   │                                                           │  │
│   │ Merge into heap (keep only top-5):                       │  │
│   │   if len(heap) < 5:                                      │  │
│   │       heappush(heap, (0.91, 21, 2))                      │  │
│   │   else:                                                   │  │
│   │       heappushpop(heap, (0.91, 21, 2))  # Replace min    │  │
│   └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│   ┌─ Folder 5 (owner: 124) ─────────────────────────────────┐  │
│   │ owner = folder_owner_map[5] = 124  ← Different owner!   │  │
│   │ index_path = ".../data/indexes/124/5.faiss"  ← Owner's path│ │
│   │ distances, indices = index.search(query_embedding, k=5)  │  │
│   │   # [[0.93, 0.85, ...]]                                  │  │
│   │   # [[7,    12, ...]]                                    │  │
│   │                                                           │  │
│   │ Merge into heap:                                         │  │
│   │   heappushpop(heap, (0.93, 7, 5))                        │  │
│   │   heappushpop(heap, (0.85, 12, 5))                       │  │
│   └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│ Step 5: Extract Top-K Results                                  │
│   top_results = heapq.nlargest(5, heap, key=lambda x: x[0])    │
│   # Sort by score descending                                   │
│                                                                 │
│   Example output:                                              │
│   [                                                             │
│     (0.95, 1, 1),    # image_id=1, folder=1, score=0.95        │
│     (0.93, 7, 5),    # image_id=7, folder=5, score=0.93        │
│     (0.91, 21, 2),   # image_id=21, folder=2, score=0.91       │
│     (0.88, 42, 1),   # image_id=42, folder=1, score=0.88       │
│     (0.87, 5, 2)     # image_id=5, folder=2, score=0.87        │
│   ]                                                             │
└─────────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ Return SearchResponse                                           │
│ {                                                               │
│   "results": [                                                  │
│     {"image_id": 1, "score": 0.95, "folder_id": 1},            │
│     {"image_id": 7, "score": 0.93, "folder_id": 5},            │
│     {"image_id": 21, "score": 0.91, "folder_id": 2},           │
│     {"image_id": 42, "score": 0.88, "folder_id": 1},           │
│     {"image_id": 5, "score": 0.87, "folder_id": 2}             │
│   ]                                                             │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

**Timing Breakdown:**
- Text embedding: ~50ms
- Load 3 indexes: ~150ms
- Search 3 indexes: ~100ms
- Merge results: <5ms
- **Total: ~300ms**

**Key Insight:** `folder_owner_map` enables User 123 to search Folder 5 (owned by User 124) by loading index from User 124's directory

---

## CLIP Model Management

### Model Architecture

**Model:** OpenAI CLIP ViT-B/32
- **Vision Encoder:** Vision Transformer (ViT) with patch size 32x32
- **Text Encoder:** Transformer with causal attention mask
- **Output Dimension:** 512 (both image and text embeddings)

### Model Loading

**First Time (Cold Start):**

```python
# embedding_service.py __init__()
self.model, self.preprocess = clip.load("ViT-B/32", self.device)

# Downloads from OpenAI CDN:
# https://openaipublic.azureedge.net/clip/models/ViT-B-32.pt
# Size: ~350MB
# Cache location: ~/.cache/clip/ViT-B-32.pt
```

**Timing:**
- Download: ~30 seconds (depends on internet speed)
- Load to RAM: ~2 seconds

**Subsequent Runs:**

```python
# Loads from cache
self.model, self.preprocess = clip.load("ViT-B/32", self.device)
# Timing: ~1-2 seconds (load from disk)
```

### Model Inference

**Image Encoding (Batch):**

```python
# Input: List of PIL Images
# Output: numpy array (N, 512)

with torch.no_grad():  # Disable gradient computation
    embeddings = self.model.encode_image(image_tensors)
    embeddings = embeddings.cpu().numpy()
```

**Timing:**
- Single image: ~30ms
- Batch of 32 images: ~200ms (6x faster than 32 individual calls)

**Text Encoding:**

```python
# Input: Text string
# Output: numpy array (1, 512)

text_tokens = clip.tokenize([text]).to(self.device)
with torch.no_grad():
    embedding = self.model.encode_text(text_tokens)
    embedding = embedding.cpu().numpy()
```

**Timing:** ~50ms per query

### Memory Usage

| Component | Memory (RAM) | Storage (Disk) |
|-----------|--------------|----------------|
| CLIP model weights | ~700MB | ~350MB (cached) |
| FAISS index (per folder, 1000 images) | ~2MB | ~512KB |
| Single PIL image (RGB 1024x768) | ~2.4MB | N/A |
| Batch of 32 images (tensors) | ~80MB | N/A |
| Embeddings (1000 images) | ~2MB | N/A |

**Total RAM (idle):** ~700MB (CLIP model)
**Total RAM (peak during batch):** ~900MB (model + images + tensors)

### Thread Safety

**CLIP model is thread-safe for inference:**

```python
# PyTorch models in eval() mode are thread-safe
self.model.eval()  # Disables training mode (no dropout, batch norm updates)

# Multiple requests can call encode_image/encode_text concurrently
# PyTorch handles internal synchronization
```

**Best Practice:** Use single global `EmbeddingService` instance (singleton pattern)

---

## FAISS Index Management

### Index Structure

**Type:** `IndexIDMap(IndexFlatIP(512))`

**Components:**

1. **IndexFlatIP:**
   - **IP:** Inner Product (dot product)
   - **Flat:** Exhaustive search (no approximation)
   - **Dimension:** 512 (CLIP embedding size)
   - **Distance Metric:** Cosine similarity (on normalized vectors)

2. **IndexIDMap:**
   - Wrapper allowing custom integer IDs (database image IDs)
   - Without wrapper: FAISS uses sequential 0, 1, 2, ...
   - With wrapper: Can use arbitrary IDs (1, 42, 999, ...)

**File Format:** Binary FAISS format (not human-readable)

**Size:** ~512 bytes per image (512 floats × 4 bytes + metadata)

### Index Lifecycle

**Create Index:**

```python
def create_faiss_index(user_id, folder_id, dimension=512):
    # Create empty index
    index = faiss.IndexIDMap(faiss.IndexFlatIP(dimension))

    # Save to disk
    index_path = f"{FAISS_FOLDER}/{user_id}/{folder_id}.faiss"
    os.makedirs(os.path.dirname(index_path), exist_ok=True)
    faiss.write_index(index, index_path)
```

**Add Vectors:**

```python
def add_vectors_batch(user_id, folder_id, vectors, vector_ids):
    # Load index from disk
    index_path = f"{FAISS_FOLDER}/{user_id}/{folder_id}.faiss"
    index = faiss.read_index(index_path)

    # Normalize vectors (CRITICAL for cosine similarity)
    vectors_array = self._normalize(np.vstack(vectors))

    # Add vectors with custom IDs
    ids_array = np.array(vector_ids, dtype='int64')
    index.add_with_ids(vectors_array, ids_array)

    # Save back to disk
    faiss.write_index(index, index_path)
```

**Search Index:**

```python
def search(query_embedding, index, k=5):
    # Normalize query (CRITICAL)
    query_embedding = self._normalize(query_embedding)

    # Search
    distances, indices = index.search(query_embedding, k)

    # distances: similarity scores (higher = more similar)
    # indices: image IDs (from add_with_ids)
```

**Delete Index:**

```python
def delete_faiss_index(user_id, folder_id):
    index_path = f"{FAISS_FOLDER}/{user_id}/{folder_id}.faiss"
    if os.path.exists(index_path):
        os.remove(index_path)
```

### Normalization (Critical)

**Why Normalize:**

- `IndexFlatIP` computes **dot product** (inner product)
- Dot product of normalized vectors = **cosine similarity**
- Formula: `cosine(a, b) = (a · b) / (||a|| × ||b||)`
- If `||a|| = ||b|| = 1`, then `cosine(a, b) = a · b`

**Implementation:**

```python
def _normalize(self, vectors):
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    norms[norms == 0] = 1e-10  # Avoid division by zero
    return vectors / norms
```

**When to Normalize:**
- ✅ Before `index.add_with_ids(vectors, ...)`
- ✅ Before `index.search(query, ...)`
- ❌ NOT before `faiss.write_index()` (already normalized in-memory)

### Index Performance

| Operation | Complexity | Timing (1000 images) |
|-----------|------------|----------------------|
| `create_faiss_index()` | O(1) | ~10ms |
| `add_vectors_batch(100)` | O(N) | ~50ms |
| `search(k=5)` | O(N×D) | ~10ms (exhaustive) |
| `delete_faiss_index()` | O(1) | ~5ms |
| `faiss.read_index()` | O(N×D) | ~50ms |
| `faiss.write_index()` | O(N×D) | ~50ms |

**Scalability:**

- **1K images:** ~2MB index, ~10ms search
- **10K images:** ~20MB index, ~100ms search
- **100K images:** ~200MB index, ~1s search
- **1M images:** ~2GB index, ~10s search

**For larger scales (>100K images):**
- Use `IndexIVFFlat` (approximate search with clustering)
- Trade accuracy (~95%) for speed (~10-100x faster)

---

## Global State & Singletons

### Module-Level Globals

**app.py (lines 49-51):**

```python
embedding_service = EmbeddingService()  # Created ONCE at module import
search_handler = SearchHandler(embedding_service=embedding_service)
```

**Lifecycle:** Created at module import → Lives for application lifetime

**Thread Safety:**
- `embedding_service` is thread-safe (PyTorch model in eval mode)
- `search_handler` has no mutable shared state (loads indexes per-request)

**Why Singleton:**
- Avoid reloading CLIP model (~2 seconds per load)
- Single model serves all requests (memory efficient)
- FastAPI reuses module-level objects across requests

### Path Resolution Cache

**search_handler.py (line 29):**

```python
FAISS_FOLDER = get_faiss_base_folder()  # Computed ONCE at import
```

**Function:**

```python
def get_faiss_base_folder():
    if os.path.exists("/app/data/indexes"):  # Docker
        return "/app/data/indexes"
    else:  # Local development
        return os.path.join(project_root, "data/indexes")
```

**Lifecycle:** Computed once at module import, result cached

---

## Performance Optimization

### Batch Processing

**Problem:** Processing images one-at-a-time is slow

**Solution:**

```python
# BAD (sequential):
for img in images:
    embedding = embed_image(img)  # 30ms each
# Total: 30ms × 100 = 3 seconds

# GOOD (batched):
embeddings = embed_images_batch(images, batch_size=32)
# Batch 1: 32 images in ~200ms
# Batch 2: 32 images in ~200ms
# Batch 3: 32 images in ~200ms
# Batch 4: 4 images in ~50ms
# Total: ~650ms (5x faster)
```

### Parallel I/O

**Problem:** Loading images from disk is I/O-bound

**Solution:**

```python
# ThreadPoolExecutor for parallel image loading
with ThreadPoolExecutor(max_workers=8) as executor:
    pil_images = list(executor.map(self._load_single_image, filepaths))
# 100 images: ~500ms (vs ~2s sequential)
```

### Single FAISS Write

**Problem:** Writing index to disk is expensive

**Solution:**

```python
# BAD (write N times):
for vector, vector_id in zip(vectors, vector_ids):
    index = faiss.read_index(path)
    index.add_with_ids(vector, [vector_id])
    faiss.write_index(index, path)
# Total: N × (read + write) = very slow

# GOOD (write once):
index = faiss.read_index(path)
index.add_with_ids(vectors_array, ids_array)  # Batch add
faiss.write_index(index, path)  # Single write
# Total: 1 × (read + write) = fast
```

### Heap-Based Merging

**Problem:** Searching multiple folders and keeping top-k

**Solution:**

```python
# Use min-heap to maintain top-k results
heap = []
for folder_id in folder_ids:
    distances, indices = search_folder(folder_id, k)
    for dist, idx in zip(distances, indices):
        if len(heap) < k:
            heapq.heappush(heap, (dist, idx, folder_id))
        else:
            heapq.heappushpop(heap, (dist, idx, folder_id))

# Complexity: O(N*k*log(k)) instead of O(N*k + k*log(k))
```

---

## Error Handling

### Request Validation Errors (422)

**Pydantic automatically validates request bodies:**

```python
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.error(f"Validation error: {exc.errors()}")
    logger.error(f"Request body: {await request.body()}")
    return JSONResponse(
        status_code=422,
        content={"detail": exc.errors()}
    )
```

**Example Error Response:**

```json
{
  "detail": [
    {
      "loc": ["body", "query"],
      "msg": "field required",
      "type": "value_error.missing"
    }
  ]
}
```

### File Not Found Errors (404/500)

**Image file missing:**

```python
def _load_single_image(self, filepath):
    resolved_path = self._resolve_image_path(filepath)
    if not os.path.exists(resolved_path):
        raise FileNotFoundError(f"Image not found: {resolved_path}")
    return Image.open(resolved_path).convert("RGB")
```

**Handled by FastAPI:**

```json
{
  "detail": "Image not found: /app/data/uploads/images/123/456/missing.jpg"
}
```

### FAISS Index Errors (500)

**Index doesn't exist:**

```python
def add_vectors_batch(self, user_id, folder_id, vectors, vector_ids):
    index_path = self._get_folder_path(user_id, folder_id)
    if not os.path.exists(index_path):
        raise FileNotFoundError(
            f"FAISS index for folder {folder_id} (user {user_id}) does not exist. "
            "Create index first using /api/create-index"
        )
```

**Response:**

```json
{
  "detail": "FAISS index for folder 456 (user 123) does not exist. Create index first using /api/create-index"
}
```

### Best-Effort Cleanup

**Index deletion doesn't fail if already deleted:**

```python
def delete_faiss_index(self, user_id, folder_id):
    index_path = self._get_folder_path(user_id, folder_id)
    if os.path.exists(index_path):
        os.remove(index_path)
        logger.info(f"Deleted FAISS index: {index_path}")
    else:
        logger.warning(f"Index not found (already deleted?): {index_path}")
    # No exception thrown - returns success
```

---

## Summary

**Total Files:** 3 Python files (~680 lines)
**API Endpoints:** 6 (2 GET, 3 POST, 1 DELETE)
**Key Objects:** 2 singletons (EmbeddingService, SearchHandler)
**AI Model:** OpenAI CLIP ViT-B/32 (~1GB download, ~700MB RAM)
**Vector Search:** FAISS IndexFlatIP (exact cosine similarity)
**Performance:** Batch processing (32 images), parallel I/O (8 threads)

The Python Search Service demonstrates **production-ready AI/ML microservice** architecture with:
- Singleton pattern for model caching
- Batch processing for throughput
- Parallel I/O for latency reduction
- Thread-safe global state
- Comprehensive error handling
- Docker and local development support
- Efficient FAISS index management

**Deployment:** Docker-ready, runs on port 5000, integrates with Java/Python backends.
