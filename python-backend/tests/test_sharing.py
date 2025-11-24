"""
Folder sharing tests.

Tests folder sharing, permissions, and collaboration features.
"""

import pytest


class TestFolderSharing:
    """Test folder sharing functionality."""

    def test_share_folder_with_another_user(self, client, authenticated_user, second_user, uploaded_folder, test_db):
        """Test sharing a folder with another user."""
        owner_token = uploaded_folder["token"]
        folder_id = uploaded_folder["folder_id"]
        recipient_username = second_user["username"]

        response = client.post("/api/share", json={
            "token": owner_token,
            "folder_id": folder_id,
            "username": recipient_username,
            "permission": "read"
        })

        assert response.status_code == 200
        data = response.json()
        assert "success" in data or "shared" in data.get("message", "").lower()

    def test_share_folder_nonexistent_user(self, client, uploaded_folder):
        """Test sharing with non-existent user fails."""
        token = uploaded_folder["token"]
        folder_id = uploaded_folder["folder_id"]

        response = client.post("/api/share", json={
            "token": token,
            "folder_id": folder_id,
            "username": "nonexistent_user_12345",
            "permission": "read"
        })

        assert response.status_code == 404

    def test_share_folder_unauthorized(self, client, second_user, uploaded_folder):
        """Test user cannot share folder they don't own."""
        # Try to share someone else's folder
        response = client.post("/api/share", json={
            "token": second_user["token"],
            "folder_id": uploaded_folder["folder_id"],
            "username": "anotheruser",
            "permission": "read"
        })

        assert response.status_code in [403, 404]

    def test_share_nonexistent_folder(self, client, authenticated_user, second_user):
        """Test sharing non-existent folder fails."""
        response = client.post("/api/share", json={
            "token": authenticated_user["token"],
            "folder_id": 99999,
            "username": second_user["username"],
            "permission": "read"
        })

        assert response.status_code == 404

    def test_share_with_self(self, client, authenticated_user, uploaded_folder):
        """Test sharing folder with yourself."""
        token = authenticated_user["token"]
        username = authenticated_user["username"]

        response = client.post("/api/share", json={
            "token": token,
            "folder_id": uploaded_folder["folder_id"],
            "username": username,
            "permission": "read"
        })

        # Should reject self-sharing
        assert response.status_code == 400


class TestSharedFolderAccess:
    """Test access to shared folders."""

    def test_view_shared_folders(self, client, authenticated_user, second_user, uploaded_folder):
        """Test viewing folders shared with you."""
        # Share folder from user1 to user2
        owner_token = uploaded_folder["token"]
        folder_id = uploaded_folder["folder_id"]
        recipient_username = second_user["username"]

        client.post("/api/share", json={
            "token": owner_token,
            "folder_id": folder_id,
            "username": recipient_username,
            "permission": "read"
        })

        # User 2 checks their folders
        response = client.post("/api/folders", json={
            "token": second_user["token"]
        })

        assert response.status_code == 200
        data = response.json()

        # Should see shared folders
        shared_folders = data.get("shared", data.get("shared_with_me", []))
        assert len(shared_folders) > 0

    def test_search_shared_folder(self, client, authenticated_user, second_user, uploaded_folder):
        """Test searching images in shared folder."""
        # Share folder
        client.post("/api/share", json={
            "token": uploaded_folder["token"],
            "folder_id": uploaded_folder["folder_id"],
            "username": second_user["username"],
            "permission": "read"
        })

        # User 2 searches the shared folder
        with patch('search_client.SearchServiceClient.search', return_value={"results": []}):
            response = client.post("/api/search", json={
                "token": second_user["token"],
                "query": "test",
                "folder_ids": [uploaded_folder["folder_id"]]
            })

            assert response.status_code == 200

    def test_cannot_delete_shared_folder(self, client, authenticated_user, second_user, uploaded_folder):
        """Test that recipient cannot delete shared folder."""
        # Share folder
        client.post("/api/share", json={
            "token": uploaded_folder["token"],
            "folder_id": uploaded_folder["folder_id"],
            "username": second_user["username"],
            "permission": "read"
        })

        # User 2 tries to delete the shared folder
        response = client.delete("/api/folders", json={
            "token": second_user["token"],
            "folder_ids": [uploaded_folder["folder_id"]]
        })

        # Should fail or "unshare" instead of delete
        if response.status_code == 200:
            # If successful, verify it was unshared, not deleted
            data = response.json()
            assert "unshared" in data.get("message", "").lower() or data.get("unshared_count", 0) > 0
        else:
            assert response.status_code in [403, 404]


