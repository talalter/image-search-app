
from routes.user_routes import router as user_router
from routes.images_routes import router as images_router
from routes.sharing_routes import router as sharing_router
from fastapi import FastAPI, HTTPException # type: ignore
from fastapi.staticfiles import StaticFiles # type: ignore
from pathlib import Path
import os
from exception_handlers import register_exception_handlers

storage_backend = os.getenv("STORAGE_BACKEND", "local").lower()


app = FastAPI()

# Register global exception handlers (similar to @ControllerAdvice in Spring Boot)
register_exception_handlers(app)

if storage_backend == 'local':
    BASE_DIR = Path(__file__).resolve().parent      # .../backend
    IMAGES_ROOT = (BASE_DIR / "images").resolve()   # .../backend/images
    app.mount("/images", StaticFiles(directory=str(IMAGES_ROOT)), name="images")
app.include_router(user_router)
app.include_router(images_router)
app.include_router(sharing_router)

@app.get("/")
def read_root():
    return {"message": "Welcome to the User API"}    

