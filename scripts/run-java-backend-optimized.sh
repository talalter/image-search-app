#!/bin/bash
# Optimized Java Backend - Limited memory usage
# Heap: 512MB max (sufficient for REST API)

echo "============================================"
echo "Java Backend (Memory Optimized)"
echo "============================================"
echo ""
echo "JVM Heap: 512MB max (was: default ~1-2GB)"
echo "Optimized for REST API workload"
echo ""

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Database configuration
export DB_USERNAME=imageuser
export DB_PASSWORD=imagepass123

# Search backend configuration - Use Python search service by default
# To use Java search service (requires Elasticsearch): export SEARCH_BACKEND=java before running
export SEARCH_BACKEND=${SEARCH_BACKEND:-python}

if [ "$SEARCH_BACKEND" = "java" ]; then
    export JAVA_SEARCH_SERVICE_URL=http://localhost:5001
    SEARCH_SERVICE_PORT=5001
    SEARCH_SERVICE_NAME="Java Search Service (Elasticsearch + ONNX)"
else
    export SEARCH_SERVICE_URL=http://localhost:5000
    SEARCH_SERVICE_PORT=5000
    SEARCH_SERVICE_NAME="Python Search Service (FAISS + PyTorch)"
fi

echo "Configuration:"
echo "  Database: imagesearch (${DB_USERNAME})"
echo "  Search Backend: ${SEARCH_BACKEND}"
echo "  Search Service: ${SEARCH_SERVICE_NAME}"
echo ""

# Verify search service is running
echo "Verifying search service on port ${SEARCH_SERVICE_PORT}..."
if ! curl -s http://localhost:${SEARCH_SERVICE_PORT}/health > /dev/null 2>&1; then
    echo "⚠️  Search service not ready. Starting anyway..."
    if [ "$SEARCH_BACKEND" = "java" ]; then
        echo "   Make sure to start: ./scripts/run-java-search-service-optimized.sh"
        echo "   (Requires Elasticsearch on port 9200)"
    else
        echo "   Make sure to start: cd python-search-service && python app.py"
    fi
else
    echo "✅ Search service ready"
fi

# Check PostgreSQL
if ! systemctl is-active --quiet postgresql; then
    echo "❌ PostgreSQL not running. Please start: sudo systemctl start postgresql"
    exit 1
fi
echo "✅ PostgreSQL ready"

echo ""
echo "Starting Java Backend with optimized JVM settings..."
echo "  Max heap: 512m (sufficient for REST API)"
echo "  Initial heap: 128m"
echo "  G1GC for low-latency"
echo ""

if [ "$SEARCH_BACKEND" = "java" ]; then
    echo "⚠️  NOTE: Java search backend requires:"
    echo "   - Elasticsearch running on port 9200 (~1.5-3GB RAM)"
    echo "   - Java Search Service on port 5001 (~300-500MB RAM)"
    echo ""
fi

cd "$PROJECT_ROOT/java-backend"

# JVM optimization flags for backend service
export JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:MaxMetaspaceSize=128m -Djava.awt.headless=true -XX:+UseCompressedOops"

./gradlew bootRun --no-daemon