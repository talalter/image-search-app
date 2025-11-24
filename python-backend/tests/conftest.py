"""
Pytest configuration and shared fixtures.

Provides test database, test client, and authentication helpers.
"""

import pytest
import sys
import os
from pathlib import Path

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
import tempfile
import shutil

# Import app components
from api import app
from database import Base, get_db


# ============== Database Fixtures ==============

@pytest.fixture(scope="function")
def test_db():
    """
    Create an in-memory SQLite database for each test.
    Automatically rolls back after each test.
    """
    # Create in-memory database
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )

    # Create all tables
    Base.metadata.create_all(bind=engine)

    # Create session
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    db = TestingSessionLocal()

    try:
        yield db
    finally:
        db.close()
        Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="function")
def client(test_db):
    """
    FastAPI test client with test database dependency override.
    """
    def override_get_db():
        try:
            yield test_db
        finally:
            pass

    app.dependency_overrides[get_db] = override_get_db

    with TestClient(app) as test_client:
        yield test_client

    app.dependency_overrides.clear()


# ============== File System Fixtures ==============

@pytest.fixture(scope="function")
def temp_upload_dir():
    """
    Create a temporary directory for file uploads during tests.
    Automatically cleaned up after each test.
    """
    temp_dir = tempfile.mkdtemp(prefix="test_uploads_")

    # Set environment variable for upload path
    original_storage = os.environ.get("STORAGE_BACKEND")
    os.environ["STORAGE_BACKEND"] = "local"

    yield Path(temp_dir)

    # Cleanup
    shutil.rmtree(temp_dir, ignore_errors=True)
    if original_storage:
        os.environ["STORAGE_BACKEND"] = original_storage
    else:
        os.environ.pop("STORAGE_BACKEND", None)


# ============== Authentication Fixtures ==============

@pytest.fixture
def test_user_credentials():
    """Standard test user credentials."""
    return {
        "username": "testuser",
        "password": "Test123!@#"
    }


@pytest.fixture
def registered_user(client, test_user_credentials):
    """
    Register a test user and return credentials.
    """
    response = client.post("/api/register", json=test_user_credentials)
    assert response.status_code == 200
    return test_user_credentials


@pytest.fixture
def authenticated_user(client, registered_user):
    """
    Register and login a test user, return auth token.
    """
    response = client.post("/api/login", json=registered_user)
    assert response.status_code == 200
    data = response.json()
    return {
        "token": data["token"],
        "username": registered_user["username"]
    }


@pytest.fixture
def second_user(client):
    """
    Create a second test user for sharing tests.
    """
    credentials = {
        "username": "testuser2",
        "password": "Test456!@#"
    }
    response = client.post("/api/register", json=credentials)
    assert response.status_code == 200

    # Login to get token
    response = client.post("/api/login", json=credentials)
    assert response.status_code == 200
    data = response.json()

    return {
        "token": data["token"],
        "username": credentials["username"]
    }


# ============== Test Data Fixtures ==============

@pytest.fixture
def sample_image_file():
    """
    Create a simple test image file (1x1 pixel PNG).
    """
    from io import BytesIO
    from PIL import Image

    # Create a 1x1 red pixel image
    img = Image.new('RGB', (1, 1), color='red')
    img_bytes = BytesIO()
    img.save(img_bytes, format='PNG')
    img_bytes.seek(0)

    return img_bytes


@pytest.fixture
def uploaded_folder(client, authenticated_user, sample_image_file):
    """
    Create a folder with uploaded images for testing.
    Returns folder_id and image metadata.
    """
    token = authenticated_user["token"]

    # Upload image to create folder
    files = {
        "files": ("test_image.png", sample_image_file, "image/png")
    }
    data = {
        "token": token,
        "folder_name": "test_folder"
    }

    response = client.post("/api/images/upload", files=files, data=data)
    assert response.status_code == 200

    response_data = response.json()
    return {
        "folder_id": response_data.get("folder_id"),
        "folder_name": "test_folder",
        "token": token
    }
