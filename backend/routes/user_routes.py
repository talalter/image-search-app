from fastapi import APIRouter, Body, HTTPException # type: ignore
from pydantic_models import UserIn, UserOut
from database import init_db, add_user, get_user_by_username, delete_user_from_db
from faiss_handler import FaissManager
import uuid
from routes.session_store import sessions, get_user_id_from_token

router = APIRouter()
faiss_manager = FaissManager()

# demoapi.py

@router.get("/")
def read_root():
    return {"message": "Welcome to the User API"}
    
@router.post("/register", response_model=UserOut)
def register(user: UserIn):
    try:
        init_db()
        user_id = add_user(user)
        faiss_manager.create_user_faiss_folder(user_id)
        return {"id": user_id, "username": user.username}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@router.post("/login")
def login(user: UserIn):
    init_db()
    db_user = get_user_by_username(user.username)
    if not db_user:
        raise HTTPException(status_code=401, detail="Invalid username or password")
    db_password = db_user[2] 
    if user.password != db_password:
        raise HTTPException(status_code=401, detail="Incorrect password")
    user_id = db_user[0]
    token = str(uuid.uuid4())  # Generate a random token
    user = UserOut(id=user_id, username=user.username)
    sessions[token] = user_id

    return {
        "message": "Login successful",
        "token": token,
        "user_id": user_id,
        "username": user.username
    }


@router.post("/logout")
def logout(token: str = Body(...)):
    if token in sessions:
        del sessions[token]
        return {"message": "Logout successful"}
    else:
        raise HTTPException(status_code=401, detail="Invalid token")
    

@router.delete("/delete-account")
def delete_account(token: str = Body(...)):
    user_id = get_user_id_from_token(token)
    delete_user_from_db(user_id)
    faiss_manager.delete_faiss_index(user_id)
    del sessions[token]
    return {"message": f"User {user_id} deleted"}


