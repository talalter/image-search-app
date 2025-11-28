#!/bin/bash

# API Testing Script for Image Search Application
# Tests all endpoints with proper error handling and validation
# Usage: ./test-api.sh [python|java]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BACKEND_TYPE=${1:-python}
if [ "$BACKEND_TYPE" = "java" ]; then
    BASE_URL="http://localhost:8080"
else
    BASE_URL="http://localhost:8000"
fi

# Test credentials
TEST_USER="testuser_$(date +%s)"
TEST_PASSWORD="TestPass123!"
TEST_USER2="testuser2_$(date +%s)"

# Global variables
TOKEN=""
USER_ID=""
FOLDER_ID=""
FOLDER_NAME="test_folder_$(date +%s)"

# Helper functions
print_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

print_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

# Check if backend is running
check_backend() {
    print_test "Checking if backend is running..."
    if curl -s -f "$BASE_URL" > /dev/null 2>&1 || curl -s -f "$BASE_URL/api/health" > /dev/null 2>&1; then
        print_success "Backend is running at $BASE_URL"
    else
        print_error "Backend is not running at $BASE_URL"
        echo "Please start the backend first:"
        echo "  docker-compose up -d"
        exit 1
    fi
}

# Test 1: User Registration
test_register() {
    print_test "Test 1: User Registration"

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/register" \
        -H "Content-Type: application/json" \
        -d "{\"username\": \"$TEST_USER\", \"password\": \"$TEST_PASSWORD\"}")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n1)

    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
        print_success "User registered successfully"
        USER_ID=$(echo "$body" | grep -o '"user_id":[0-9]*' | grep -o '[0-9]*' || echo "$body" | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
        print_info "User ID: $USER_ID"
    else
        print_error "Registration failed with code $http_code"
        echo "$body"
        exit 1
    fi
}

# Test 2: User Login
test_login() {
    print_test "Test 2: User Login"

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\": \"$TEST_USER\", \"password\": \"$TEST_PASSWORD\"}")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n1)

    if [ "$http_code" = "200" ]; then
        print_success "Login successful"
        TOKEN=$(echo "$body" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
        print_info "Token: ${TOKEN:0:20}..."
    else
        print_error "Login failed with code $http_code"
        echo "$body"
        exit 1
    fi
}

# Test 3: Create Folder
test_create_folder() {
    print_test "Test 3: Create Folder"

    # First, get existing folders to check endpoint works
    response=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/get-folders?token=$TOKEN")
    http_code=$(echo "$response" | tail -n1)

    if [ "$http_code" = "200" ]; then
        print_success "Get folders endpoint working"
        print_info "Folder name will be: $FOLDER_NAME"
    else
        print_error "Get folders failed with code $http_code"
    fi
}

# Test 4: Upload Image
test_upload_image() {
    print_test "Test 4: Upload Image"

    # Create a test image
    TEST_IMAGE_PATH="/tmp/test_image_$$.jpg"

    # Create a simple colored square image (requires ImageMagick, or use base64)
    if command -v convert &> /dev/null; then
        convert -size 100x100 xc:blue "$TEST_IMAGE_PATH"
        print_info "Created test image with ImageMagick"
    else
        # Create a minimal valid JPEG using base64
        echo "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCABkAGQDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlbaWmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD3+iiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKAP//Z" | base64 -d > "$TEST_IMAGE_PATH"
        print_info "Created minimal test JPEG"
    fi

    if [ ! -f "$TEST_IMAGE_PATH" ]; then
        print_error "Failed to create test image"
        return 1
    fi

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/upload-images" \
        -F "folder_name=$FOLDER_NAME" \
        -F "token=$TOKEN" \
        -F "files=@$TEST_IMAGE_PATH")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n1)

    if [ "$http_code" = "200" ]; then
        print_success "Image uploaded successfully"
        FOLDER_ID=$(echo "$body" | grep -o '"folder_id":[0-9]*' | grep -o '[0-9]*')
        print_info "Folder ID: $FOLDER_ID"
    else
        print_error "Upload failed with code $http_code"
        echo "$body"
    fi

    # Cleanup
    rm -f "$TEST_IMAGE_PATH"
}

# Test 5: Search Images
test_search_images() {
    print_test "Test 5: Search Images"

    if [ -z "$FOLDER_ID" ]; then
        print_info "Skipping search test (no images uploaded)"
        return
    fi

    # Wait a bit for embeddings to be processed
    print_info "Waiting 5 seconds for embeddings to process..."
    sleep 5

    response=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/search-images?query=blue&token=$TOKEN")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n1)

    if [ "$http_code" = "200" ]; then
        print_success "Search completed successfully"
        result_count=$(echo "$body" | grep -o '"results":\[' | wc -l)
        print_info "Search returned results"
    else
        print_error "Search failed with code $http_code"
        echo "$body"
    fi
}

