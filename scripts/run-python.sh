#!/bin/bash
# Run Python Backend with PostgreSQL
# This script automatically sets the database credentials and runs the backend

echo "============================================"
echo "Starting Python Backend"
echo "============================================"
echo ""
echo "⚠️  Make sure python-search-service is running!"
echo "Run: ./scripts/run-python-search-service.sh"
echo ""

# Get the project root directory (parent of scripts/)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Set database credentials
export DB_USERNAME=imageuser
export DB_PASSWORD=imagepass123
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=imagesearch

# Set search service URL for local development
export SEARCH_SERVICE_URL=http://localhost:5000

# Set storage backend (local or aws)
export STORAGE_BACKEND=local

# Set base URL for image serving
export BASE_URL=http://localhost:8000

echo "Database configuration:"
echo "  Database: ${DB_NAME}"
echo "  Host: ${DB_HOST}:${DB_PORT}"
echo "  Username: ${DB_USERNAME}"
echo "  Password: ${DB_PASSWORD}"
echo ""

# Check if PostgreSQL is running
if ! systemctl is-active --quiet postgresql; then
    echo "⚠️  PostgreSQL is not running. Attempting to start..."
    sudo systemctl start postgresql
    if [ $? -ne 0 ]; then
        echo "❌ Failed to start PostgreSQL"
        echo "Please start it manually: sudo systemctl start postgresql"
        exit 1
    fi
    echo "✅ PostgreSQL started"
fi

# Check if virtual environment exists
if [ ! -d "$PROJECT_ROOT/python-backend/venv" ]; then
    echo "Virtual environment not found. Creating..."
    cd "$PROJECT_ROOT/python-backend"
    python3 -m venv venv
    if [ $? -ne 0 ]; then
        echo "❌ Failed to create virtual environment"
        exit 1
    fi
    echo "✅ Virtual environment created"
    echo ""
fi

# Activate virtual environment
echo "Activating virtual environment..."
source "$PROJECT_ROOT/python-backend/venv/bin/activate"

# Install/update dependencies
echo "Checking dependencies..."
cd "$PROJECT_ROOT/python-backend"
pip install -q -r requirements.txt
if [ $? -ne 0 ]; then
    echo "❌ Failed to install dependencies"
    exit 1
fi

echo ""
echo "Starting Python backend on port 8000..."
echo "Access the API at: http://localhost:8000"
echo "Press Ctrl+C to stop"
echo ""

# Run the backend with uvicorn
uvicorn api:app --reload --port 8000
