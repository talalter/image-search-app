from pydantic import BaseModel # type: ignore
from typing import List, Optional

# ============== User Models ==============
class UserIn(BaseModel):
    """Input model for user registration and login"""
    username: str
    password: str

class UserOut(BaseModel):
    """Output model for user data (without password)"""
    id: int
    username: str

# ============== Folder Models ==============
class FolderDeleteRequest(BaseModel):
    """Request to delete one or more folders"""
    token: str
    folder_ids: List[int]

class FolderResponse(BaseModel):
    """Single folder information"""
    id: int
    folder_name: str

class FoldersListResponse(BaseModel):
    """List of folders for a user"""
    folders: List[FolderResponse]

# ============== Image Search Models ==============
class SearchImageRequest(BaseModel):
    """Request model for image search query parameters"""
    token: str
    query: str
    top_k: int = 5
    folder_ids: Optional[str] = None  # Comma-separated folder IDs, None means search all

class ImageSearchResult(BaseModel):
    """Single search result"""
    image: str
    similarity: float

class SearchImageResponse(BaseModel):
    """Response containing search results"""
    results: List[ImageSearchResult]

# ============== Image Upload Models ==============
class UploadImagesResponse(BaseModel):
    """Response after uploading images"""
    message: str
    folder_id: int
    uploaded_count: int