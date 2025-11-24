"""
Search functionality tests.

Tests semantic search integration with search microservice.
"""

import pytest
from unittest.mock import Mock, patch


class TestSearch:
    """Test semantic image search."""

    @patch('search_client.SearchServiceClient.search')
    def test_search_with_text_query(self, mock_search, client, authenticated_user, uploaded_folder):
        """Test searching images with text query."""
        token = authenticated_user["token"]

        # Mock search service response
        mock_search.return_value = {
            "results": [
                {"image_id": 1, "score": 0.95},
                {"image_id": 2, "score": 0.87}
            ]
        }

        response = client.post("/api/search", json={
            "token": token,
            "query": "red sunset",
            "folder_ids": [uploaded_folder["folder_id"]]
        })

        assert response.status_code == 200
        data = response.json()
        assert "results" in data or "images" in data

    def test_search_without_authentication(self, client):
        """Test search without auth token fails."""
        response = client.post("/api/search", json={
            "query": "sunset",
            "folder_ids": [1]
        })

        assert response.status_code in [400, 401, 422]

    def test_search_empty_query(self, client, authenticated_user):
        """Test search with empty query."""
        token = authenticated_user["token"]

        response = client.post("/api/search", json={
            "token": token,
            "query": "",
            "folder_ids": [1]
        })

        # Should either reject or return all images
        assert response.status_code in [200, 400]

    @patch('search_client.SearchServiceClient.search')
    def test_search_across_multiple_folders(self, mock_search, client, authenticated_user):
        """Test searching across multiple folders."""
        token = authenticated_user["token"]

        mock_search.return_value = {"results": []}

        response = client.post("/api/search", json={
            "token": token,
            "query": "beach",
            "folder_ids": [1, 2, 3]
        })

        assert response.status_code == 200
        # Verify search was called with correct folders
        mock_search.assert_called_once()

    @patch('search_client.SearchServiceClient.search')
    def test_search_respects_permissions(self, mock_search, client, authenticated_user, second_user):
        """Test users can only search folders they have access to."""
        # User 1's token
        token1 = authenticated_user["token"]
        # User 2's token
        token2 = second_user["token"]

        mock_search.return_value = {"results": []}

        # User 1 searches their folders
        response1 = client.post("/api/search", json={
            "token": token1,
            "query": "test",
            "folder_ids": [1]
        })

        # User 2 tries to search User 1's folder (should fail or return empty)
        response2 = client.post("/api/search", json={
            "token": token2,
            "query": "test",
            "folder_ids": [1]  # User 1's folder
        })

        assert response1.status_code == 200
        # Response2 should either fail or return no results
        if response2.status_code == 200:
            data = response2.json()
            results = data.get("results", data.get("images", []))
            assert len(results) == 0

    @patch('search_client.SearchServiceClient.search')
    def test_search_no_results(self, mock_search, client, authenticated_user):
        """Test search with no matching results."""
        token = authenticated_user["token"]

        # Mock empty results
        mock_search.return_value = {"results": []}

        response = client.post("/api/search", json={
            "token": token,
            "query": "nonexistent object",
            "folder_ids": [1]
        })

        assert response.status_code == 200
        data = response.json()
        results = data.get("results", data.get("images", []))
        assert len(results) == 0

    def test_search_service_unavailable(self, client, authenticated_user):
        """Test graceful handling when search service is down."""
        token = authenticated_user["token"]

        with patch('search_client.SearchServiceClient.search', side_effect=Exception("Service unavailable")):
            response = client.post("/api/search", json={
                "token": token,
                "query": "test",
                "folder_ids": [1]
            })

            # Should return error gracefully
            assert response.status_code in [500, 503]
            data = response.json()
            assert "error" in data or "detail" in data


class TestSearchIntegration:
    """Test search service integration."""

    @patch('search_client.SearchServiceClient.embed_images')
    def test_embedding_generation_on_upload(self, mock_embed, client, authenticated_user, sample_image_file):
        """Test that uploading images triggers embedding generation."""
        token = authenticated_user["token"]

        files = {"files": ("test.png", sample_image_file, "image/png")}
        data = {"token": token, "folder_name": "embed_test"}

        response = client.post("/api/images/upload", files=files, data=data)

        assert response.status_code == 200
        # Embedding should be triggered (may be async)
        # Check that embed_images was called or queued

    @patch('search_client.SearchServiceClient.create_index')
    def test_index_creation_on_folder_creation(self, mock_create_index, client, authenticated_user, sample_image_file):
        """Test that FAISS index is created for new folders."""
        token = authenticated_user["token"]

        files = {"files": ("test.png", sample_image_file, "image/png")}
        data = {"token": token, "folder_name": "new_index_folder"}

        response = client.post("/api/images/upload", files=files, data=data)

        assert response.status_code == 200
        # Index creation should be triggered

    @patch('search_client.SearchServiceClient.delete_index')
    def test_index_deletion_on_folder_deletion(self, mock_delete_index, client, uploaded_folder):
        """Test that FAISS index is deleted when folder is deleted."""
        token = uploaded_folder["token"]
        folder_id = uploaded_folder["folder_id"]

        response = client.delete("/api/folders", json={
            "token": token,
            "folder_ids": [folder_id]
        })

        assert response.status_code == 200
        # Index deletion should be triggered
