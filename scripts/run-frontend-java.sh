#!/bin/bash
# Run React Frontend configured for Java Backend

echo "============================================"
echo "Starting React Frontend (Java Backend Mode)"
echo "============================================"
echo ""
echo "Frontend: http://localhost:3000"
echo "Backend: http://localhost:8080 (Java Spring Boot)"
echo ""
echo "Make sure the Java backend is running!"
echo "Run: ./scripts/run-java.sh"
echo ""

# Get the project root directory (parent of scripts/)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

cd "$PROJECT_ROOT/frontend"

# Set backend to Java (port 8080)
export REACT_APP_BACKEND=java

npm start
