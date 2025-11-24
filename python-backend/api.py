
from routes.user_routes import router as user_router
from routes.images_routes import router as images_router
from routes.sharing_routes import router as sharing_router
from fastapi import FastAPI, HTTPException # type: ignore
from fastapi.middleware.cors import CORSMiddleware # type: ignore
from fastapi.staticfiles import StaticFiles # type: ignore
from pathlib import Path
import os
from exception_handlers import register_exception_handlers

storage_backend = os.getenv("STORAGE_BACKEND", "local").lower()


app = FastAPI(title="Image Search API (Python FastAPI)")

# CORS middleware - allow React frontend to call this backend
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",  # React dev server
        "http://localhost:3001",  # Alternative port
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register global exception handlers (similar to @ControllerAdvice in Spring Boot)
register_exception_handlers(app)

if storage_backend == 'local':
    BASE_DIR = Path(__file__).resolve().parent      # .../python-backend
    IMAGES_ROOT = (BASE_DIR.parent / "data" / "uploads" / "images").resolve()   # .../data/uploads/images
    # Create the directory if it doesn't exist
    IMAGES_ROOT.mkdir(parents=True, exist_ok=True)
    app.mount("/images", StaticFiles(directory=str(IMAGES_ROOT)), name="images")

app.include_router(user_router)
app.include_router(images_router)
app.include_router(sharing_router)

@app.get("/api")
def read_root():
    return {"message": "Welcome to Image Search API (Python FastAPI)"}    

