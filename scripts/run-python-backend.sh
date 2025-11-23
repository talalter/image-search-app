#!/bin/bash
# Run Python Backend (FastAPI) on port 8000
# This is the monolithic backend with CLIP + FAISS built-in

echo "============================================"
echo "Starting Python Backend (FastAPI)"
echo "============================================"
echo ""
echo "Backend: http://localhost:8000"
echo "API Docs: http://localhost:8000/docs"
echo ""

# Check if virtual environment exists
if [ ! -d "venv" ]; then
    echo "⚠️  Virtual environment not found. Creating one..."
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
    echo "Virtual environment created"
else
    source venv/bin/activate
fi

echo "Starting FastAPI on port 8000..."
echo ""

# Change to python-backend directory and run uvicorn
cd python-backend
python -m uvicorn api:app --reload --port 8000 --host 0.0.0.0
