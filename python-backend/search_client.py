"""
HTTP client for communicating with the Python search microservice.

This allows the Python backend to delegate CLIP embedding and FAISS operations
to the shared search-service microservice, matching the Java backend's architecture.

Architecture:
Python Backend → HTTP → Search Service (FAISS + CLIP)
"""

import requests
import logging
from typing import List, Dict, Optional
import os

logger = logging.getLogger("image_search_app.search_client")

class SearchServiceClient:
    """
    HTTP client for the search microservice.

    Provides methods to:
    - Create FAISS indexes
    - Embed images and add to FAISS
    - Search images using text queries
    - Delete FAISS indexes
    """

    def __init__(self, base_url: Optional[str] = None, timeout: int = 30):
        """
        Initialize the search service client.

        Args:
            base_url: Base URL of search service (default: http://localhost:5000)
            timeout: Request timeout in seconds (default: 30)
        """
        self.base_url = base_url or os.getenv("SEARCH_SERVICE_URL", "http://localhost:5000")
        self.timeout = timeout
        logger.info(f"SearchServiceClient initialized with base URL: {self.base_url}")

    def create_index(self, user_id: int, folder_id: int) -> bool:
        """
        Create a new FAISS index for a folder.

        Args:
            user_id: User ID
            folder_id: Folder ID

        Returns:
            True if successful

        Raises:
            Exception if service call fails
        """
        logger.info(f"Creating FAISS index for user {user_id}, folder {folder_id}")

        try:
            response = requests.post(
                f"{self.base_url}/create-index",
                json={
                    "user_id": user_id,
                    "folder_id": folder_id
                },
                timeout=self.timeout
            )
            response.raise_for_status()
            logger.info(f"Successfully created FAISS index")
            return True

        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to create FAISS index: {e}")
            raise Exception(f"Search service unavailable: {str(e)}")

    def embed_images(self, user_id: int, folder_id: int, images: List[Dict[str, any]]) -> bool:
        """
        Generate embeddings for images and add to FAISS index.

        Args:
            user_id: User ID
            folder_id: Folder ID
            images: List of dicts with 'image_id' and 'file_path' keys

        Returns:
            True if successful

        Raises:
            Exception if service call fails
        """
        logger.info(f"Embedding {len(images)} images for user {user_id}, folder {folder_id}")

        try:
            response = requests.post(
                f"{self.base_url}/embed-images",
                json={
                    "user_id": user_id,
                    "folder_id": folder_id,
                    "images": images
                },
                timeout=self.timeout
            )
            response.raise_for_status()
            logger.info(f"Successfully embedded {len(images)} images")
            return True

        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to embed images: {e}")
            raise Exception(f"Search service unavailable: {str(e)}")

    def search(
        self,
        user_id: int,
        query: str,
        folder_ids: List[int],
        folder_owner_map: Dict[int, int],
        top_k: int = 5
    ) -> tuple:
        """
        Perform semantic image search using FAISS.

        Args:
            user_id: User ID (for logging/tracking)
            query: Text search query
            folder_ids: List of folder IDs to search
            folder_owner_map: Map of folder_id -> owner_user_id
            top_k: Number of results to return

        Returns:
            Tuple of (distances, indices) matching FAISS format:
            - distances: List of lists with similarity scores
            - indices: List of lists with image IDs

        Raises:
            Exception if service call fails
        """
        logger.info(f"Searching with query='{query}', folders={folder_ids}, top_k={top_k}")

        try:
            # Convert folder_owner_map keys to strings for JSON serialization
            folder_owner_map_str = {str(k): v for k, v in folder_owner_map.items()}

            response = requests.post(
                f"{self.base_url}/search",
                json={
                    "user_id": user_id,
                    "query": query,
                    "folder_ids": folder_ids,
                    "folder_owner_map": folder_owner_map_str,
                    "top_k": top_k
                },
                timeout=self.timeout
            )
            response.raise_for_status()

            data = response.json()
            results = data.get("results", [])

            # Convert to FAISS format (distances and indices as nested lists)
            if not results:
                return [[]], [[]]

            distances = [[result["score"] for result in results]]
            indices = [[result["image_id"] for result in results]]

            logger.info(f"Search completed: {len(results)} results found")
            return distances, indices

        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to search images: {e}")
            raise Exception(f"Search service unavailable: {str(e)}")

    def delete_index(self, user_id: int, folder_id: int) -> bool:
        """
        Delete FAISS index for a folder.

        Args:
            user_id: User ID
            folder_id: Folder ID

        Returns:
            True if successful

        Note:
            Does not raise exceptions - best effort cleanup
        """
        logger.info(f"Deleting FAISS index for user {user_id}, folder {folder_id}")

        try:
            response = requests.delete(
                f"{self.base_url}/delete-index/{user_id}/{folder_id}",
                timeout=self.timeout
            )
            response.raise_for_status()
            logger.info(f"Successfully deleted FAISS index")
            return True

        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to delete FAISS index: {e}")
            # Don't throw - best effort cleanup
            return False

    def health_check(self) -> bool:
        """
        Check if search service is healthy.

        Returns:
            True if service is reachable and healthy
        """
        try:
            response = requests.get(f"{self.base_url}/health", timeout=5)
            return response.status_code == 200
        except:
            return False
