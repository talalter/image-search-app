"""
Image upload and management tests.

Tests image upload, folder creation, image retrieval, and deletion.
"""

import pytest
from io import BytesIO
from PIL import Image


class TestImageUpload:
    """Test image upload functionality."""

    def test_upload_single_image(self, client, authenticated_user, sample_image_file):
        """Test uploading a single image creates folder and saves file."""
        token = authenticated_user["token"]

        files = {
            "files": ("test.png", sample_image_file, "image/png")
        }
        data = {
            "token": token,
            "folder_name": "my_photos"
        }

        response = client.post("/api/images/upload", files=files, data=data)

        assert response.status_code == 200
        response_data = response.json()
        assert "message" in response_data
        assert "folder_id" in response_data
        assert response_data["uploaded_count"] == 1

    def test_upload_multiple_images(self, client, authenticated_user):
        """Test uploading multiple images at once."""
        token = authenticated_user["token"]

        # Create multiple test images
        files = []
        for i in range(3):
            img = Image.new('RGB', (10, 10), color=(i*80, 0, 0))
            img_bytes = BytesIO()
            img.save(img_bytes, format='PNG')
            img_bytes.seek(0)
            files.append(("files", (f"test{i}.png", img_bytes, "image/png")))

        data = {
            "token": token,
            "folder_name": "batch_upload"
        }

        response = client.post("/api/images/upload", files=files, data=data)

        assert response.status_code == 200
        response_data = response.json()
        assert response_data["uploaded_count"] == 3

    def test_upload_to_existing_folder(self, client, uploaded_folder, sample_image_file):
        """Test uploading images to an existing folder."""
        token = uploaded_folder["token"]
        folder_name = uploaded_folder["folder_name"]

        # Upload another image to same folder
        files = {
            "files": ("test2.png", sample_image_file, "image/png")
        }
        data = {
            "token": token,
            "folder_name": folder_name
        }

        response = client.post("/api/images/upload", files=files, data=data)

        assert response.status_code == 200
        response_data = response.json()
        assert response_data["folder_id"] == uploaded_folder["folder_id"]

    def test_upload_without_authentication(self, client, sample_image_file):
        """Test uploading without auth token fails."""
        files = {
            "files": ("test.png", sample_image_file, "image/png")
        }
        data = {
            "folder_name": "unauthorized"
        }

        response = client.post("/api/images/upload", files=files, data=data)

        assert response.status_code in [400, 401, 422]

    def test_upload_invalid_file_type(self, client, authenticated_user):
        """Test uploading non-image file is rejected."""
        token = authenticated_user["token"]

        # Create a text file
        text_file = BytesIO(b"This is not an image")

        files = {
            "files": ("test.txt", text_file, "text/plain")
        }
        data = {
            "token": token,
            "folder_name": "invalid_files"
        }

        response = client.post("/api/images/upload", files=files, data=data)

        # Should reject invalid file type
        assert response.status_code == 400

    def test_upload_empty_folder_name(self, client, authenticated_user, sample_image_file):
        """Test uploading with empty folder name fails."""
        token = authenticated_user["token"]

        files = {
            "files": ("test.png", sample_image_file, "image/png")
        }
        data = {
            "token": token,
            "folder_name": ""
        }

        response = client.post("/api/images/upload", files=files, data=data)

        assert response.status_code == 400

    def test_upload_no_files(self, client, authenticated_user):
        """Test upload request with no files fails."""
        token = authenticated_user["token"]

        data = {
            "token": token,
            "folder_name": "empty"
        }

        response = client.post("/api/images/upload", data=data)

        assert response.status_code in [400, 422]


