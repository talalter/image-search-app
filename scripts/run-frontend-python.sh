#!/bin/bash
# Run React Frontend configured for Python Backend

echo "============================================"
echo "Starting React Frontend (Python Backend Mode)"
echo "============================================"
echo ""
echo "Frontend: http://localhost:3000"
echo "Backend: http://localhost:8000 (Python FastAPI)"
echo ""
echo "Make sure the Python backend is running!"
echo "Run: ./scripts/run-python-backend.sh"
echo ""

# Get the project root directory (parent of scripts/)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

cd "$PROJECT_ROOT/frontend"

# Set backend to Python (port 8000)
export REACT_APP_BACKEND=python

npm start
