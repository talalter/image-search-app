from typing import List, Optional
from faiss_handler import FaissManager
from fastapi import UploadFile, File, Body, APIRouter, Form, HTTPException # type: ignore
from pydantic_models import (
    FolderDeleteRequest,
    FoldersListResponse,
    SearchImageRequest,
    SearchImageResponse,
    ImageSearchResult,
    UploadImagesResponse
)
from database import add_folder, add_image_to_images, get_image_path_by_image_id, get_folders_by_user_id, get_folders_by_user_id_and_folder_name, delete_folder_by_id
from utils import embed_image
import io
from routes.session_store import get_user_id_from_token
from PIL import Image
from aws_handler import upload_image, get_path_to_save

router = APIRouter()
faiss_manager = FaissManager()


@router.delete("/delete-folders")
async def delete_folder(request: FolderDeleteRequest = Body(...)):
    user_id = get_user_id_from_token(request.token)
    if not user_id:
        raise HTTPException(status_code=404, detail="User not found")
    
    for folder_id in request.folder_ids:
        delete_folder_by_id(folder_id, user_id)

@router.post("/upload-images", response_model=UploadImagesResponse)
async def upload_multiple_images(
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

    # Create folder and FAISS index for it
    folder_id = add_folder(user_id, folderName)
    faiss_manager.create_faiss_index(user_id, folder_id)
    
    uploaded_count = 0
    
    # Process each uploaded file
    for file in files:
        # Validate file type
        if not file.filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            raise HTTPException(status_code=400, detail=f"Invalid file type: {file.filename}")
        
        # Read and process the image
        file.file.seek(0)
        image = Image.open(file.file).convert("RGB")        
        embedding = embed_image(image)

        # Save image and create database record
        s3_key = f"images/{user_id}/{folder_id}/{file.filename}"
        image_id = add_image_to_images(user_id, s3_key, folder_id)
        upload_image(file, s3_key, upload_type='folder')

        # Add embedding to FAISS index for searching
        faiss_manager.add_vector_to_faiss(user_id, folder_id, embedding, image_id)
        uploaded_count += 1

    # Return structured response using Pydantic model
    return UploadImagesResponse(
        message=f"Successfully uploaded {uploaded_count} images",
        folder_id=folder_id,
        uploaded_count=uploaded_count
    )


@router.get("/search-images", response_model=SearchImageResponse)
def search_images(
    token: str,
    query: str,
    top_k: int = 5,
    folder_ids: Optional[str] = None
):
    """
    Search for images using text query.
    
    Why GET instead of POST?
    - GET is the REST standard for read-only operations
    - Search doesn't modify data on the server (idempotent)
    - Results are cacheable (better performance)
    - URLs are bookmarkable/shareable
    - Browser back button works naturally
    - Follows conventions (Google, Amazon, etc. use GET for search)
    
    Parameters are validated against SearchImageRequest model internally.
    """
    # Extract and validate user from token
    user_id = get_user_id_from_token(token)
    if not user_id:
        raise HTTPException(status_code=404, detail="User not found")
    
    # Parse folder_ids from comma-separated string to list
    # If no folder_ids provided (or empty string), search all user's folders
    if not folder_ids:
        folder_id_list = get_folders_by_user_id(user_id)
        folder_id_list = [folder['id'] for folder in folder_id_list]
    else:
        # Convert "1,2,3" to [1, 2, 3]
        folder_id_list = [int(fid.strip()) for fid in folder_ids.split(",") if fid.strip()]
    
    # Perform the search
    distances, indices = faiss_manager.search(user_id, query, folder_id_list, top_k)
    
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


@router.get("/get-folders", response_model=FoldersListResponse)
def get_folders(token: str):
    """
    Get all folders for a user.
    
    Why use response_model?
    - Automatically validates the response structure
    - Generates OpenAPI documentation
    - Ensures consistent API responses
    """
    user_id = get_user_id_from_token(token)
    folders = get_folders_by_user_id(user_id)
    
    # Return matches FoldersListResponse model structure
    return FoldersListResponse(folders=folders)



