#!/bin/bash
# Run Python Backend (FastAPI) on port 8000
# This backend now uses the search-service microservice for AI operations

echo "============================================"
echo "Starting Python Backend (FastAPI)"
echo "============================================"
echo ""
echo "Backend: http://localhost:8000"
echo "API Docs: http://localhost:8000/docs"
echo ""
echo "⚠️  Make sure search-service is running!"
echo "Run: ./scripts/run-search-service.sh"
echo ""

# Get the project root directory (parent of scripts/)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Check if virtual environment exists at project root
if [ ! -d "$PROJECT_ROOT/venv" ]; then
    echo "⚠️  Virtual environment not found at project root. Creating one..."
    cd "$PROJECT_ROOT"
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
    echo "✅ Virtual environment created"
else
    source "$PROJECT_ROOT/venv/bin/activate"
fi

cd "$PROJECT_ROOT/python-backend"

# Set search service URL for local development
export SEARCH_SERVICE_URL=http://localhost:5000

echo "Starting FastAPI on port 8000..."
echo ""

# Run uvicorn
python3 -m uvicorn api:app --reload --port 8000 --host 0.0.0.0
