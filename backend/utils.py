# app.py

import os
import torch
import clip
import numpy as np
import faiss


device = "cuda" if torch.cuda.is_available() else "cpu"
model, preprocess = clip.load("ViT-B/32", device)

def embed_image(pil_image):
    image = preprocess(pil_image).unsqueeze(0).to(device)
    with torch.no_grad():
        image_features = model.encode_image(image)
    return image_features.cpu().numpy()

def embed_text(text):
    text_tokens = clip.tokenize([text]).to(device)
    with torch.no_grad():
        text_features = model.encode_text(text_tokens)
    return text_features.cpu().numpy()


def get_images_from_computer(path='./images/tal alter'): 
    """ Retrieve all image files from a specified directory."""
    images = []
    for filename in os.listdir(path):
        if filename.lower().endswith(('.png', '.jpg', '.jpeg', '.gif')):
            images.append(os.path.join(path, filename))
    return images


def find_nearest_image_faiss(query_text, image_paths, k=1):
    id = 1
    query_embedding = embed_text(query_text)
    #index = faiss.IndexIDMap(faiss.IndexFlatL2(512))  # 512 is the dimension of ViT-B/32 embeddings
    index = faiss.IndexFlatL2(512)
    # for path in image_paths:
    #     embedding = embed_image(path)
    #     index.add_with_ids(embedding.astype('float32'), np.array([id]))
    #     id += 1


    
    embeddings = np.vstack([embed_image(path) for path in image_paths])
    index.add(embeddings)
    
    query_embedding = embed_text(query_text).astype('float32').reshape(1, -1)
    distances, indices = index.search(query_embedding, k) # Get the k nearest neighbors
    
    nearest_index = indices[0][0]
    return image_paths[nearest_index], distances[0][0]


if __name__ == "__main__":
    images_path = get_images_from_computer()
    txt = "A tool to cut things"
    image, similarity = find_nearest_image_faiss(txt, images_path)
    print(image, similarity)