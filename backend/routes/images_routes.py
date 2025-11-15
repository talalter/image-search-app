from typing import List, Optional
from faiss_handler import FaissManager
from fastapi import UploadFile, File, Body, APIRouter, Form, HTTPException, BackgroundTasks, Depends # type: ignore
from pydantic_models import (
    FolderDeleteRequest,
    FoldersListResponse,
    CombinedFoldersResponse,
    SearchImageRequest,
    SearchImageResponse,
    ImageSearchResult,
    UploadImagesResponse
)
from database import add_folder, add_image_to_images, get_image_path_by_image_id, get_folders_by_user_id, get_folders_by_user_id_and_folder_name, delete_folder_by_id
from utils import embed_image
import io
import os
import shutil
from routes.session_store import get_user_id_from_token
from PIL import Image
from aws_handler import upload_image, get_path_to_save
import logging

# Module logger
logger = logging.getLogger("image_search_app.routes.images")
if not logger.handlers:
    # Configure basic logging in case the application hasn't configured logging yet
    logging.basicConfig(level=logging.INFO)


router = APIRouter()
faiss_manager = FaissManager()


def process_images_background(user_id: int, folder_id: int, image_paths: List[tuple]):
    """
    Background task to process images: generate embeddings and add to FAISS index.
    This runs after the user receives a successful response.
    
    Args:
        user_id: User ID
        folder_id: Folder ID
        image_paths: List of tuples (image_id, file_path)
    """
    logger.info("[BACKGROUND] Starting processing of %d images for user %s, folder %s", len(image_paths), user_id, folder_id)

    for image_id, file_path in image_paths:
        try:
            # Load image and generate embedding
            image = Image.open(file_path).convert("RGB")
            embedding = embed_image(image)

            # Add to FAISS index
            faiss_manager.add_vector_to_faiss(user_id, folder_id, embedding, image_id)
            logger.info("[BACKGROUND] Processed image %s (path=%s)", image_id, file_path)
        except Exception:
            logger.exception("[BACKGROUND] Error processing image %s (path=%s)", image_id, file_path)
    


@router.delete("/delete-folders")
async def delete_folder(request: FolderDeleteRequest = Body(...)):
    """
    Delete folders and all associated resources.
    
    This endpoint handles complete folder deletion:
    1. Database records (folders + images tables)
    2. Physical image files from filesystem
    3. FAISS vector index
    """
    print(f"[DELETE] Received delete request for folders: {request.folder_ids}")
    user_id = get_user_id_from_token(request.token)
    print(f"[DELETE] User ID: {user_id}")
    if not user_id:
        raise HTTPException(status_code=404, detail="User not found")
    
    for folder_id in request.folder_ids:
        print(f"[DELETE] Deleting folder_id={folder_id} for user_id={user_id}")
        
        try:
            # 1. Delete database records (folders and images)
            # This will CASCADE delete folder_shares automatically
            delete_folder_by_id(folder_id, user_id)
            print(f"[DELETE] Database records deleted")
            
            # 2. Delete physical image files from filesystem
            folder_path = os.path.join("images", str(user_id), str(folder_id))
            if os.path.exists(folder_path):
                shutil.rmtree(folder_path)
                print(f"[DELETE] Deleted folder path: {folder_path}")
            else:
                print(f"[DELETE] Folder path doesn't exist: {folder_path}")
            
            # 3. Delete FAISS vector index
            faiss_manager.delete_faiss_index(user_id, folder_id)
            print(f"[DELETE] FAISS index deleted")
        except ValueError as e:
            # Folder not found or permission denied
            raise HTTPException(status_code=403, detail=str(e))
        except Exception as e:
            # Other errors (database, filesystem, FAISS)
            print(f"[DELETE] Error deleting folder {folder_id}: {e}")
            raise HTTPException(status_code=500, detail=f"Failed to delete folder {folder_id}: {str(e)}")
    
    print(f"[DELETE] Successfully deleted {len(request.folder_ids)} folder(s)")
    return {"message": f"Successfully deleted {len(request.folder_ids)} folder(s)"}

