
from routes.user_routes import router as user_router
from routes.images_routes import router as images_router
from fastapi import FastAPI, HTTPException # type: ignore
# demoapi.py

app = FastAPI()
app.include_router(user_router)
app.include_router(images_router)

@app.get("/")
def read_root():
    return {"message": "Welcome to the User API"}    

