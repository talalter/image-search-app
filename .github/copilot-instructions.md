# Image Search App - AI Agent Instructions

## Architecture Overview

**Semantic image search using CLIP embeddings + FAISS vector similarity**

- **Backend**: FastAPI (Python) with SQLite database, CLIP model for embeddings, FAISS for vector search
- **Frontend**: React (no routing library) with manual state management
- **Storage**: Pluggable backend via `STORAGE_BACKEND` env var (`local` or `aws`)
- **Authentication**: Session-based tokens stored in SQLite with 12-hour TTL

### Key Data Flow
1. User uploads images → CLIP generates 512-dim embeddings → stored in per-folder FAISS indexes
2. User searches with text → CLIP embeds query → FAISS returns top-K cosine similarities
3. Each user has isolated folder/index structure: `backend/images/{user_id}/{folder_id}/` and `backend/faisses_indexes/{user_id}/{folder_id}.faiss`

## Critical Backend Patterns

### Pydantic Response Models (REQUIRED)
**Always use response_model for endpoint responses:**
```python
@router.post("/login", response_model=LoginResponse)
def login(payload: UserIn):
    return LoginResponse(token=token, user_id=user_id, username=username)
```
Why? 
- Type safety and automatic validation
- Consistent API contracts
- Auto-generated OpenAPI docs
- Prevents accidental data leakage

**Available response models** (see `pydantic_models.py`):
- `LoginResponse`: Login endpoint
- `UserOut`: User registration
- `SearchImageResponse`: Image search results
- `UploadImagesResponse`: Image upload confirmation
- `FoldersListResponse`: Folder listings

### File Upload Pattern (multipart/form-data)
**Use Form(...) + File(...) instead of Pydantic models for file uploads:**
```python
async def upload_multiple_images(
    token: str = Form(...),
    folderName: str = Form(...),
    files: List[UploadFile] = File(...)
)
```
Why? UploadFile handles binary streams; Pydantic only supports JSON bodies.

### GET vs POST for Search
**Search uses GET (not POST):**
```python
@router.get("/search-images", response_model=SearchImageResponse)
def search_images(token: str, query: str, top_k: int = 5, folder_ids: Optional[str] = None):
```
Rationale: REST convention for read-only ops, cacheable, bookmarkable. See docstring in `backend/routes/images_routes.py:95-105`.

### FAISS Index Management
- **One index per folder**: `{user_id}/{folder_id}.faiss`
- **Normalized vectors**: Cosine similarity via `IndexFlatIP` (inner product on normalized vectors)
- **Vector IDs map to SQLite image IDs**: Enables retrieval of file paths after search
- **Must normalize** query and stored vectors before FAISS operations (see `faiss_handler.py:24-27`)

### Storage Backend Abstraction
Check `STORAGE_BACKEND` env var in `aws_handler.py`:
- `local`: Files saved to `backend/images/`, served via FastAPI StaticFiles mount
- `aws`: Files uploaded to S3, retrieved via presigned URLs (60s expiry)

Pattern: `upload_image()` and `get_path_to_save()` abstract storage operations.

## Database Schema

**SQLite with foreign key constraints enabled** (`PRAGMA foreign_keys = ON`):
```
users (id, username, password, created_at)
  ↓
folders (id, user_id, folder_name) [UNIQUE(user_id, folder_name)]
  ↓
images (id, user_id, folder_id, filepath)

sessions (token, user_id, expires_at, last_seen) [CASCADE on user delete]
```

### Session Management
- Tokens: 32-byte urlsafe random strings (`secrets.token_urlsafe(32)`)
- Auto-refresh on each request via `refresh_session_expiry()` in `session_store.py`
- Cleanup expired sessions before each auth check
- Security: PBKDF2-HMAC-SHA256 with 390K iterations (see `security.py`)

## Frontend Architecture

### State Management
**No Redux/Context** – Local state + props drilling:
- `App.jsx` manages `user`, `selectedFolderIdsforSearch`, `SelectedFolderIdsforUpload`
- Token stored in `localStorage` via `saveToken()`/`getToken()` functions
- Components communicate via callback props (`setSelectedFolderIds`)

### API Communication Pattern (REFACTORED)
**Use centralized API utility layer** (`frontend/src/utils/api.js`):
```javascript
import { loginUser, saveToken, APIError, HTTP_STATUS } from '../utils/api';

// Make API calls through utility functions
const userData = await loginUser(username, password);
saveToken(userData.token);
```

**Benefits:**
- Single source of truth for endpoints (`API_ENDPOINTS`)
- Consistent error handling with `APIError` class
- Named HTTP status codes (`HTTP_STATUS.UNAUTHORIZED`)
- JSDoc documentation for all functions
- Easy to mock in tests

**Legacy pattern** (being phased out):
```javascript
// OLD - Direct fetch calls
const res = await fetch('/login', { method: 'POST', ... });
```

**All requests proxy through React dev server** (`proxy: "http://localhost:9999"` in `frontend/package.json`):
No `REACT_APP_API_URL` needed – proxy handles routing during dev.

