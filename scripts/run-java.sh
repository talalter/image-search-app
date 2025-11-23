#!/bin/bash
# Run Java Backend with PostgreSQL
# This script automatically sets the database credentials and runs the backend

echo "============================================"
echo "Starting Java Backend"
echo "============================================"
echo ""

# Set database credentials
export DB_USERNAME=imageuser
export DB_PASSWORD=imagepass123

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

cd java-backend && ./gradlew bootRun
