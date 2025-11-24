# Testing Documentation

Comprehensive testing guide for the Image Search Application.

## Table of Contents

- [Overview](#overview)
- [Python Backend Tests](#python-backend-tests)
- [Java Backend Tests](#java-backend-tests)
- [Running Tests](#running-tests)
- [Test Coverage](#test-coverage)
- [CI/CD Integration](#cicd-integration)
- [Best Practices](#best-practices)

---

## Overview

This project follows industry-standard testing practices with comprehensive test coverage across both backends.

### Testing Philosophy

- **Unit Tests**: Fast, isolated tests for individual components
- **Integration Tests**: Test interactions between components
- **Test Coverage**: Minimum 70% code coverage required
- **Test Isolation**: Each test is independent and can run in any order
- **Test Data**: Uses fixtures and mocks to avoid external dependencies

### Test Structure

```
image-search-app/
├── python-backend/
│   ├── tests/
│   │   ├── __init__.py
│   │   ├── conftest.py                 # Shared fixtures
│   │   ├── test_authentication.py      # Auth tests
│   │   ├── test_images.py              # Image upload tests
│   │   ├── test_search.py              # Search tests
│   │   └── test_sharing.py             # Sharing tests
│   ├── pytest.ini                      # Pytest configuration
│   └── requirements-test.txt           # Test dependencies
│
└── java-backend/
    ├── src/test/java/
    │   └── com/imagesearch/
    │       ├── controller/
    │       │   ├── UserControllerTest.java
    │       │   └── ImageControllerTest.java
    │       └── service/
    │           └── SearchServiceTest.java
    ├── src/test/resources/
    │   └── application-test.properties  # Test configuration
    └── build.gradle                     # Jacoco coverage config
```

---

## Python Backend Tests

### Prerequisites

Install test dependencies:

```bash
cd python-backend
pip install -r requirements-test.txt
```

### Test Categories

#### 1. Authentication Tests (`test_authentication.py`)

Tests user registration, login, and security:

- ✅ Successful registration
- ✅ Duplicate username rejection
- ✅ Weak password rejection
- ✅ Successful login with valid credentials
- ✅ Failed login with invalid credentials
- ✅ Password hashing (BCrypt)
- ✅ SQL injection protection
- ✅ XSS protection
- ✅ Session management

**Example:**
```python
def test_register_success(client):
    response = client.post("/api/register", json={
        "username": "newuser",
        "password": "SecurePass123!"
    })
    assert response.status_code == 200
```

#### 2. Image Upload Tests (`test_images.py`)

Tests image upload and folder management:

- ✅ Single image upload
- ✅ Multiple image upload (batch)
- ✅ Upload to existing folder
- ✅ Authentication checks
- ✅ File type validation (PNG, JPEG only)
- ✅ Folder isolation (users can't access others' folders)
- ✅ Folder deletion
- ✅ Large file handling

**Example:**
```python
def test_upload_single_image(client, authenticated_user, sample_image_file):
    files = {"files": ("test.png", sample_image_file, "image/png")}
    data = {"token": authenticated_user["token"], "folder_name": "my_photos"}

    response = client.post("/api/images/upload", files=files, data=data)
    assert response.status_code == 200
    assert response.json()["uploaded_count"] == 1
```

#### 3. Search Tests (`test_search.py`)

Tests semantic search functionality:

- ✅ Text-based image search
- ✅ Search across multiple folders
- ✅ Permission checks (can only search accessible folders)
- ✅ Empty query handling
- ✅ No results handling
- ✅ Search service integration
- ✅ Embedding generation on upload
- ✅ Index creation/deletion

**Example:**
```python
@patch('search_client.SearchServiceClient.search')
def test_search_with_text_query(mock_search, client, authenticated_user):
    mock_search.return_value = {"results": [{"image_id": 1, "score": 0.95}]}

    response = client.post("/api/search", json={
        "token": authenticated_user["token"],
        "query": "red sunset",
        "folder_ids": [1]
    })
    assert response.status_code == 200
```

#### 4. Sharing Tests (`test_sharing.py`)

Tests folder sharing and collaboration:

- ✅ Share folder with another user
- ✅ Different permission levels (read/write)
- ✅ Access shared folders
- ✅ Search in shared folders
- ✅ Cannot delete shared folders (only unshare)
- ✅ Unshare/remove shares
- ✅ Share validation (can't share with self, non-existent users)

### Fixtures (conftest.py)

Shared test fixtures available to all tests:

- `test_db`: In-memory SQLite database (isolated per test)
- `client`: FastAPI test client
- `authenticated_user`: Pre-registered and logged-in user
- `second_user`: Second user for sharing tests
- `sample_image_file`: Test image (1x1 PNG)
- `uploaded_folder`: Folder with uploaded images
- `temp_upload_dir`: Temporary directory for file operations

### Running Python Tests

```bash
cd python-backend

# Run all tests
pytest

# Run specific test file
pytest tests/test_authentication.py

# Run specific test
pytest tests/test_authentication.py::TestRegistration::test_register_success

# Run with coverage report
pytest --cov=. --cov-report=html

# Run tests by marker
pytest -m auth          # Authentication tests only
pytest -m integration   # Integration tests only
pytest -m "not slow"    # Skip slow tests
```

### Test Markers

Categorize tests for selective execution:

```python
@pytest.mark.unit
@pytest.mark.auth
@pytest.mark.security
@pytest.mark.slow
@pytest.mark.integration
```

---

## Java Backend Tests

### Prerequisites

Java tests use Gradle and JUnit 5:

```bash
cd java-backend
./gradlew build
```

### Test Categories

#### 1. UserControllerTest

Tests authentication endpoints:

- ✅ User registration
- ✅ User login
- ✅ Input validation
- ✅ Duplicate username handling
- ✅ Weak password rejection
- ✅ SQL injection protection
- ✅ XSS protection

**Example:**
```java
@Test
@DisplayName("Should successfully register a new user")
void testRegisterSuccess() throws Exception {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("testuser");
    request.setPassword("SecurePass123!");

    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());
}
```

#### 2. ImageControllerTest

Tests image upload functionality:

- ✅ Single image upload
- ✅ Multiple image upload
- ✅ Authentication checks
- ✅ File type validation
- ✅ Empty folder name rejection
- ✅ Large file handling
- ✅ PNG/JPEG acceptance

**Example:**
```java
@Test
@DisplayName("Should successfully upload multiple images")
void testUploadMultipleImages() throws Exception {
    MockMultipartFile file1 = new MockMultipartFile(
        "files", "test1.png", MediaType.IMAGE_PNG_VALUE, "image1".getBytes()
    );
    MockMultipartFile file2 = new MockMultipartFile(
        "files", "test2.png", MediaType.IMAGE_PNG_VALUE, "image2".getBytes()
    );

    mockMvc.perform(multipart("/api/images/upload")
            .file(file1).file(file2)
            .param("token", TEST_TOKEN)
            .param("folderName", "batch_upload"))
            .andExpect(status().isOk());
}
```

#### 3. SearchServiceTest

Unit tests for search service logic:

- ✅ Search with valid query
- ✅ Empty results handling
- ✅ Multiple folder search
- ✅ Permission checks
- ✅ Search service unavailable handling
- ✅ Missing image handling
- ✅ Result mapping
- ✅ Score-based sorting

**Example:**
```java
@Test
@DisplayName("Should return search results for valid query")
void testSearchSuccess() {
    List<Long> folderIds = Arrays.asList(1L);
    when(folderService.getUserAccessibleFolders(userId, folderIds))
        .thenReturn(Arrays.asList(testFolder));

    SearchServiceResponse response = new SearchServiceResponse();
    response.setResults(Arrays.asList(resultItem));
    when(pythonSearchClient.search(eq("sunset"), anyMap()))
        .thenReturn(response);

    var results = searchService.search(userId, "sunset", folderIds, 5);
    assertThat(results.getResults()).hasSize(1);
}
```

### Running Java Tests

```bash
cd java-backend

# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests UserControllerTest

# Run specific test method
./gradlew test --tests UserControllerTest.testRegisterSuccess

# Run tests with coverage report
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

### Test Configuration

**application-test.properties:**
- Uses H2 in-memory database
- Disables caching
- Mock search service URL
- Test storage paths
- Reduced logging

---

## Test Coverage

### Coverage Requirements

- **Minimum**: 70% code coverage
- **Excluded**: DTOs, configuration classes, main application class
- **Reports**: HTML and XML formats

### Python Coverage

```bash
cd python-backend

# Generate coverage report
pytest --cov=. --cov-report=html --cov-report=term-missing

# View HTML report
open htmlcov/index.html

# Check coverage threshold
pytest --cov=. --cov-fail-under=70
```

Coverage is configured in `pytest.ini`:
```ini
[pytest]
addopts = --cov=. --cov-report=html --cov-report=term-missing
```

### Java Coverage (Jacoco)

```bash
cd java-backend

# Run tests with coverage
./gradlew test jacocoTestReport

# View report
open build/reports/jacoco/test/html/index.html

# Verify minimum coverage (70%)
./gradlew jacocoTestCoverageVerification
```

Coverage is configured in `build.gradle`:
```gradle
jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.70  // 70% minimum
            }
        }
    }
}
```

---

## CI/CD Integration

### GitHub Actions Example

Create `.github/workflows/test.yml`:

```yaml
name: Run Tests

on: [push, pull_request]

jobs:
  python-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.12'
      - name: Install dependencies
        run: |
          cd python-backend
          pip install -r requirements.txt
          pip install -r requirements-test.txt
      - name: Run tests
        run: |
          cd python-backend
          pytest --cov=. --cov-report=xml
      - name: Upload coverage
        uses: codecov/codecov-action@v3

  java-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run tests
        run: |
          cd java-backend
          ./gradlew test jacocoTestReport
      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

### Pre-commit Hook

Add to `.git/hooks/pre-commit`:

```bash
#!/bin/bash

echo "Running Python tests..."
cd python-backend && pytest -q
if [ $? -ne 0 ]; then
    echo "Python tests failed!"
    exit 1
fi

echo "Running Java tests..."
cd ../java-backend && ./gradlew test -q
if [ $? -ne 0 ]; then
    echo "Java tests failed!"
    exit 1
fi

echo "All tests passed!"
```

---

## Best Practices

### Writing Good Tests

#### 1. Test Naming

**Python:**
```python
def test_register_success(client):  # Clear, descriptive name
def test_login_wrong_password(client):  # Describes expected behavior
```

**Java:**
```java
@DisplayName("Should successfully register a new user")
void testRegisterSuccess() { }  // Readable in reports
```

#### 2. AAA Pattern

Structure tests with **Arrange, Act, Assert**:

```python
def test_upload_image(client, authenticated_user):
    # Arrange
    token = authenticated_user["token"]
    files = {"files": ("test.png", sample_image, "image/png")}

    # Act
    response = client.post("/api/images/upload", files=files, data={"token": token})

    # Assert
    assert response.status_code == 200
    assert response.json()["uploaded_count"] == 1
```

#### 3. Test Isolation

Each test should be independent:

```python
@pytest.fixture(scope="function")  # New database per test
def test_db():
    # Setup
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)

    yield db

    # Teardown
    Base.metadata.drop_all(engine)
```

#### 4. Use Mocks for External Services

```python
@patch('search_client.SearchServiceClient.search')
def test_search(mock_search, client):
    mock_search.return_value = {"results": []}
    # Test continues without actual search service
```

#### 5. Test Edge Cases

```python
def test_upload_empty_folder_name(client):  # Edge case
def test_search_with_no_results(client):    # Edge case
def test_large_file_upload(client):         # Boundary condition
```

### Common Pitfalls to Avoid

❌ **Don't test implementation details**
```python
# Bad
def test_password_hash_algorithm():
    assert "bcrypt" in hash_function.__name__

# Good
def test_password_is_hashed():
    user = register_user("user", "pass")
    assert user.password != "pass"  # Tests behavior, not implementation
```

❌ **Don't share state between tests**
```python
# Bad
test_data = []  # Global state

def test_one():
    test_data.append(1)  # Affects other tests

# Good
@pytest.fixture
def test_data():
    return []  # Fresh data per test
```

❌ **Don't use hardcoded IDs**
```python
# Bad
def test_get_user():
    user = get_user(id=123)  # Hardcoded ID

# Good
def test_get_user(authenticated_user):
    user = get_user(id=authenticated_user["id"])  # Dynamic ID
```

### Test Maintenance

- **Run tests frequently**: Before commits, during development
- **Keep tests fast**: Use mocks, in-memory databases
- **Update tests with code changes**: Tests are code too
- **Review test failures**: Don't ignore flaky tests
- **Refactor tests**: Apply DRY principles to test code

---

## Troubleshooting

### Common Issues

**Python: ModuleNotFoundError**
```bash
# Solution: Ensure PYTHONPATH includes project root
cd python-backend
export PYTHONPATH=.
pytest
```

**Java: Tests not found**
```bash
# Solution: Clean and rebuild
./gradlew clean test
```

**Coverage too low**
```bash
# Solution: Check uncovered lines
pytest --cov=. --cov-report=term-missing
# Add tests for uncovered code
```

**Flaky tests (sometimes pass/fail)**
```bash
# Solution: Check for:
# - Shared state between tests
# - Race conditions
# - External dependencies
# - Non-deterministic code (random, time)
```

---

## Summary

This testing suite provides:

✅ **Comprehensive coverage** of all major features
✅ **Fast execution** with in-memory databases and mocks
✅ **Clear documentation** for maintainability
✅ **CI/CD ready** for automated testing
✅ **Best practices** following industry standards

### Quick Reference

```bash
# Python Backend
cd python-backend
pytest                              # Run all tests
pytest -v                           # Verbose output
pytest --cov                        # With coverage
pytest -m auth                      # Only auth tests

# Java Backend
cd java-backend
./gradlew test                      # Run all tests
./gradlew test --tests UserControllerTest  # Specific class
./gradlew jacocoTestReport          # Coverage report
```

### Next Steps

1. Run tests locally to verify setup
2. Add tests to CI/CD pipeline
3. Set up code coverage badges
4. Add integration tests for end-to-end flows
5. Consider performance/load testing with Locust

For questions or issues, see the main [README.md](../README.md) or open an issue on GitHub.
