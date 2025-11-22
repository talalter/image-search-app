from fastapi import APIRouter, Body, HTTPException # type: ignore
from pydantic_models import UserIn, UserOut, LoginResponse
from database import init_db, add_user, get_user_by_username, delete_user_from_db
from faiss_handler import FaissManager
import uuid
from security import verify_password
from routes.session_store import (
    get_user_id_from_token,
    invalidate_session,
    invalidate_user_sessions,
    issue_session_token,
)
from exceptions import InvalidCredentialsException, StorageException

router = APIRouter()
faiss_manager = FaissManager()

# demoapi.py

@router.get("/")
def read_root():
    return {"message": "Welcome to the User API"}
    
@router.post("/register", response_model=UserOut)
def register(user: UserIn):
    """
    Register a new user account.

    Returns:
    - 200: User created successfully
    - 400: Username already taken or validation error
    - 409: Duplicate username (handled by DuplicateRecordException)

    Exceptions are automatically converted to HTTP responses by global handlers.
    """
    init_db()
    user_id = add_user(user)  # Raises DuplicateRecordException if username exists
    try:
        faiss_manager.create_user_faiss_folder(user_id)
    except Exception as e:
        # Non-critical: FAISS folder creation failure shouldn't block registration
        print(f"[WARNING] Failed to create FAISS folder for user {user_id}: {e}")
    return {"id": user_id, "username": user.username}

@router.post("/login", response_model=LoginResponse)
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


@router.post("/logout")
def logout(token: str = Body(..., embed=True)):
    try:
        get_user_id_from_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid token")
    
    invalidate_session(token)
    return {"message": "Logout successful"}


@router.delete("/delete-account")
def delete_account(token: str = Body(..., embed=True)):
    """
    Delete the authenticated user's account.

    Expects JSON body: { "token": "..." }
    Returns 401 when token is invalid/expired.
    """
    try:
        user_id = get_user_id_from_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    # Delete user data and related resources
    delete_user_from_db(user_id)
    try:
        faiss_manager.delete_faiss_index(user_id)
    except Exception:
        # Swallow faiss deletion errors to avoid failing the whole request
        pass
    invalidate_user_sessions(user_id)
    return {"message": f"User {user_id} deleted"}


