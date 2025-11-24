from fastapi import APIRouter, Body, HTTPException # type: ignore
from pydantic_models import UserIn, UserOut, LoginResponse
from database import init_db, add_user, get_user_by_username, delete_user_from_db
import uuid
import os
import shutil
from security import verify_password
from routes.session_store import (
    get_user_id_from_token,
    invalidate_session,
    invalidate_user_sessions,
    issue_session_token,
)
from exceptions import InvalidCredentialsException, StorageException
from search_client import SearchServiceClient

router = APIRouter()
search_client = SearchServiceClient()


@router.get("/api")
def read_root():
    return {"message": "Welcome to the User API"}
    
@router.post("/api/users/register", response_model=UserOut)
def register(user: UserIn):
    """
    Register a new user account.

    Returns:
    - 200: User created successfully
    - 400: Username already taken or validation error
    - 409: Duplicate username (handled by DuplicateRecordException)

    Exceptions are automatically converted to HTTP responses by global handlers.

    Note: FAISS indexes are created on-demand by the search-service when folders are created.
    """
    init_db()
    user_id = add_user(user)  # Raises DuplicateRecordException if username exists
    return {"id": user_id, "username": user.username}

@router.post("/api/users/login", response_model=LoginResponse)
def login(payload: UserIn):
    """
    Authenticate user and create session.

    Process:
    1. Validate credentials against database
    2. Generate secure session token
    3. Return token for subsequent requests

    Security:
    - Passwords are hashed with PBKDF2-HMAC-SHA256
    - Generic error messages prevent username enumeration
    - Tokens expire after 12 hours of inactivity

    Raises:
    - InvalidCredentialsException: Wrong username or password (converted to 401 by global handler)
    """
    init_db()

    # Look up user by username
    db_user = get_user_by_username(payload.username)
    if not db_user:
        # Raise custom exception - generic message prevents username enumeration
        raise InvalidCredentialsException(username=payload.username)

    # Extract fields from database tuple
    user_id, username, db_password_hash, created_at = db_user

    # Verify password using secure hash comparison
    if not verify_password(payload.password, db_password_hash):
        raise InvalidCredentialsException(username=payload.username)

    # Create session token (stored in database with 12-hour expiry)
    token = issue_session_token(user_id)
    
    # Return structured response using Pydantic model
    return LoginResponse(
        token=token,
        user_id=user_id,
        username=username
    )


@router.post("/api/users/logout")
def logout(token: str = Body(..., embed=True)):
    try:
        get_user_id_from_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid token")

    invalidate_session(token)
    return {"message": "Logout successful"}


@router.delete("/api/users/delete")
def delete_account(token: str = Body(..., embed=True)):
    """
    Delete the authenticated user's account and all associated data.

    Expects JSON body: { "token": "..." }
    Returns 401 when token is invalid/expired.

    Cleanup process:
    1. Delete database records (CASCADE deletes folders, images, shares, sessions)
    2. Delete physical image files from filesystem
    3. Delete FAISS vector indices via search-service
    """
    try:
        user_id = get_user_id_from_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    # 1. Delete database records (CASCADE will delete folders, images, sessions, shares)
    delete_user_from_db(user_id)
    invalidate_user_sessions(user_id)

    # 2. Delete physical image files from filesystem
    # Get project root directory
    current_dir = os.getcwd()
    if os.path.basename(current_dir) == 'python-backend':
        project_root = os.path.dirname(current_dir)
    else:
        project_root = current_dir

    user_images_path = os.path.join(project_root, "data", "uploads", "images", str(user_id))
    if os.path.exists(user_images_path):
        shutil.rmtree(user_images_path)

    # 3. Delete all FAISS indices for this user via search-service
    user_indices_path = os.path.join(project_root, "data", "indexes", str(user_id))
    if os.path.exists(user_indices_path):
        shutil.rmtree(user_indices_path)

    return {"message": f"User {user_id} and all associated data deleted successfully"}


