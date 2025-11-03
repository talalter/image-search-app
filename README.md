# 🔍 Image Search Application

Semantic image search using CLIP embeddings and FAISS vector similarity. Upload images, create folders, and search using natural language queries to find visually similar images.

[![Python](https://img.shields.io/badge/Python-3.12-blue.svg)](https://www.python.org/)
[![React](https://img.shields.io/badge/React-18-61dafb.svg)](https://reactjs.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.104-009688.svg)](https://fastapi.tiangolo.com/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ed.svg)](https://www.docker.com/)

## � Key Highlights

- 🤖 **AI-Powered Search**: Uses OpenAI's CLIP model for semantic understanding ("sunset beach" finds beach sunset photos)
- ⚡ **Efficient Vector Search**: FAISS similarity search on 512-dimensional embeddings
- 🐳 **Production-Ready**: Fully containerized with Docker, ready to deploy
- 🔐 **Enterprise Security**: PBKDF2 password hashing (390K iterations), session-based auth
- 👥 **Multi-User Platform**: Complete user isolation with folder sharing capabilities
- 📦 **Full-Stack Modern**: React frontend, FastAPI backend, SQLite database

## �🌟 Features

### Core Functionality
- **🔐 User Authentication**: Secure registration, login, and session management
- **📁 Folder Management**: Create, organize, and delete image folders
- **📤 Image Upload**: Bulk upload images with automatic CLIP embedding generation
- **🔎 Semantic Search**: Natural language queries using CLIP + FAISS cosine similarity
- **👥 Folder Sharing**: Share folders with other users (view/edit permissions)
- **🌐 Multi-user Isolation**: Each user has isolated storage and indexes

### Technical Features
- **Vector Search**: FAISS IndexFlatIP for efficient similarity search
- **AI Embeddings**: OpenAI CLIP (ViT-B/32) model for image understanding
- **Storage Options**: Local filesystem or AWS S3 (configurable)
- **Persistent Data**: SQLite database for metadata, file volumes for images/indexes

## 🏗️ Architecture

```
Frontend (React + Nginx) ←→ Backend (FastAPI) ←→ CLIP Model
                                    ↓
                     ┌──────────────┼──────────────┐
                     ↓              ↓              ↓
                 SQLite         FAISS          Images
              (users/folders)  (embeddings)  (storage)
```

![Architecture Diagram](architecture_diagram.svg)

## 🚀 Quick Start with Docker

### Prerequisites
- Docker & Docker Compose installed ([Get Docker](https://docs.docker.com/get-docker/))
- 4GB+ RAM available (CLIP model requires ~2GB)

### Run the Application

```bash
# Clone the repository
git clone https://github.com/talalter/image-search-app.git
cd image-search-app

# Build and start containers (first time takes ~5 minutes)
docker-compose up --build

# Access the application
# Frontend: http://localhost:3000
# Backend API: http://localhost:9999
# API Docs: http://localhost:9999/docs
```

**That's it!** The app will:
1. Build frontend and backend images (~5 minutes first time)
2. Start both services with networking configured
3. Create persistent volumes for data
4. Be accessible at http://localhost:3000

### Stop the Application
```bash
docker-compose down
```

### View Logs
```bash
docker-compose logs -f backend
docker-compose logs -f frontend
```

## 🛠️ Local Development Setup

### Backend Setup
```bash
# Create virtual environment
python3 -m venv talenv
source talenv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Run backend
cd backend
uvicorn api:app --reload --port 9999
```

### Frontend Setup
```bash
cd frontend
npm install
npm start  # Runs on port 3000, proxies API to :9999
```

## 📖 Usage Guide

### 1. Register & Login
- Create an account with username/password
- Login to receive authentication token (stored in localStorage)

### 2. Create Folders
- Click "📁 Manage Folders" → "➕ Create Folder"
- Enter folder name (e.g., "Vacation Photos", "Work Documents")

### 3. Upload Images
- Select a folder from the upload panel
- Choose multiple images (JPG, PNG)
- Images are automatically embedded using CLIP

### 4. Search Images
- Enter natural language query (e.g., "sunset over mountains", "red car")
- Optionally select specific folders to search
- View top matching images with similarity scores

### 5. Share Folders
- Click "📤 Share Folder"
- Enter username and permission level (view/edit)
- Shared users can search (and upload if edit permission)

## 🗂️ Project Structure

```
image-search-app/
├── backend/
│   ├── api.py                  # FastAPI app entry point
│   ├── database.py             # SQLite operations
│   ├── faiss_handler.py        # FAISS index management
│   ├── utils.py                # CLIP embedding generation
│   ├── security.py             # Password hashing
│   ├── routes/
│   │   ├── user_routes.py      # Register/login/logout
│   │   ├── images_routes.py    # Upload/search/folders
│   │   └── sharing_routes.py   # Folder sharing
│   ├── images/                 # Uploaded images (volume)
│   ├── faisses_indexes/        # FAISS indexes (volume)
│   ├── backend.db              # SQLite database (volume)
│   └── Dockerfile
│
├── frontend/
│   ├── src/
│   │   ├── App.jsx             # Main component
│   │   └── components/
│   │       ├── Login.jsx
│   │       ├── SearchImages.jsx
│   │       ├── UploadImages.jsx
│   │       ├── GetFolders.jsx
│   │       ├── ShareFolder.jsx
│   │       └── SharedWithMe.jsx
│   ├── nginx.conf              # Production web server config
│   └── Dockerfile
│
└── docker-compose.yml          # Multi-container orchestration
```

## 🔧 Configuration

### Environment Variables (Backend)

```bash
STORAGE_BACKEND=local  # Options: 'local' or 'aws'

# For AWS S3 storage (optional)
AWS_ACCESS_KEY_ID=your_key
AWS_SECRET_ACCESS_KEY=your_secret
AWS_BUCKET_NAME=your_bucket
AWS_REGION=us-east-1
```

### Database Schema

**users**: id, username, password_hash, created_at  
**folders**: id, user_id, folder_name  
**images**: id, user_id, folder_id, filepath  
**folder_shares**: id, folder_id, owner_id, shared_with_user_id, permission  
**sessions**: token, user_id, expires_at, last_seen

## 🐳 Docker Details

### Image Sizes
- Backend: ~1.8GB (includes Python + CLIP model + FAISS)
- Frontend: ~25MB (multi-stage build with Nginx)

### Volumes (Data Persistence)
```yaml
./backend/images → /app/images            # Uploaded images
./backend/faisses_indexes → /app/faisses_indexes  # FAISS indexes
./backend/backend.db → /app/backend.db    # SQLite database
```

### Network
- Containers communicate via `app-network` bridge
- Frontend proxies API requests to `backend:9999`
- Only ports 3000 and 9999 exposed to host

## 🚢 Deployment Options

### Railway (Recommended - Free Tier)
1. Push code to GitHub
2. Connect Railway to your repo
3. Add environment variables in dashboard
4. Railway auto-detects Docker Compose

### Render
1. Create Web Service for backend (Docker)
2. Create Static Site for frontend
3. Set environment variables
4. Connect to GitHub for auto-deploys

### AWS/DigitalOcean
1. Set up EC2/Droplet instance
2. Install Docker & Docker Compose
3. Clone repo and run `docker-compose up -d`
4. Configure reverse proxy (Nginx) for HTTPS

## 🧪 Testing

```bash
# Run multi-user isolation tests
cd backend
python3 test_multi_user.py
```

Tests cover:
- User registration and login
- Folder isolation between users
- Search result isolation
- FAISS index separation
- Folder sharing permissions
- Session independence

## 🔒 Security

- **Passwords**: PBKDF2-HMAC-SHA256 with 390K iterations
- **Sessions**: 32-byte random tokens with 12-hour TTL
- **Foreign Keys**: CASCADE deletes for data integrity
- **Input Validation**: Pydantic models on all endpoints
- **CORS**: Configured for same-origin policy

## 📝 API Endpoints

### Authentication
- `POST /register` - Create new user
- `POST /login` - Get auth token
- `POST /logout` - Invalidate session

### Folder Management
- `POST /create-folder` - Create new folder
- `POST /delete-folder` - Delete folder and contents
- `GET /get-folders` - List owned + shared folders

### Image Operations
- `POST /upload-images` - Upload multiple images
- `GET /search-images` - Semantic search query

### Sharing
- `POST /share-folder` - Share folder with user
- `POST /revoke-folder-share` - Remove access
- `GET /folders-shared-with-me` - List received shares
- `GET /folders-shared-by-me` - List given shares

Full API documentation: http://localhost:9999/docs

## 🤝 Contributing

This is a portfolio project, but suggestions are welcome! Feel free to:
- Report bugs via GitHub Issues
- Suggest features
- Submit pull requests

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details

## 👤 Author

**Tal Alter**
- GitHub: [@talalter](https://github.com/talalter)
- LinkedIn: [Add your LinkedIn URL here]
- Project Repository: [image-search-app](https://github.com/talalter/image-search-app)

---

**⭐ If you found this project interesting, please consider giving it a star!**

## 🙏 Acknowledgments

- **OpenAI CLIP** - Image embedding model
- **FAISS** - Facebook AI Similarity Search
- **FastAPI** - Modern Python web framework
- **React** - Frontend UI library