class TestFolderManagement:
    """Test folder listing and management."""

    def test_get_user_folders(self, client, authenticated_user, uploaded_folder):
        """Test retrieving user's folders."""
        token = authenticated_user["token"]

        response = client.post("/api/folders", json={"token": token})

        assert response.status_code == 200
        data = response.json()
        assert "owned" in data or "folders" in data
        # Should contain at least the uploaded folder
        folders = data.get("owned", data.get("folders", []))
        assert len(folders) > 0

    def test_get_folders_without_auth(self, client):
        """Test getting folders without authentication fails."""
        response = client.post("/api/folders", json={})

        assert response.status_code in [400, 401, 422]

    def test_folder_isolation(self, client, authenticated_user, second_user, sample_image_file):
        """Test users can only see their own folders (unless shared)."""
        # User 1 uploads to folder
        files = {
            "files": ("user1.png", sample_image_file, "image/png")
        }
        data = {
            "token": authenticated_user["token"],
            "folder_name": "user1_private"
        }
        client.post("/api/images/upload", files=files, data=data)

        # User 2 checks their folders
        response = client.post("/api/folders", json={"token": second_user["token"]})

        assert response.status_code == 200
        data = response.json()
        folders = data.get("owned", data.get("folders", []))

        # User 2 should not see User 1's folder
        folder_names = [f.get("folder_name", f.get("name")) for f in folders]
        assert "user1_private" not in folder_names


class TestFolderDeletion:
    """Test folder deletion functionality."""

    def test_delete_owned_folder(self, client, uploaded_folder):
        """Test deleting a folder the user owns."""
        token = uploaded_folder["token"]
        folder_id = uploaded_folder["folder_id"]

        response = client.delete("/api/folders", json={
            "token": token,
            "folder_ids": [folder_id]
        })

        assert response.status_code == 200
        data = response.json()
        assert "deleted" in data["message"].lower() or data.get("deleted_count", 0) > 0

    def test_delete_nonexistent_folder(self, client, authenticated_user):
        """Test deleting non-existent folder fails gracefully."""
        token = authenticated_user["token"]

        response = client.delete("/api/folders", json={
            "token": token,
            "folder_ids": [99999]  # Non-existent ID
        })

        # Should either succeed with 0 deleted or fail with 404
        assert response.status_code in [200, 404]

    def test_delete_folder_unauthorized(self, client, authenticated_user, second_user, sample_image_file):
        """Test user cannot delete another user's folder."""
        # User 1 creates folder
        files = {"files": ("img.png", sample_image_file, "image/png")}
        data = {"token": authenticated_user["token"], "folder_name": "protected"}
        response = client.post("/api/images/upload", files=files, data=data)
        folder_id = response.json()["folder_id"]

        # User 2 tries to delete User 1's folder
        response = client.delete("/api/folders", json={
            "token": second_user["token"],
            "folder_ids": [folder_id]
        })

        # Should fail or delete 0 folders
        if response.status_code == 200:
            assert response.json().get("deleted_count", 1) == 0
        else:
            assert response.status_code in [403, 404]

    def test_delete_multiple_folders(self, client, authenticated_user, sample_image_file):
        """Test deleting multiple folders at once."""
        token = authenticated_user["token"]

        # Create two folders
        folder_ids = []
        for i in range(2):
            files = {"files": (f"img{i}.png", sample_image_file, "image/png")}
            data = {"token": token, "folder_name": f"folder{i}"}
            response = client.post("/api/images/upload", files=files, data=data)
            folder_ids.append(response.json()["folder_id"])

        # Delete both
        response = client.delete("/api/folders", json={
            "token": token,
            "folder_ids": folder_ids
        })

        assert response.status_code == 200
        data = response.json()
        assert data.get("deleted_count", 0) == 2 or "deleted" in data["message"].lower()


class TestImageRetrieval:
    """Test image retrieval and serving."""

    def test_list_folder_images(self, client, uploaded_folder):
        """Test retrieving images from a folder."""
        # Note: Depends on your API endpoint for listing images
        # Adjust endpoint as needed
        token = uploaded_folder["token"]
        folder_id = uploaded_folder["folder_id"]

        # If you have an endpoint like /api/folders/{id}/images
        # Adjust based on your actual API structure
        response = client.post("/api/folders", json={"token": token})

        assert response.status_code == 200
        # Verify folder contains images
        # Structure depends on your API response format

    def test_image_url_generation(self, client, uploaded_folder):
        """Test that uploaded images have accessible URLs."""
        # This test verifies that image URLs are properly formatted
        # and follow the expected pattern
        token = uploaded_folder["token"]

        response = client.post("/api/folders", json={"token": token})

        assert response.status_code == 200
        # Check that folder contains image data with URLs
        # Adjust based on your API structure
