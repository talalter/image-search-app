#!/bin/bash
# Run Python Search Microservice on port 5000
# This is the AI microservice called by both Python and Java backends

echo "============================================"
echo "Starting Python Search Microservice"
echo "============================================"
echo ""
echo "Service: http://localhost:5000"
echo "Endpoints: /search, /embed-images, /create-index"
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

cd "$PROJECT_ROOT/python-search-service"

echo "Starting search microservice on port 5000..."
echo ""

# Run the search service
python3 app.py
