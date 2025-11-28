#!/bin/bash
# Warmup script - Ensures all services are ready and first requests are fast
# This script should be run after docker-compose up completes

set -e

echo "========================================="
echo "Starting service warmup..."
echo "========================================="

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to check if a service is responding
check_service() {
    local name=$1
    local url=$2
    local max_attempts=${3:-30}
    local attempt=1

    echo -n "Checking $name... "

    while [ $attempt -le $max_attempts ]; do
        if curl -f -s -o /dev/null "$url"; then
            echo -e "${GREEN}✓${NC}"
            return 0
        fi
        sleep 2
        attempt=$((attempt + 1))
    done

    echo -e "${RED}✗ (timeout after $max_attempts attempts)${NC}"
    return 1
}

# Function to warm up a service endpoint
warmup_endpoint() {
    local name=$1
    local url=$2

    echo -n "Warming up $name... "
    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" || echo "000")

    if [ "$response" != "000" ]; then
        echo -e "${GREEN}✓ (HTTP $response)${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠ (no response)${NC}"
        return 1
    fi
}

echo ""
echo "Step 1: Checking service health..."
echo "-----------------------------------"

check_service "PostgreSQL" "http://localhost:5432" 15 || echo -e "${YELLOW}⚠ PostgreSQL might not have HTTP endpoint${NC}"
check_service "Elasticsearch" "http://localhost:9200/_cluster/health" 30
check_service "Java Search Service" "http://localhost:5001/actuator/health" 45
check_service "Java Backend" "http://localhost:8080/actuator/health" 30
check_service "Frontend (Nginx)" "http://localhost:3000" 15

echo ""
echo "Step 2: Warming up application..."
echo "-----------------------------------"

# Warm up Elasticsearch
warmup_endpoint "Elasticsearch cluster info" "http://localhost:9200/"

# Warm up Java Search Service (loads ONNX models into memory)
warmup_endpoint "Search service health" "http://localhost:5001/actuator/health"

# Warm up Java Backend
warmup_endpoint "Backend health" "http://localhost:8080/actuator/health"

# Warm up Frontend
warmup_endpoint "Frontend main page" "http://localhost:3000/"

echo ""
echo "Step 3: Pre-loading ML models..."
echo "-----------------------------------"

# Create a temporary test to trigger model loading
# (Optional - only if search service has a test endpoint)
echo -e "${YELLOW}⚠ Model pre-loading requires actual image/text search${NC}"
echo "  Models will load on first real search request."

echo ""
echo "========================================="
echo -e "${GREEN}Warmup complete!${NC}"
echo "========================================="
echo ""
echo "Service endpoints:"
echo "  Frontend:      http://localhost:3000"
echo "  Java Backend:  http://localhost:8080"
echo "  Search Service: http://localhost:5001"
echo "  Elasticsearch: http://localhost:9200"
echo "  PostgreSQL:    localhost:5432"
echo ""
echo "Logs:"
echo "  docker-compose -f docker-compose.optimized.yml logs -f [service-name]"
echo ""
echo "Ready for demo! 🚀"
