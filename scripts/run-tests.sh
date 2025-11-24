#!/bin/bash
# Run tests for Image Search Application
# Usage: ./scripts/run-tests.sh [python|java|all]

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "================================================"
echo "  Image Search App - Test Runner"
echo "================================================"
echo ""

# Parse arguments
TEST_TARGET="${1:-all}"

run_python_tests() {
    echo -e "${BLUE}Running Python Backend Tests...${NC}"
    echo ""

    cd python-backend

    # Check if test dependencies are installed
    if ! python -c "import pytest" 2>/dev/null; then
        echo "Installing test dependencies..."
        pip install -r requirements-test.txt
    fi

    # Run tests with coverage
    pytest -v --cov=. --cov-report=html --cov-report=term-missing

    if [ $? -eq 0 ]; then
        echo ""
        echo -e "${GREEN}✓ Python tests passed!${NC}"
        echo -e "Coverage report: python-backend/htmlcov/index.html"
    else
        echo ""
        echo -e "${RED}✗ Python tests failed!${NC}"
        exit 1
    fi

    cd ..
}

run_java_tests() {
    echo -e "${BLUE}Running Java Backend Tests...${NC}"
    echo ""

    cd java-backend

    # Run tests with Gradle
    ./gradlew test jacocoTestReport

    if [ $? -eq 0 ]; then
        echo ""
        echo -e "${GREEN}✓ Java tests passed!${NC}"
        echo -e "Coverage report: java-backend/build/reports/jacoco/test/html/index.html"
    else
        echo ""
        echo -e "${RED}✗ Java tests failed!${NC}"
        exit 1
    fi

    cd ..
}

# Run tests based on argument
case "$TEST_TARGET" in
    python)
        run_python_tests
        ;;
    java)
        run_java_tests
        ;;
    all)
        run_python_tests
        echo ""
        echo "================================================"
        echo ""
        run_java_tests
        ;;
    *)
        echo "Usage: $0 [python|java|all]"
        echo ""
        echo "Examples:"
        echo "  $0           # Run all tests"
        echo "  $0 python    # Run Python tests only"
        echo "  $0 java      # Run Java tests only"
        exit 1
        ;;
esac

echo ""
echo "================================================"
echo -e "${GREEN}✓ All tests completed successfully!${NC}"
echo "================================================"
echo ""
echo "View coverage reports:"
echo "  Python: python-backend/htmlcov/index.html"
echo "  Java:   java-backend/build/reports/jacoco/test/html/index.html"
echo ""