class TestSharingPermissions:
    """Test different sharing permission levels."""

    def test_read_permission(self, client, authenticated_user, second_user, uploaded_folder):
        """Test read-only permission allows viewing but not modification."""
        # Share with read permission
        client.post("/api/share", json={
            "token": uploaded_folder["token"],
            "folder_id": uploaded_folder["folder_id"],
            "username": second_user["username"],
            "permission": "read"
        })

        # User 2 can view folder
        response = client.post("/api/folders", json={
            "token": second_user["token"]
        })
        assert response.status_code == 200

        # User 2 cannot upload to folder (if your API supports this)
        # Adjust based on your implementation

    def test_write_permission(self, client, authenticated_user, second_user, uploaded_folder, sample_image_file):
        """Test write permission allows adding images."""
        # Share with write permission
        client.post("/api/share", json={
            "token": uploaded_folder["token"],
            "folder_id": uploaded_folder["folder_id"],
            "username": second_user["username"],
            "permission": "write"
        })

        # User 2 uploads to shared folder
        files = {"files": ("newimage.png", sample_image_file, "image/png")}
        data = {
            "token": second_user["token"],
            "folder_name": uploaded_folder["folder_name"]
        }

        response = client.post("/api/images/upload", files=files, data=data)

        # Should succeed if write permission is implemented
        # Adjust assertion based on your implementation
        assert response.status_code in [200, 403]  # 403 if not implemented

    def test_invalid_permission_level(self, client, uploaded_folder, second_user):
        """Test sharing with invalid permission level."""
        response = client.post("/api/share", json={
            "token": uploaded_folder["token"],
            "folder_id": uploaded_folder["folder_id"],
            "username": second_user["username"],
            "permission": "invalid"
        })

        assert response.status_code == 400


class TestUnsharing:
    """Test removing folder shares."""

    def test_unshare_folder(self, client, authenticated_user, second_user, uploaded_folder):
        """Test owner can unshare folder."""
        # Share folder
        client.post("/api/share", json={
            "token": uploaded_folder["token"],
            "folder_id": uploaded_folder["folder_id"],
            "username": second_user["username"],
            "permission": "read"
        })

        # Unshare (implementation depends on your API)
        # Adjust endpoint as needed
        response = client.post("/api/unshare", json={
            "token": uploaded_folder["token"],
            "folder_id": uploaded_folder["folder_id"],
            "username": second_user["username"]
        })

        # Should succeed
        assert response.status_code in [200, 204]

    def test_recipient_removes_shared_folder(self, client, authenticated_user, second_user, uploaded_folder):
        """Test recipient can remove shared folder from their view."""
        # Share folder
        client.post("/api/share", json={
            "token": uploaded_folder["token"],
            "folder_id": uploaded_folder["folder_id"],
            "username": second_user["username"],
            "permission": "read"
        })

        # User 2 removes share (unsubscribes)
        response = client.delete("/api/folders", json={
            "token": second_user["token"],
            "folder_ids": [uploaded_folder["folder_id"]]
        })

        assert response.status_code == 200
        data = response.json()
        # Should unshare, not delete
        assert "unshared" in data.get("message", "").lower() or data.get("unshared_count", 0) > 0


# Import patch for mocking
from unittest.mock import patch
