"""
Python Search Microservice

Focused microservice responsible for:
- CLIP embedding generation
- FAISS vector index management
- Semantic image search

Called by Java backend via HTTP. Does NOT handle authentication,
user management, or business logic - that's the Java backend's job.

Architecture:
Java Backend → HTTP → This Service (FAISS + CLIP)
"""

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Dict, Optional
import uvicorn
import logging

from search_handler import SearchHandler
from embedding_service import EmbeddingService

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(title="Image Search Microservice", version="1.0.0")

# CORS - allow both Java and Python backends to call this service
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

# Initialize services (single shared EmbeddingService to avoid loading CLIP model twice)
embedding_service = EmbeddingService()
search_handler = SearchHandler(embedding_service=embedding_service)

# ============== Exception Handlers ==============

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """Log detailed validation errors for debugging."""
    logger.error(f"Validation error for {request.method} {request.url.path}")
    logger.error(f"Request body: {await request.body()}")
    logger.error(f"Validation errors: {exc.errors()}")
    return JSONResponse(
        status_code=422,
        content={"detail": exc.errors()}
    )

# ============== Request/Response Models ==============

class SearchRequest(BaseModel):
    """Request model for image search."""
    user_id: int
    query: str
    folder_ids: List[int]
    folder_owner_map: Dict[str, int]  # folder_id (as string in JSON) -> owner_user_id
    top_k: int = 5

    model_config = {"populate_by_name": True}

class SearchResult(BaseModel):
    """Individual search result."""
    image_id: int
    score: float
    folder_id: int

    model_config = {"populate_by_name": True}

class SearchResponse(BaseModel):
    """Response model for image search."""
    results: List[SearchResult]

class ImageInfo(BaseModel):
    """Image information for embedding."""
    image_id: int  # Changed from camelCase to snake_case
    file_path: str  # Changed from camelCase to snake_case

    model_config = {"populate_by_name": True}

class EmbedImagesRequest(BaseModel):
    """Request model for embedding images."""
    user_id: int  # Changed from camelCase to snake_case
    folder_id: int  # Changed from camelCase to snake_case
    images: List[ImageInfo]

    model_config = {"populate_by_name": True}

class CreateIndexRequest(BaseModel):
    """Request model for creating FAISS index."""
    user_id: int
    folder_id: int

    model_config = {"populate_by_name": True}

# ============== API Endpoints ==============

@app.get("/")
def root():
    """Root endpoint."""
    return {
        "service": "Image Search Microservice",
        "status": "running",
        "version": "1.0.0"
    }

@app.get("/health")
def health():
    """Health check endpoint for Docker."""
    return {
        "status": "healthy",
        "service": "python-search-service"
    }

@app.post("/api/search", response_model=SearchResponse)
def search_images(request: SearchRequest):
    """
    Perform semantic image search using FAISS.

    Called by Java backend when user searches for images.

    Args:
        request: Search parameters (query, folders, etc.)

    Returns:
        List of image IDs with similarity scores
    """
    logger.info(f"Search request: query='{request.query}', folders={request.folder_ids}")

    try:
        # Convert folder_owner_map keys from strings to integers
        # (JSON dict keys are always strings, but we need ints)
        folder_owner_map = {int(k): v for k, v in request.folder_owner_map.items()}

        # Calls search_handler which embeds the text internally
        distances, indices, folder_ids_list = search_handler.search_with_ownership(
            query=request.query,  # ← Text query passed here
            folder_ids=request.folder_ids,
            folder_owner_map=folder_owner_map,
            k=request.top_k
        )

        # Build response
        results = []
        if distances and len(distances) > 0 and len(distances[0]) > 0:
            for i in range(len(indices[0])):
                results.append(SearchResult(
                    image_id=int(indices[0][i]),
                    score=float(distances[0][i]),
                    folder_id=int(folder_ids_list[0][i])
                ))

        logger.info(f"Search completed: {len(results)} results found")
        return SearchResponse(results=results)

    except Exception as e:
        logger.error(f"Search failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")

@app.post("/api/embed-images")
def embed_images(request: EmbedImagesRequest):
    """
    Generate embeddings for uploaded images and add to FAISS index.
    """
    import time
    start_time = time.time()
    
    logger.info(f"Embed request: userId={request.user_id}, folderId={request.folder_id}, count={len(request.images)}")

    try:
        file_paths = [img.file_path for img in request.images]
        image_ids = [img.image_id for img in request.images]
        
        # Generate all embeddings in a single batch operation
        embeddings = embedding_service.embed_image_files_batch(file_paths, batch_size=32)
        
        # Convert to list of individual embeddings
        embeddings_list = [embeddings[i] for i in range(len(embeddings))]
        
        # Add all embeddings to FAISS index
        faiss_start = time.time()
        search_handler.add_vectors_batch(
            user_id=request.user_id,
            folder_id=request.folder_id,
            vectors=embeddings_list,
            vector_ids=image_ids
        )
        faiss_time = time.time() - faiss_start
        
        total_time = time.time() - start_time
        logger.info(f"✅ Successfully embedded {len(request.images)} images " +
                   f"(faiss: {faiss_time:.2f}s, total: {total_time:.2f}s)")
        return {"message": f"Successfully embedded {len(request.images)} images"}

    except Exception as e:
        logger.error(f"Embedding failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Embedding failed: {str(e)}")

@app.post("/api/create-index")
def create_index(request: CreateIndexRequest):
    """
    Create a new FAISS index for a folder.

    Called by Java backend when a new folder is created.

    Args:
        request: User ID and folder ID

    Returns:
        Success message
    """
    logger.info(f"Create index: userId={request.user_id}, folderId={request.folder_id}")

    try:
        search_handler.create_faiss_index(
            user_id=request.user_id,
            folder_id=request.folder_id
        )
        return {"message": "Index created successfully"}

    except Exception as e:
        logger.error(f"Index creation failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Index creation failed: {str(e)}")

@app.delete("/api/delete-index/{user_id}/{folder_id}")
def delete_index(user_id: int, folder_id: int):
    """
    Delete FAISS index for a folder.

    Called by Java backend when a folder is deleted.

    Args:
        user_id: User ID
        folder_id: Folder ID

    Returns:
        Success message
    """
    logger.info(f"Delete index: userId={user_id}, folderId={folder_id}")

    try:
        search_handler.delete_faiss_index(user_id, folder_id)
        return {"message": "Index deleted successfully"}

    except Exception as e:
        logger.error(f"Index deletion failed: {e}", exc_info=True)
        # Don't throw error - best effort cleanup
        return {"message": f"Index deletion failed: {str(e)}"}

if __name__ == "__main__":
    logger.info("Starting Image Search Microservice on port 5000")
    uvicorn.run(app, host="0.0.0.0", port=5000)
