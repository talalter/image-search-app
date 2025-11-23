# Image Search Microservice

Python microservice for semantic image search using CLIP embeddings and FAISS vector indexing.

## Purpose

This service is called by the Java backend to perform AI-powered image search. It handles:
- CLIP embedding generation for images and text
- FAISS vector index management
- Semantic similarity search

**This service does NOT handle:**
- User authentication
- Business logic
- Database operations
- API authorization

Those are the responsibility of the Java backend.

## Architecture

```
Java Backend (port 8080)
    ↓ HTTP
Python Search Service (port 5000)
    ├── CLIP model
    └── FAISS indexes
```

## API Endpoints

### `POST /search`
Search for similar images using text query.

**Request:**
```json
{
  "userId": 1,
  "query": "sunset beach",
  "folderIds": [1, 2, 3],
  "folderOwnerMap": {"1": 1, "2": 1, "3": 2},
  "topK": 5
}
```

**Response:**
```json
{
  "results": [
    {"imageId": 42, "score": 0.95},
    {"imageId": 17, "score": 0.88}
  ]
}
```

### `POST /embed-images`
Generate embeddings for uploaded images (async).

**Request:**
```json
{
  "userId": 1,
  "folderId": 3,
  "images": [
    {"imageId": 1, "filePath": "images/1/3/photo.jpg"}
  ]
}
```

### `POST /create-index`
Create new FAISS index for a folder.

**Request:**
```json
{
  "userId": 1,
  "folderId": 3
}
```

### `DELETE /delete-index/{user_id}/{folder_id}`
Delete FAISS index for a folder.

## Setup

### Install dependencies:
```bash
pip install -r requirements.txt
```

### Run the service:
```bash
python app.py
```

The service will run on http://localhost:5000

## Technology Stack

- **FastAPI**: Modern Python web framework
- **CLIP**: OpenAI's vision-language model
- **FAISS**: Facebook's vector similarity search library
- **PyTorch**: Deep learning framework
