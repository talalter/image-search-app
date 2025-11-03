import faiss
import numpy as np
import os
from utils import embed_text
import heapq
FAISS_FOLDER: str = "faisses_indexes"
class FaissManager:
    def __init__(self, base_folder: str = FAISS_FOLDER):
        self.base_folder = base_folder
        os.makedirs(self.base_folder, exist_ok=True)


    def create_user_faiss_folder(self, user_id: int):
        """Create a base FAISS folder for the user (without creating any index yet)."""
        folder_path = os.path.join(self.base_folder, str(user_id))
        if not os.path.exists(folder_path):
            os.makedirs(folder_path, exist_ok=True)
        else:
            print(f"FAISS folder for user {user_id} already exists.")


    def _get_folder_path(self, user_id: int, folder_id: int) -> str:
        return os.path.join(self.base_folder, str(user_id), f"{folder_id}.faiss")
    

    def _normalize(self, vectors: np.ndarray) -> np.ndarray:
        norms = np.linalg.norm(vectors, axis=1, keepdims=True)
        norms[norms == 0] = 1e-10
        return vectors / norms
    
    def create_faiss_index(self, user_id: int, folder_id: int, dimension: int = 512):
        """
        Create a normalized FAISS index using cosine similarity (IndexFlatIP).
        """
        folder_path = self._get_folder_path(user_id, folder_id)
        os.makedirs(os.path.dirname(folder_path), exist_ok=True)
        index = faiss.IndexIDMap(faiss.IndexFlatIP(dimension))
        faiss.write_index(index, folder_path)

        
    def add_vector_to_faiss(self, user_id: int, folder_id: int, vector: np.ndarray, vector_id: int):
        index_path = self._get_folder_path(user_id, folder_id)
        if not os.path.exists(index_path):
            raise FileNotFoundError(f"FAISS index for folder {folder_id} (user {user_id}) does not exist.")
        
        index = faiss.read_index(index_path)
        vector = np.array(vector, dtype='float32').reshape(1, -1)
        vector = self._normalize(vector)
        index.add_with_ids(vector, np.array([vector_id], dtype='int64'))
        faiss.write_index(index, index_path)    


    def search(self, user_id: int, query: str, folder_ids: list[int], k: int = 1):
        query_embedding = embed_text(query).astype('float32').reshape(1, -1)
        query_embedding = self._normalize(query_embedding)
        
        results = []

        print(f"Searching FAISS for user {user_id}, query='{query}', folders={folder_ids}")

        for folder_id in folder_ids:
            index_path = self._get_folder_path(user_id, folder_id)
            if not os.path.exists(index_path):
                raise FileNotFoundError(f"FAISS index for folder {folder_id} (user {user_id}) does not exist.")
            
            index = faiss.read_index(index_path)
            local_k = min(k, index.ntotal)
            distances, indices = index.search(query_embedding, local_k)
            for d, i in zip(distances[0], indices[0]):
                results.append((float(d), int(i)))


        results.sort(key=lambda x: x[0], reverse=True)
        distances = [[res[0] for res in results][:k]]
        indices = [[res[1] for res in results][:k]]
        return distances, indices

    def search_with_ownership(self, query: str, folder_ids: list[int], folder_owner_map: dict[int, int], k: int = 1):
        """
        Search across multiple folders where each folder may be owned by different users.
        
        Args:
            query: Search query text
            folder_ids: List of folder IDs to search
            folder_owner_map: Dict mapping folder_id to owner_user_id (for correct FAISS index path)
            k: Number of top results to return
            
        Returns:
            Tuple of (distances, indices) lists
        """
        query_embedding = embed_text(query).astype('float32').reshape(1, -1)
        query_embedding = self._normalize(query_embedding)
        
        results = []

        print(f"Searching FAISS with ownership mapping, query='{query}', folders={folder_ids}")
        print(f"Folder ownership map: {folder_owner_map}")

        for folder_id in folder_ids:
            owner_user_id = folder_owner_map.get(folder_id)
            if owner_user_id is None:
                print(f"Warning: No owner found for folder {folder_id}, skipping")
                continue
                
            index_path = self._get_folder_path(owner_user_id, folder_id)
            if not os.path.exists(index_path):
                print(f"Warning: FAISS index not found at {index_path}, skipping folder {folder_id}")
                continue
            
            index = faiss.read_index(index_path)
            local_k = min(k, index.ntotal)
            distances, indices = index.search(query_embedding, local_k)
            for d, i in zip(distances[0], indices[0]):
                results.append((float(d), int(i)))

        results.sort(key=lambda x: x[0], reverse=True)
        distances = [[res[0] for res in results][:k]]
        indices = [[res[1] for res in results][:k]]
        return distances, indices


    def delete_faiss_index(self, user_id, folder_id):
        """Delete a FAISS index for a user."""
        index_path = self._get_folder_path(user_id, folder_id)
        if os.path.exists(index_path):
            os.remove(index_path)
            return True
        else:
            raise FileNotFoundError(f"FAISS index for user {user_id} does not exist.")
