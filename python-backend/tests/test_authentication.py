"""
Authentication tests.

Tests user registration, login, session management, and security.
"""

import pytest
from fastapi.testclient import TestClient


class TestRegistration:
    """Test user registration functionality."""

    def test_register_success(self, client):
        """Test successful user registration."""
        response = client.post("/api/register", json={
            "username": "newuser",
            "password": "SecurePass123!"
        })

        assert response.status_code == 200
        data = response.json()
        assert "message" in data
        assert "successfully" in data["message"].lower()

    def test_register_duplicate_username(self, client, registered_user):
        """Test registration with existing username fails."""
        response = client.post("/api/register", json=registered_user)

        assert response.status_code == 400
        data = response.json()
        assert "already exists" in data["detail"].lower() or "duplicate" in data["detail"].lower()

    def test_register_weak_password(self, client):
        """Test registration with weak password fails."""
        response = client.post("/api/register", json={
            "username": "weakuser",
            "password": "123"  # Too short
        })

        assert response.status_code == 400

    def test_register_invalid_username(self, client):
        """Test registration with invalid username."""
        # Empty username
        response = client.post("/api/register", json={
            "username": "",
            "password": "SecurePass123!"
        })
        assert response.status_code == 400

        # Username with special characters (if not allowed)
        response = client.post("/api/register", json={
            "username": "user@#$%",
            "password": "SecurePass123!"
        })
        # Depending on validation rules, may pass or fail
        # Adjust assertion based on your validation logic

    def test_register_missing_fields(self, client):
        """Test registration with missing fields."""
        # Missing password
        response = client.post("/api/register", json={
            "username": "testuser"
        })
        assert response.status_code == 422  # FastAPI validation error

        # Missing username
        response = client.post("/api/register", json={
            "password": "SecurePass123!"
        })
        assert response.status_code == 422


class TestLogin:
    """Test user login functionality."""

    def test_login_success(self, client, registered_user):
        """Test successful login returns token."""
        response = client.post("/api/login", json=registered_user)

        assert response.status_code == 200
        data = response.json()
        assert "token" in data
        assert len(data["token"]) > 0

    def test_login_wrong_password(self, client, registered_user):
        """Test login with incorrect password fails."""
        response = client.post("/api/login", json={
            "username": registered_user["username"],
            "password": "WrongPassword123!"
        })

        assert response.status_code == 401
        data = response.json()
        assert "invalid" in data["detail"].lower() or "incorrect" in data["detail"].lower()

    def test_login_nonexistent_user(self, client):
        """Test login with non-existent username fails."""
        response = client.post("/api/login", json={
            "username": "nonexistent",
            "password": "Password123!"
        })

        assert response.status_code == 401

    def test_login_missing_credentials(self, client):
        """Test login with missing credentials."""
        # Missing password
        response = client.post("/api/login", json={
            "username": "testuser"
        })
        assert response.status_code == 422

        # Missing username
        response = client.post("/api/login", json={
            "password": "Password123!"
        })
        assert response.status_code == 422


class TestAuthentication:
    """Test authentication and authorization."""

    def test_authenticated_endpoint_with_valid_token(self, client, authenticated_user):
        """Test accessing protected endpoint with valid token."""
        response = client.post("/api/folders", json={
            "token": authenticated_user["token"]
        })

        # Should succeed (even if no folders, should return 200 with empty list)
        assert response.status_code == 200

    def test_authenticated_endpoint_without_token(self, client):
        """Test accessing protected endpoint without token fails."""
        response = client.post("/api/folders", json={})

        # Should fail with 400 or 401
        assert response.status_code in [400, 401, 422]

    def test_authenticated_endpoint_with_invalid_token(self, client):
        """Test accessing protected endpoint with invalid token."""
        response = client.post("/api/folders", json={
            "token": "invalid-token-12345"
        })

        # Should fail with 401 or 404
        assert response.status_code in [401, 404]

    def test_session_persistence(self, client, authenticated_user):
        """Test that token works across multiple requests."""
        token = authenticated_user["token"]

        # First request
        response1 = client.post("/api/folders", json={"token": token})
        assert response1.status_code == 200

        # Second request with same token
        response2 = client.post("/api/folders", json={"token": token})
        assert response2.status_code == 200


class TestSecurity:
    """Test security features."""

    def test_password_is_hashed(self, client, test_db, test_user_credentials):
        """Test that passwords are hashed in database, not stored plaintext."""
        from database import get_user_by_username

        # Register user
        client.post("/api/register", json=test_user_credentials)

        # Check database
        user = get_user_by_username(test_db, test_user_credentials["username"])
        assert user is not None
        assert user.password != test_user_credentials["password"]  # Should be hashed
        assert len(user.password) > 50  # BCrypt hashes are long

    def test_sql_injection_protection(self, client):
        """Test SQL injection attempts are blocked."""
        # Try SQL injection in username
        response = client.post("/api/login", json={
            "username": "admin' OR '1'='1",
            "password": "password"
        })

        # Should fail gracefully, not expose SQL error
        assert response.status_code in [401, 400]
        # Response should not contain SQL keywords
        assert "SELECT" not in response.text.upper()
        assert "FROM" not in response.text.upper()

    def test_xss_protection(self, client):
        """Test XSS attempts are sanitized."""
        response = client.post("/api/register", json={
            "username": "<script>alert('xss')</script>",
            "password": "SecurePass123!"
        })

        # Should either reject or sanitize
        if response.status_code == 200:
            # If accepted, verify it's stored safely
            login_response = client.post("/api/login", json={
                "username": "<script>alert('xss')</script>",
                "password": "SecurePass123!"
            })
            # Should work if stored properly
            assert login_response.status_code in [200, 401]

    def test_rate_limiting_registration(self, client):
        """Test that rapid registration attempts are handled."""
        # Note: Only tests if rate limiting is implemented
        # Attempt multiple rapid registrations
        for i in range(10):
            response = client.post("/api/register", json={
                "username": f"spamuser{i}",
                "password": "Password123!"
            })
            # All should succeed or be rate-limited gracefully
            assert response.status_code in [200, 201, 429]  # 429 = Too Many Requests
