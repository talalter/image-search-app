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
export SEARCH_SERVICE_URL=http://localhost:5001

echo "Configuration:"
echo "  Database: imagesearch (${DB_USERNAME})"
echo "  Search Service: ${SEARCH_SERVICE_URL}"
echo ""

# Verify search service is running
echo "Verifying search service..."
if ! curl -s http://localhost:5001/actuator/health > /dev/null 2>&1; then
    echo "⚠️  Search service not ready. Starting anyway..."
    echo "   Make sure to start: ./scripts/run-java-search-service-optimized.sh"
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
echo "   Starting with optimized JVM settings..."
echo "   Max heap: 512m (sufficient for REST API)"
echo "   Initial heap: 128m"
echo "   G1GC for low-latency"
echo ""

cd "$PROJECT_ROOT/java-backend"

# JVM optimization flags for backend service
export JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:MaxMetaspaceSize=128m -Djava.awt.headless=true -XX:+UseCompressedOops"

./gradlew bootRun --no-daemon