### Folder Selection UX
- Multi-select with visual feedback (green background = selected)
- Empty `folder_ids` → backend searches all user folders
- Comma-separated IDs in GET params: `folder_ids=1,2,3`

## Development Workflow

### Running the App
```bash
# Backend (port 9999)
cd backend/
source ../talenv/bin/activate
uvicorn api:app --reload --port 9999

# Frontend (port 3000, proxies to backend)
cd frontend/
npm start
```

### Virtual Environment
**Always activate `talenv`** before backend work:
```bash
source talenv/bin/activate
```
CLIP model loads on first import of `utils.py` (~1-2s delay).

### Database Operations
- No migrations framework – manual schema changes via `database.py:init_db()`
- Use `delete_db()` for full reset during dev (deletes all tables)
- Foreign key cascades handle cleanup automatically

## Testing Patterns

### Manual Testing Checklist
1. Register user → verify FAISS folder created (`backend/faisses_indexes/{user_id}/`)
2. Upload images → check SQLite `images` table and folder-specific index created
3. Search without selecting folders → should query all user folders
4. Search with folder selection → verify `folder_ids` param in request
5. Logout → verify session deleted from `sessions` table

### Common Pitfalls
- **CLIP model device**: Defaults to CPU (`device = "cpu"` in `utils.py`) – no GPU setup
- **FAISS IndexFlatIP**: Requires normalized vectors or similarities will be incorrect
- **File paths in DB**: Store relative paths (`images/{user_id}/{folder_id}/{filename}`) not absolute
- **React proxy**: Backend must run on port 9999 or update `frontend/package.json` proxy

## Code Conventions

### Import Organization
Backend files use standard library → third-party → local:
```python
from typing import List, Optional
from fastapi import UploadFile, File
from database import add_folder
```

### API Development Best Practices

#### Backend Endpoint Pattern
```python
@router.post("/endpoint", response_model=ResponseModel)
def endpoint_name(payload: RequestModel):
    """
    Clear docstring explaining:
    - What the endpoint does
    - Security considerations
    - Process flow
    """
    # Explicit tuple unpacking (not magic indexes)
    user_id, username, field3 = db_result
    
    # Return Pydantic model instance
    return ResponseModel(field1=value1, field2=value2)
```

#### Frontend API Call Pattern
```javascript
// In frontend/src/utils/api.js
/**
 * Function description
 * @param {type} param - Description
 * @returns {Promise<{field: type}>} Description
 * @throws {APIError} When error occurs
 */
export async function apiFunction(param) {
  return post(API_ENDPOINTS.ENDPOINT, { param });
}

// In component
import { apiFunction, APIError, HTTP_STATUS } from '../utils/api';

try {
  const result = await apiFunction(value);
  // Handle success
} catch (err) {
  if (err instanceof APIError) {
    if (err.status === HTTP_STATUS.UNAUTHORIZED) {
      // Handle specific error
    }
  }
}
```

### Type Hints & Validation
- **Backend**: Pydantic models in `pydantic_models.py` for request/response validation
  - Always use `response_model` parameter on endpoints
  - Tuple unpacking instead of magic indexes: `user_id, username, password_hash, created_at = db_user`
- **Frontend**: JSDoc for type documentation + client-side validation
  - JSDoc parameters: `@param {string} username - User's username`
  - JSDoc returns: `@returns {Promise<{token: string, user_id: number}>}`
  - Validate forms before API calls (e.g., `validateForm()` in Login)
- Use `# type: ignore` for third-party imports without stubs

### Error Handling
- **Backend**: 
  - Raise `HTTPException` with specific status codes (401, 404, 400)
  - Use generic error messages to prevent username enumeration
  - Add comprehensive docstrings explaining security considerations
- **Frontend**: 
  - Use `APIError` class with status codes for structured error handling
  - Context-aware error messages: "Invalid username or password" vs "Server error"
  - Client-side validation with helpful messages before API calls

## Key Files Reference

### Backend
- `backend/api.py`: FastAPI app entry, router registration, static file mounting
- `backend/pydantic_models.py`: Request/response models (UserIn, LoginResponse, SearchImageResponse, etc.)
- `backend/routes/user_routes.py`: Auth endpoints (login, register, logout) with Pydantic response models
- `backend/routes/images_routes.py`: Upload/search/folder endpoints with detailed docstrings
- `backend/faiss_handler.py`: FaissManager class encapsulates all vector operations
- `backend/security.py`: Password hashing (PBKDF2-HMAC-SHA256)
- `backend/routes/session_store.py`: Token management (issue, validate, refresh)

### Frontend
- `frontend/src/App.jsx`: Main layout, auth state, folder selection state management
- `frontend/src/utils/api.js`: **Centralized API layer** with endpoints, error handling, token management
- `frontend/src/components/Login.jsx`: Login form with validation using API utility functions
- `frontend/src/components/SearchImages.jsx`: Query input, results display, folder filtering
