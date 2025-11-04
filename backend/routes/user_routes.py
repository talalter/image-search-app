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
    """
    try:
        init_db()
        user_id = add_user(user)
        faiss_manager.create_user_faiss_folder(user_id)
        return {"id": user_id, "username": user.username}
    except ValueError as e:
        # Specific error for duplicate username or validation issues
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        # Generic error for unexpected issues
        raise HTTPException(status_code=500, detail="Registration failed. Please try again.")

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
    """
    init_db()
    
    # Look up user by username
    db_user = get_user_by_username(payload.username)
    if not db_user:
        # Generic message prevents attackers from knowing if username exists
        raise HTTPException(
            status_code=401, 
            detail="Invalid username or password"
        )
    
    # Extract fields from database tuple
    user_id, username, db_password_hash, created_at = db_user
    
    # Verify password using secure hash comparison
    if not verify_password(payload.password, db_password_hash):
        raise HTTPException(
            status_code=401, 
            detail="Invalid username or password"
        )
    
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
def delete_account(token: str = Body(...)):
    user_id = get_user_id_from_token(token)
    delete_user_from_db(user_id)
    faiss_manager.delete_faiss_index(user_id)
    invalidate_user_sessions(user_id)
    return {"message": f"User {user_id} deleted"}


