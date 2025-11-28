#!/bin/bash
# Verify all Java stack services are running and healthy

echo "============================================"
echo "Java Stack Health Check"
echo "============================================"
echo ""

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check service
check_service() {
    local name=$1
    local url=$2
    local expected=$3

    echo -n "Checking $name... "

    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)

    if [ "$response" = "$expected" ]; then
        echo -e "${GREEN}✅ OK${NC} (HTTP $response)"
        return 0
    else
        echo -e "${RED}❌ FAILED${NC} (HTTP $response, expected $expected)"
        return 1
    fi
}

# Check PostgreSQL
echo -n "Checking PostgreSQL... "
if systemctl is-active --quiet postgresql; then
    echo -e "${GREEN}✅ Running${NC}"
else
    echo -e "${RED}❌ Not running${NC}"
    echo "Start with: sudo systemctl start postgresql"
fi

echo ""

# Check Java Search Service
check_service "Java Search Service" "http://localhost:5001/actuator/health" "200"

# Check Java Backend
check_service "Java Backend" "http://localhost:8080/api/health" "200"

# Check Frontend
check_service "Frontend" "http://localhost:3000" "200"

echo ""
echo "============================================"
echo "Port Status"
echo "============================================"
echo ""

# Check which ports are in use
check_port() {
    local port=$1
    local service=$2

    echo -n "Port $port ($service): "
    if lsof -i:$port > /dev/null 2>&1; then
        echo -e "${GREEN}✅ In use${NC}"
        lsof -i:$port | grep LISTEN | awk '{print "  Process: " $1 " (PID: " $2 ")"}'
    else
        echo -e "${YELLOW}⚠️  Not in use${NC}"
    fi
}

check_port 5001 "Java Search Service"
check_port 8080 "Java Backend"
check_port 3000 "Frontend"
check_port 5432 "PostgreSQL"

echo ""
echo "============================================"
echo "Service URLs"
echo "============================================"
echo ""
echo "🔍 Java Search Service:"
echo "   Health: http://localhost:5001/actuator/health"
echo "   Metrics: http://localhost:5001/actuator/metrics"
echo "   Info: http://localhost:5001/actuator/info"
echo ""
echo "🚀 Java Backend:"
echo "   Health: http://localhost:8080/api/health"
echo "   API Base: http://localhost:8080/api"
echo ""
echo "🌐 Frontend:"
echo "   App: http://localhost:3000"
echo ""
echo "============================================"
echo "Quick Tests"
echo "============================================"
echo ""

# Test search service endpoint if running
if curl -s http://localhost:5001/actuator/health > /dev/null 2>&1; then
    echo "Testing Java Search Service info endpoint..."
    curl -s http://localhost:5001/actuator/info | head -20
    echo ""
fi

# Test backend endpoint if running
if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
    echo "Testing Java Backend health endpoint..."
    curl -s http://localhost:8080/api/health
    echo ""
fi

echo ""
echo "============================================"
echo "To start services:"
echo "============================================"
echo ""
echo "All at once:"
echo "  ./scripts/run-all-java-stack.sh"
echo ""
echo "Or manually in separate terminals:"
echo "  Terminal 1: ./scripts/run-java-search-service.sh"
echo "  Terminal 2: ./scripts/run-java-backend-with-java-search.sh"
echo "  Terminal 3: ./scripts/run-frontend-java.sh"
echo ""
