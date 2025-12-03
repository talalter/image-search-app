"""
FAISS search handler.

Manages FAISS vector indexes for semantic image search.
"""

import faiss
import numpy as np
import os
import heapq
import logging

from embedding_service import EmbeddingService

logger = logging.getLogger(__name__)

# Determine base folder based on environment
def get_faiss_base_folder():
    """Get FAISS index base folder (supports both Docker and local dev)."""
    if os.path.exists("/app"):
        # Docker environment
        return "/app/data/indexes"
    else:
        # Local development - use data/indexes relative to project root
        # python-search-service/ is at project root level, so go up and use data/indexes
        project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        return os.path.join(project_root, "data", "indexes")

FAISS_FOLDER = get_faiss_base_folder()

class SearchHandler:
    """Manages FAISS indexes for image search."""

    def __init__(self, embedding_service: EmbeddingService = None, base_folder: str = FAISS_FOLDER, cache_size: int = 50):
        self.base_folder = base_folder
        os.makedirs(self.base_folder, exist_ok=True)
        self.embedding_service = embedding_service if embedding_service is not None else EmbeddingService()

        # LRU cache for FAISS indexes: {(user_id, folder_id): index}
        # Reduces disk I/O by keeping recently used indexes in memory
        self._index_cache = {}
        self._cache_access_order = []  # Track access order for LRU eviction
        self._cache_size = cache_size

        logger.info(f"SearchHandler initialized with base folder: {base_folder}, cache_size: {cache_size}")

    def _get_folder_path(self, user_id: int, folder_id: int) -> str:
        """Get path to FAISS index file for a folder."""
        return os.path.join(self.base_folder, str(user_id), f"{folder_id}.faiss")

    def _normalize(self, vectors: np.ndarray) -> np.ndarray:
        """Normalize vectors for cosine similarity."""
        norms = np.linalg.norm(vectors, axis=1, keepdims=True)
        norms[norms == 0] = 1e-10
        return vectors / norms

    def _load_index(self, user_id: int, folder_id: int):
        """
        Load FAISS index with LRU caching.

        This significantly improves search performance by avoiding repeated disk I/O
        for frequently accessed indexes.

        Args:
            user_id: User ID
            folder_id: Folder ID

        Returns:
            FAISS index object
        """
        cache_key = (user_id, folder_id)

        # Check cache
        if cache_key in self._index_cache:
            # Move to end (most recently used)
            self._cache_access_order.remove(cache_key)
            self._cache_access_order.append(cache_key)
            logger.debug(f"Cache HIT for index {cache_key}")
            return self._index_cache[cache_key]

        # Cache miss - load from disk
        index_path = self._get_folder_path(user_id, folder_id)
        if not os.path.exists(index_path):
            raise FileNotFoundError(f"FAISS index not found at {index_path}")

        index = faiss.read_index(index_path)
        logger.debug(f"Cache MISS for index {cache_key} - loaded from disk")

        # Add to cache
        self._index_cache[cache_key] = index
        self._cache_access_order.append(cache_key)

        # Evict oldest if cache is full
        if len(self._index_cache) > self._cache_size:
            oldest_key = self._cache_access_order.pop(0)
            del self._index_cache[oldest_key]
            logger.debug(f"Evicted index {oldest_key} from cache")

        return index

    def _invalidate_cache(self, user_id: int, folder_id: int):
        """Remove index from cache (called when index is modified or deleted)."""
        cache_key = (user_id, folder_id)
        if cache_key in self._index_cache:
            del self._index_cache[cache_key]
            self._cache_access_order.remove(cache_key)
            logger.debug(f"Invalidated cache for index {cache_key}")

    def create_faiss_index(self, user_id: int, folder_id: int, dimension: int = 512):
        """
        Create a normalized FAISS index using cosine similarity (IndexFlatIP).

        Args:
            user_id: User ID
            folder_id: Folder ID
            dimension: Embedding dimension (default 512 for CLIP ViT-B/32)
        """
        folder_path = self._get_folder_path(user_id, folder_id)
        os.makedirs(os.path.dirname(folder_path), exist_ok=True)

        # IndexFlatIP = Inner Product index (for cosine similarity with normalized vectors)
        # IndexIDMap allows us to use custom image IDs instead of sequential indices
        index = faiss.IndexIDMap(faiss.IndexFlatIP(dimension))
        faiss.write_index(index, folder_path)
        logger.info(f"Created FAISS index: {folder_path}")

    def add_vector_to_faiss(self, user_id: int, folder_id: int, vector: np.ndarray, vector_id: int):
        """
        Add an image embedding to the FAISS index.

        Args:
            user_id: User ID
            folder_id: Folder ID
            vector: Image embedding vector
            vector_id: Image ID (from database)
        """
        index_path = self._get_folder_path(user_id, folder_id)
        if not os.path.exists(index_path):
            raise FileNotFoundError(f"FAISS index for folder {folder_id} (user {user_id}) does not exist.")

        # Load existing index
        index = faiss.read_index(index_path)

        # Normalize and add vector
        vector = np.array(vector, dtype='float32').reshape(1, -1)
        vector = self._normalize(vector)
        index.add_with_ids(vector, np.array([vector_id], dtype='int64'))

        # Save updated index
        faiss.write_index(index, index_path)

        # Invalidate cache since index was modified
        self._invalidate_cache(user_id, folder_id)

        logger.debug(f"Added vector {vector_id} to index {index_path}")

    def add_vectors_batch(self, user_id: int, folder_id: int, vectors: list, vector_ids: list):
        """
        Add multiple image embeddings to the FAISS index in a single batch operation.
        This is more efficient and avoids concurrency issues compared to multiple individual adds.

        Args:
            user_id: User ID
            folder_id: Folder ID
            vectors: List of image embedding vectors (numpy arrays)
            vector_ids: List of image IDs (from database)
        """
        if len(vectors) != len(vector_ids):
            raise ValueError(f"Vectors count ({len(vectors)}) must match IDs count ({len(vector_ids)})")

        if len(vectors) == 0:
            logger.warning("add_vectors_batch called with empty vectors list")
            return

        index_path = self._get_folder_path(user_id, folder_id)
        if not os.path.exists(index_path):
            raise FileNotFoundError(f"FAISS index for folder {folder_id} (user {user_id}) does not exist.")

        # Load existing index
        index = faiss.read_index(index_path)

        # Stack all vectors into a single numpy array and normalize
        vectors_array = np.vstack([np.array(v, dtype='float32').reshape(1, -1) for v in vectors])
        vectors_array = self._normalize(vectors_array)
        ids_array = np.array(vector_ids, dtype='int64')

        # Add all vectors at once
        index.add_with_ids(vectors_array, ids_array)

        # Save updated index (single write operation)
        faiss.write_index(index, index_path)

        # Invalidate cache since index was modified
        self._invalidate_cache(user_id, folder_id)

        logger.info(f"Batch added {len(vectors)} vectors to index {index_path}")

    def search_with_ownership(
        self,
        query: str,
        folder_ids: list[int],
        folder_owner_map: dict[int, int],
        k: int = 5
    ):
        """
        Search across multiple folders where each folder may be owned by different users.

        This handles the case where:
        - User A searches folders they own (stored under /faiss_indexes/A/)
        - User A also searches folders shared by User B (stored under /faiss_indexes/B/)

        Args:
            query: Text search query
            folder_ids: List of folder IDs to search
            folder_owner_map: Mapping of folder_id -> owner_user_id (for finding correct index path)
            k: Number of results to return

        Returns:
            Tuple of (distances, indices, folder_ids_list) where:
            - distances are similarity scores
            - indices are image IDs
            - folder_ids_list are the folder IDs for each result
        """
        # Generate query embedding
        query_embedding = self.embedding_service.embed_text(query).astype('float32').reshape(1, -1)
        query_embedding = self._normalize(query_embedding)

        # Use heap to efficiently keep top-k results across all folders
        # Each heap entry is: (distance, image_id, folder_id)
        heap = []

        logger.info(f"Searching folders: {folder_ids}")
        logger.debug(f"Folder ownership map: {folder_owner_map}")

        for folder_id in folder_ids:
            owner_user_id = folder_owner_map.get(folder_id)
            if owner_user_id is None:
                logger.warning(f"No owner found for folder {folder_id}, skipping")
                continue

            index_path = self._get_folder_path(owner_user_id, folder_id)
            if not os.path.exists(index_path):
                logger.warning(f"FAISS index not found at {index_path}, skipping folder {folder_id}")
                continue

            # Load index from cache (or disk if not cached)
            try:
                index = self._load_index(owner_user_id, folder_id)
            except FileNotFoundError:
                logger.warning(f"FAISS index not found for folder {folder_id}, skipping")
                continue

            local_k = min(k, index.ntotal)
            if local_k == 0:
                continue

            distances, indices = index.search(query_embedding, local_k)

            # Add results to heap with folder_id
            for d, i in zip(distances[0], indices[0]):
                if len(heap) < k:
                    heapq.heappush(heap, (d, int(i), folder_id))
                else:
                    heapq.heappushpop(heap, (d, int(i), folder_id))

        # Extract top-k results
        top_results = heapq.nlargest(k, heap, key=lambda x: x[0])
        distances = [[res[0] for res in top_results]]
        indices = [[res[1] for res in top_results]]
        folder_ids_list = [[res[2] for res in top_results]]

        logger.info(f"Search completed: {len(top_results)} results")
        return distances, indices, folder_ids_list

    def delete_faiss_index(self, user_id: int, folder_id: int):
        """
        Delete a FAISS index for a folder.

        Args:
            user_id: User ID
            folder_id: Folder ID
        """
        # Invalidate cache first
        self._invalidate_cache(user_id, folder_id)

        index_path = self._get_folder_path(user_id, folder_id)
        if os.path.exists(index_path):
            os.remove(index_path)
            logger.info(f"Deleted FAISS index: {index_path}")
            return True
        else:
            logger.warning(f"FAISS index not found: {index_path}")
            raise FileNotFoundError(f"FAISS index for folder {folder_id} (user {user_id}) does not exist.")
