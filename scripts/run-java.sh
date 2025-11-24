#!/bin/bash
# Run Java Backend with PostgreSQL
# This script automatically sets the database credentials and runs the backend

echo "============================================"
echo "Starting Java Backend"
echo "============================================"
echo ""
echo "⚠️  Make sure search-service is running!"
echo "Run: ./scripts/run-search-service.sh"
echo ""

# Get the project root directory (parent of scripts/)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Set database credentials
export DB_USERNAME=imageuser
export DB_PASSWORD=imagepass123

# Set search service URL for local development
export SEARCH_SERVICE_URL=http://localhost:5000

echo "Database configuration:"
echo "  Database: imagesearch"
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

echo "Starting Java backend on port 8080..."
echo ""

cd "$PROJECT_ROOT/java-backend" && ./gradlew bootRun
