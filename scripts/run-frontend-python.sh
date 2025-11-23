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

cd frontend

# Set backend to Python (port 8000)
export REACT_APP_BACKEND=python

npm start
