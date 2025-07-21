from pydantic import BaseModel # type: ignore
from typing import List

class UserIn(BaseModel):
    username: str
    password: str

class UserOut(BaseModel):
    id: int
    username: str

class ImageUploadInput(BaseModel):
    user_id: int
    image_path: str
    
class SearchImageInput(BaseModel):
    user_id: int
    query: str
    top_k: int = 1

class FolderDeleteRequest(BaseModel):
    token: str
    folder_ids: List[int]