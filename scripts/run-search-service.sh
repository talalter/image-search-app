#!/bin/bash
# Run Python Search Microservice on port 5000
# This is the AI microservice called by the Java backend

echo "============================================"
echo "Starting Python Search Microservice"
echo "============================================"
echo ""
echo "Service: http://localhost:5000"
echo "Endpoints: /search, /embed-images, /create-index"
echo ""

# Check if virtual environment exists
if [ ! -d "venv" ]; then
    echo "⚠️  Virtual environment not found. Creating one..."
    cd search-service
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
    cd ..
    echo "✅ Virtual environment created"
else
    cd search-service
    source venv/bin/activate
fi

echo "Starting search microservice on port 5000..."
echo ""

# Run the search service
python3 app.py
