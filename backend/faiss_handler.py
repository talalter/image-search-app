import faiss
import numpy as np
import os
from utils import embed_text

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
        merged_index = faiss.IndexIDMap(faiss.IndexFlatL2(query_embedding.shape[1]))

        print(f"Searching FAISS for user {user_id}, query='{query}', folders={folder_ids}")

        for folder_id in folder_ids:
            index_path = self._get_folder_path(user_id, folder_id)
            if not os.path.exists(index_path):
                raise FileNotFoundError(f"FAISS index for folder {folder_id} (user {user_id}) does not exist.")
            
            index = faiss.read_index(index_path)
            vectors, ids = self.extract_vectors_and_ids(index)
            merged_index.add_with_ids(vectors, ids)

        if merged_index.ntotal == 0:
            raise ValueError("No vectors found in the provided folder IDs.")

        distances, indices = merged_index.search(query_embedding, k)
        return distances, indices


    def delete_faiss_index(self, user_id, folder_id):
        """Delete a FAISS index for a user."""
        index_path = self._get_folder_path(user_id, folder_id)
        if os.path.exists(index_path):
            os.remove(index_path)
            return True
        else:
            raise FileNotFoundError(f"FAISS index for user {user_id} does not exist.")


    @staticmethod
    def extract_vectors_and_ids(index: faiss.IndexIDMap):
        if index.ntotal == 0:
            return np.empty((0, index.d), dtype='float32'), np.empty((0,), dtype='int64')
        vectors = index.index.reconstruct_n(0, index.ntotal)
        ids = faiss.vector_to_array(index.id_map)
        return vectors, ids

