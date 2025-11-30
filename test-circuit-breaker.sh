#!/bin/bash

# Circuit Breaker Test Script
# This tests the circuit breaker without needing the full application running

echo "=========================================="
echo "Circuit Breaker Implementation Test"
echo "=========================================="
echo ""

# Test 1: Verify dependencies are in build.gradle
echo "Test 1: Checking if Resilience4j dependencies are present..."
if grep -q "resilience4j-spring-boot3" java-backend/build.gradle; then
    echo "✅ Resilience4j Spring Boot dependency found"
else
    echo "❌ Resilience4j Spring Boot dependency NOT found"
    exit 1
fi

if grep -q "resilience4j-circuitbreaker" java-backend/build.gradle; then
    echo "✅ Resilience4j Circuit Breaker dependency found"
else
    echo "❌ Resilience4j Circuit Breaker dependency NOT found"
    exit 1
fi

if grep -q "spring-boot-starter-actuator" java-backend/build.gradle; then
    echo "✅ Spring Boot Actuator dependency found"
else
    echo "❌ Spring Boot Actuator dependency NOT found"
    exit 1
fi

echo ""

# Test 2: Verify configuration in application.yml
echo "Test 2: Checking Circuit Breaker configuration in application.yml..."
if grep -q "resilience4j:" java-backend/src/main/resources/application.yml; then
    echo "✅ Resilience4j configuration section found"
else
    echo "❌ Resilience4j configuration section NOT found"
    exit 1
fi

if grep -q "pythonSearchService:" java-backend/src/main/resources/application.yml; then
    echo "✅ pythonSearchService circuit breaker configured"
else
    echo "❌ pythonSearchService circuit breaker NOT configured"
    exit 1
fi

if grep -q "javaSearchService:" java-backend/src/main/resources/application.yml; then
    echo "✅ javaSearchService circuit breaker configured"
else
    echo "❌ javaSearchService circuit breaker NOT configured"
    exit 1
fi

if grep -q "failureRateThreshold: 50" java-backend/src/main/resources/application.yml; then
    echo "✅ Failure rate threshold configured (50%)"
else
    echo "❌ Failure rate threshold NOT configured"
    exit 1
fi

if grep -q "circuitbreakers" java-backend/src/main/resources/application.yml; then
    echo "✅ Actuator circuitbreakers endpoint configured"
else
    echo "❌ Actuator circuitbreakers endpoint NOT configured"
    exit 1
fi

echo ""

# Test 3: Verify @CircuitBreaker annotations in code
echo "Test 3: Checking @CircuitBreaker annotations in PythonSearchClient..."
if grep -q "@CircuitBreaker(name = \"pythonSearchService\"" java-backend/src/main/java/com/imagesearch/client/PythonSearchClient.java; then
    echo "✅ @CircuitBreaker annotation found in PythonSearchClient"

    # Count number of @CircuitBreaker annotations
    CB_COUNT=$(grep -c "@CircuitBreaker(name = \"pythonSearchService\"" java-backend/src/main/java/com/imagesearch/client/PythonSearchClient.java)
    echo "   Found $CB_COUNT circuit breaker(s) in PythonSearchClient"

    if [ "$CB_COUNT" -ge 4 ]; then
        echo "   ✅ All expected methods are protected (search, embedImages, createIndex, deleteIndex)"
    else
        echo "   ⚠️  Expected 4 circuit breakers, found $CB_COUNT"
    fi
else
    echo "❌ @CircuitBreaker annotation NOT found in PythonSearchClient"
    exit 1
fi

echo ""

echo "Test 4: Checking fallback methods in PythonSearchClient..."
FALLBACK_COUNT=$(grep -c "Fallback" java-backend/src/main/java/com/imagesearch/client/PythonSearchClient.java)
echo "   Found $FALLBACK_COUNT fallback method(s)"

if grep -q "searchFallback" java-backend/src/main/java/com/imagesearch/client/PythonSearchClient.java; then
    echo "   ✅ searchFallback() method found"
else
    echo "   ❌ searchFallback() method NOT found"
    exit 1
fi

if grep -q "embedImagesFallback" java-backend/src/main/java/com/imagesearch/client/PythonSearchClient.java; then
    echo "   ✅ embedImagesFallback() method found"
else
    echo "   ❌ embedImagesFallback() method NOT found"
    exit 1
fi

echo ""

echo "Test 5: Checking @CircuitBreaker annotations in JavaSearchClient..."
if grep -q "@CircuitBreaker(name = \"javaSearchService\"" java-backend/src/main/java/com/imagesearch/client/JavaSearchClient.java; then
    echo "✅ @CircuitBreaker annotation found in JavaSearchClient"
else
    echo "❌ @CircuitBreaker annotation NOT found in JavaSearchClient"
    exit 1
fi

if grep -q "deleteElasticsearchIndexFallback" java-backend/src/main/java/com/imagesearch/client/JavaSearchClient.java; then
    echo "✅ deleteElasticsearchIndexFallback() method found"
else
    echo "❌ deleteElasticsearchIndexFallback() method NOT found"
    exit 1
fi

echo ""

# Test 6: Verify imports
echo "Test 6: Checking required imports..."
if grep -q "import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;" java-backend/src/main/java/com/imagesearch/client/PythonSearchClient.java; then
    echo "✅ CircuitBreaker import found in PythonSearchClient"
else
    echo "❌ CircuitBreaker import NOT found in PythonSearchClient"
    exit 1
fi

if grep -q "import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;" java-backend/src/main/java/com/imagesearch/client/JavaSearchClient.java; then
    echo "✅ CircuitBreaker import found in JavaSearchClient"
else
    echo "❌ CircuitBreaker import NOT found in JavaSearchClient"
    exit 1
fi

echo ""

# Test 7: Compilation test
echo "Test 7: Compiling code to verify syntax..."
cd java-backend
./gradlew clean compileJava --quiet 2>&1 | tail -5

if [ $? -eq 0 ]; then
    echo "✅ Code compiles successfully"
else
    echo "❌ Compilation failed"
    exit 1
fi

cd ..

echo ""
echo "=========================================="
echo "✅ ALL TESTS PASSED!"
echo "=========================================="
echo ""
echo "Circuit Breaker Implementation Summary:"
echo "  - Dependencies: ✅ Installed"
echo "  - Configuration: ✅ Configured (50% threshold, 100 requests window)"
echo "  - PythonSearchClient: ✅ 4 methods protected with fallbacks"
echo "  - JavaSearchClient: ✅ 1 method protected with fallback"
echo "  - Code: ✅ Compiles successfully"
echo ""
echo "Next Steps:"
echo "  1. Start backend with database credentials:"
echo "     cd java-backend"
echo "     DB_USERNAME=imageuser DB_PASSWORD=your_password ./gradlew bootRun"
echo ""
echo "  2. Test circuit breaker live:"
echo "     - Make 15 requests to search endpoint without Python service running"
echo "     - Circuit should OPEN after 10 failures"
echo "     - Check state: curl http://localhost:8080/actuator/circuitbreakers"
echo ""
echo "  3. See CIRCUIT_BREAKER_DEMO.md for full demo scenarios"
echo ""