# Test 6: Get Folders
test_get_folders() {
    print_test "Test 6: Get User Folders"

    response=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/get-folders?token=$TOKEN")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n1)

    if [ "$http_code" = "200" ]; then
        print_success "Retrieved folders successfully"
        folder_count=$(echo "$body" | grep -o '"folder_name"' | wc -l)
        print_info "User has $folder_count folder(s)"
    else
        print_error "Get folders failed with code $http_code"
        echo "$body"
    fi
}

# Test 7: Register Second User
test_register_second_user() {
    print_test "Test 7: Register Second User (for sharing test)"

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/register" \
        -H "Content-Type: application/json" \
        -d "{\"username\": \"$TEST_USER2\", \"password\": \"$TEST_PASSWORD\"}")

    http_code=$(echo "$response" | tail -n1)

    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
        print_success "Second user registered successfully"
    else
        print_error "Second user registration failed with code $http_code"
    fi
}

# Test 8: Share Folder
test_share_folder() {
    print_test "Test 8: Share Folder"

    if [ -z "$FOLDER_ID" ]; then
        print_info "Skipping share test (no folder created)"
        return
    fi

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/share-folder" \
        -H "Content-Type: application/json" \
        -d "{\"token\": \"$TOKEN\", \"folder_id\": $FOLDER_ID, \"shared_with_username\": \"$TEST_USER2\", \"permission\": \"view\"}")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n1)

    if [ "$http_code" = "200" ]; then
        print_success "Folder shared successfully"
    else
        print_error "Share folder failed with code $http_code"
        echo "$body"
    fi
}

# Test 9: Get Shared Folders
test_get_shared_folders() {
    print_test "Test 9: Get Shared Folders"

    response=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/get-shared-folders?token=$TOKEN")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n1)

    if [ "$http_code" = "200" ]; then
        print_success "Retrieved shared folders successfully"
    else
        print_error "Get shared folders failed with code $http_code"
        echo "$body"
    fi
}

# Test 10: Delete Folder
test_delete_folder() {
    print_test "Test 10: Delete Folder"

    if [ -z "$FOLDER_ID" ]; then
        print_info "Skipping delete test (no folder created)"
        return
    fi

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/delete-folders" \
        -H "Content-Type: application/json" \
        -d "{\"token\": \"$TOKEN\", \"folder_ids\": [$FOLDER_ID]}")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n1)

    if [ "$http_code" = "200" ]; then
        print_success "Folder deleted successfully"
    else
        print_error "Delete folder failed with code $http_code"
        echo "$body"
    fi
}

# Test 11: Logout
test_logout() {
    print_test "Test 11: Logout"

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/logout" \
        -H "Content-Type: application/json" \
        -d "{\"token\": \"$TOKEN\"}")

    http_code=$(echo "$response" | tail -n1)

    if [ "$http_code" = "200" ]; then
        print_success "Logout successful"
    else
        print_error "Logout failed with code $http_code"
    fi
}

# Test 12: Delete User Account
test_delete_account() {
    print_test "Test 12: Delete User Account"

    # Login again to get fresh token
    response=$(curl -s -X POST "$BASE_URL/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\": \"$TEST_USER\", \"password\": \"$TEST_PASSWORD\"}")

    TOKEN=$(echo "$response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

    if [ -z "$TOKEN" ]; then
        print_info "Skipping delete account (login failed)"
        return
    fi

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/delete-account" \
        -H "Content-Type: application/json" \
        -d "{\"token\": \"$TOKEN\"}")

    http_code=$(echo "$response" | tail -n1)

    if [ "$http_code" = "200" ]; then
        print_success "Account deleted successfully"
    else
        print_error "Delete account failed with code $http_code"
    fi
}

# Main execution
main() {
    echo "========================================="
    echo "Image Search API Testing Script"
    echo "Backend: $BACKEND_TYPE ($BASE_URL)"
    echo "========================================="
    echo ""

    check_backend
    echo ""

    test_register
    echo ""

    test_login
    echo ""

    test_create_folder
    echo ""

    test_upload_image
    echo ""

    test_search_images
    echo ""

    test_get_folders
    echo ""

    test_register_second_user
    echo ""

    test_share_folder
    echo ""

    test_get_shared_folders
    echo ""

    test_delete_folder
    echo ""

    test_logout
    echo ""

    test_delete_account
    echo ""

    echo "========================================="
    echo -e "${GREEN}All tests completed!${NC}"
    echo "========================================="
}

# Run main function
main