@router.post("/upload-images", response_model=UploadImagesResponse)
async def upload_multiple_images(
    background_tasks: BackgroundTasks,
    token: str = Form(...),
    folderName: str = Form(...),
    files: List[UploadFile] = File(...)
):
    """
    Upload multiple images to a new folder.
    
    Why Form(...) and File(...) instead of Pydantic model?
    - File uploads require multipart/form-data encoding
    - UploadFile is a special FastAPI type for handling binary file streams
    - Pydantic models only work with JSON (application/json)
    - Form(...) extracts fields from multipart/form-data
    - This is the standard way to handle file uploads in FastAPI
    """
    # Validate user authentication
    user_id = get_user_id_from_token(token)
    if not user_id:
        raise HTTPException(status_code=404, detail="User not found")

    # Validate that files were provided
    if not files:
        raise HTTPException(status_code=400, detail="No files provided")

    # Check if folder already exists, if not create it
    folder_id = get_folders_by_user_id_and_folder_name(user_id, folderName)
    if folder_id is None:
        # Create new folder and FAISS index
        folder_id = add_folder(user_id, folderName)
        faiss_manager.create_faiss_index(user_id, folder_id)
    # If folder exists, we'll just add images to the existing folder and index
    
    uploaded_count = 0
    image_paths_for_processing = []
    
    # FAST PATH: Just save files and create DB records (no CLIP processing yet)
    for file in files:
        # Validate file type
        if not file.filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            raise HTTPException(status_code=400, detail=f"Invalid file type: {file.filename}")
        
        # Save image and create database record (fast operations)
        s3_key = f"images/{user_id}/{folder_id}/{file.filename}"
        image_id = add_image_to_images(user_id, s3_key, folder_id)
        
        # Reset file pointer and save
        file.file.seek(0)
        saved_path = upload_image(file, s3_key, upload_type='folder')
        
        # Store for background processing
        image_paths_for_processing.append((image_id, saved_path))
        uploaded_count += 1

    # Schedule background task to process embeddings (CLIP + FAISS)
    # This happens AFTER we return the response to the user
    background_tasks.add_task(
        process_images_background,
        user_id,
        folder_id,
        image_paths_for_processing
    )

    # Return immediately - user doesn't wait for CLIP processing!
    return UploadImagesResponse(
        message=f"Successfully uploaded {uploaded_count} images. Processing embeddings in background...",
        folder_id=folder_id,
        uploaded_count=uploaded_count
    )


@router.get("/search-images", response_model=SearchImageResponse)
def search_images(params: SearchImageRequest = Depends()):
    """
    Search for images using text query.
    """
    # Extract values from typed request model
    token = params.token
    query = params.query
    top_k = params.top_k or 5
    folder_ids = params.folder_ids

    # Extract and validate user from token
    user_id = get_user_id_from_token(token)
    if not user_id:
        raise HTTPException(status_code=404, detail="User not found")

    # Parse folder_ids from comma-separated string to list
    # If no folder_ids provided (or empty string), search all accessible folders (owned + shared)
    from database import get_all_accessible_folders, check_folder_access

    # Build a mapping of folder_id to owner_user_id for FAISS index paths
    folder_owner_map = {}

    if not folder_ids:
        all_folders = get_all_accessible_folders(user_id)
        folder_id_list = [folder['id'] for folder in all_folders]
        # Get owner info for each folder
        for folder in all_folders:
            if folder.get('is_owner'):
                folder_owner_map[folder['id']] = user_id
            else:
                # Shared folder - need to get owner_id
                folder_owner_map[folder['id']] = folder.get('owner_id')
    else:
        # Convert "1,2,3" to [1, 2, 3] and verify user has access to each
        requested_folder_ids = [int(fid.strip()) for fid in folder_ids.split(",") if fid.strip()]
        folder_id_list = []

        for fid in requested_folder_ids:
            access = check_folder_access(user_id, fid)
            if access:  # User has access (either owns it or it's shared)
                folder_id_list.append(fid)
                # Determine owner for FAISS index path
                if access.get('is_owner'):
                    folder_owner_map[fid] = user_id
                else:
                    folder_owner_map[fid] = access.get('owner_id')
    
    # Perform the search with folder ownership information
    distances, indices = faiss_manager.search_with_ownership(query, folder_id_list, folder_owner_map, top_k)
    
    # Build response using Pydantic models for type safety and validation
    results = []
    for i in range(len(indices[0])):
        image_id = indices[0][i]
        s3_key = get_image_path_by_image_id(image_id)
        image_url = get_path_to_save(s3_key)
        similarity = distances[0][i]
        # Using ImageSearchResult model ensures consistent response structure
        results.append(ImageSearchResult(image=image_url, similarity=float(similarity)))
    
    return SearchImageResponse(results=results)  


@router.get("/get-folders", response_model=CombinedFoldersResponse)
def get_folders(token: str):
    """
    Get all folders for a user (owned + shared with them).
    
    Why use response_model?
    - Automatically validates the response structure
    - Generates OpenAPI documentation
    - Ensures consistent API responses
    """
    from database import get_all_accessible_folders
    
    user_id = get_user_id_from_token(token)
    folders = get_all_accessible_folders(user_id)

    # Build grouped lists for owned vs shared
    owned = [f for f in folders if f.get('is_owner')]
    shared = [f for f in folders if not f.get('is_owner')]

    # Return both flat list and grouped lists for backward compatibility
    return CombinedFoldersResponse(folders=folders, owned=owned, shared=shared)



