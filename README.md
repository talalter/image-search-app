Prompt‑based image search using CLIP embeddings + FAISS indexes. Upload images, index them, and run natural‑language queries to retrieve visually/semantically similar images.

Status: Work‑in‑progress.

## Features
- FastAPI backend with user registration, login, logout, and account deletion.
- Upload folders of images and automatically build FAISS indexes per folder for similarity search.
- Search with text prompts and return top matches based on cosine similarity
- Configurable storage backend: local filesystem or AWS S3, selected via the 'STORAGE_BACKEND' environment variable​ 
- React frontend with components for folder upload and querying results.

## Notes
- SQLite database stores user credentials, folder metadata, and image file paths.
- Embeddings are generated with OpenAI's CLIP model (ViT-B/32) and stored in FAISS indexes for efficient similarity search.
- When STORAGE_BACKEND=local, the backend serves static images directly; setting it to aws uploads files to S3 and retrieves them through presigned URLs.

## Project Diagram
![Project Diagram](image-search-app.svg)