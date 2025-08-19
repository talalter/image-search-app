from typing import List
from faiss_handler import FaissManager
from fastapi import UploadFile, File, Body, APIRouter, Form, HTTPException # type: ignore
from pydantic_models import *
from database import add_folder, add_image_to_images, get_image_path_by_image_id, get_folders_by_user_id, get_folders_by_user_id_and_folder_name, delete_folder_by_id
from utils import embed_image
from typing import List
import io
from routes.session_store import sessions, get_user_id_from_token
from PIL import Image
from aws_handler import upload_image, get_path_to_save
# demoapi.py

router = APIRouter()
faiss_manager = FaissManager()


@router.delete("/delete-folders")
async def delete_folder(request: FolderDeleteRequest = Body(...)):
    user_id = get_user_id_from_token(request.token)
    if not user_id:
        raise HTTPException(status_code=404, detail="User not found")
    
    for folder_id in request.folder_ids:
        delete_folder_by_id(folder_id, user_id)

@router.post("/upload-images")
async def upload_multiple_images(
    token: str = Form(...),
    folderName: str = Form(...),
    #isNewFolder : str = Form(...),
    files: List[UploadFile] = File(...)
):
    #print(isNewFolder)
    user_id = get_user_id_from_token(token)
    if not user_id:
        raise HTTPException(status_code=404, detail="User not found")
    #is_new = isNewFolder.lower() == 'true' 
    #if is_new:
    folder_id = add_folder(user_id, folderName)
    faiss_manager.create_faiss_index(user_id, folder_id) 
    # else:
    #     print("notttttttttttttttt")
        
    #     folder_id = get_folders_by_user_id_and_folder_name(user_id, folderName)

    for file in files:
        if not file.filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            raise HTTPException(status_code=400, detail="Invalid file type")
        file.file.seek(0)
        image = Image.open(file.file).convert("RGB")        
        embedding = embed_image(image)

        s3_key = f"images/{user_id}/{folder_id}/{file.filename}"
        image_id = add_image_to_images(user_id, s3_key, folder_id)
        upload_image(file, s3_key, upload_type='folder')

        faiss_manager.add_vector_to_faiss( user_id, folder_id, embedding, image_id)

    return {"message": f"Images uploaded for user {user_id}"}


@router.get("/search-images")
def search_images(token: str, query: str, top_k: int, folders_ids:str):
    print(f'folder_ids: {folders_ids}')
    folder_id_list = [int(fid) for fid in folders_ids.split(",") if fid]
    print(f'folder_id_list: {folder_id_list}')

    if len(folder_id_list) == 0:
        folder_id_list = get_folders_by_user_id(get_user_id_from_token(token))
        folder_id_list = [folder['id'] for folder in folder_id_list]
    user_id = get_user_id_from_token(token)
    if not user_id:
        raise HTTPException(status_code=404, detail="User not found")   
    distances, indices = faiss_manager.search(user_id, query, folder_id_list, top_k)  # Get the nearest image
    results = []
    for i in range(len(indices[0])):
        image_id = indices[0][i]
        s3_key = get_image_path_by_image_id(image_id)
        image_url = get_path_to_save(s3_key)
        print(f'image_url: {image_url}')
        similarity = distances[0][i]
        results.append({"image": image_url, "similarity": float(similarity)})
    return {"results": results}  


@router.get("/get-folders")
def get_folders(token: str):
    user_id = get_user_id_from_token(token)
    folders = get_folders_by_user_id(user_id)
    return {"folders": folders}



