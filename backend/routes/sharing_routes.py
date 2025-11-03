from fastapi import APIRouter, HTTPException, Body
from pydantic import BaseModel
from typing import List
from database import (
    share_folder_with_user,
    revoke_folder_share,
    get_folders_shared_with_user,
    get_folders_shared_by_user,
    check_folder_access,
    get_folders_by_user_id
)
from routes.session_store import get_user_id_from_token

router = APIRouter()


class ShareFolderRequest(BaseModel):
    """Request to share a folder with another user"""
    token: str
    folder_id: int
    username: str
    permission: str = "view"


class RevokeFolderShareRequest(BaseModel):
    """Request to revoke folder sharing"""
    token: str
    folder_id: int
    username: str


@router.post("/share-folder")
def share_folder(request: ShareFolderRequest):
    """
    Share a folder with another user by username.
    Only the folder owner can share their folders.
    """
    # Authenticate the requesting user
    owner_id = get_user_id_from_token(request.token)
    if not owner_id:
        raise HTTPException(status_code=401, detail="Invalid token")
    
    # Verify the user owns this folder
    user_folders = get_folders_by_user_id(owner_id)
    folder_ids = [f["id"] for f in user_folders]
    
    if request.folder_id not in folder_ids:
        raise HTTPException(status_code=403, detail="You don't own this folder")
    
    # Validate permission
    if request.permission not in ["view", "edit"]:
        raise HTTPException(status_code=400, detail="Permission must be 'view' or 'edit'")
    
    try:
        share_id = share_folder_with_user(
            request.folder_id,
            owner_id,
            request.username,
            request.permission
        )
        return {
            "message": f"Folder shared successfully with {request.username}",
            "share_id": share_id
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/revoke-folder-share")
def revoke_share(request: RevokeFolderShareRequest):
    """
    Revoke folder sharing access from a user.
    Only the folder owner can revoke access.
    """
    # Authenticate the requesting user
    owner_id = get_user_id_from_token(request.token)
    if not owner_id:
        raise HTTPException(status_code=401, detail="Invalid token")
    
    # Verify the user owns this folder
    user_folders = get_folders_by_user_id(owner_id)
    folder_ids = [f["id"] for f in user_folders]
    
    if request.folder_id not in folder_ids:
        raise HTTPException(status_code=403, detail="You don't own this folder")
    
    success = revoke_folder_share(request.folder_id, owner_id, request.username)
    
    if success:
        return {"message": f"Access revoked for {request.username}"}
    else:
        raise HTTPException(status_code=404, detail=f"No share found with {request.username}")


@router.get("/folders-shared-with-me")
def get_shared_with_me(token: str):
    """
    Get all folders that have been shared with the current user.
    """
    user_id = get_user_id_from_token(token)
    if not user_id:
        raise HTTPException(status_code=401, detail="Invalid token")
    
    shared_folders = get_folders_shared_with_user(user_id)
    return {"folders": shared_folders}


@router.get("/folders-shared-by-me")
def get_shared_by_me(token: str):
    """
    Get all folders that the current user has shared with others.
    Shows who each folder is shared with.
    """
    user_id = get_user_id_from_token(token)
    if not user_id:
        raise HTTPException(status_code=401, detail="Invalid token")
    
    shared_folders = get_folders_shared_by_user(user_id)
    return {"folders": shared_folders}


@router.get("/check-folder-access")
def check_access(token: str, folder_id: int):
    """
    Check if the current user has access to a specific folder.
    Returns access details including ownership and permission level.
    """
    user_id = get_user_id_from_token(token)
    if not user_id:
        raise HTTPException(status_code=401, detail="Invalid token")
    
    access_info = check_folder_access(user_id, folder_id)
    
    if not access_info:
        raise HTTPException(status_code=403, detail="No access to this folder")
    
    return access_info
