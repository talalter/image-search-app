import faiss
import numpy as np
import os
from backend.utils import embed_text

class FaissManager:
    def __init__(self, base_folder: str = "faisses_indexes"):
        self.base_folder = base_folder
        os.makedirs(self.base_folder, exist_ok=True)

    def _get_folder_path(self, folder_id: int) -> str:
        return os.path.join(self.base_folder, f"{folder_id}.faiss")

    def _normalize(self, vectors: np.ndarray) -> np.ndarray:
        norms = np.linalg.norm(vectors, axis=1, keepdims=True)
        norms[norms == 0] = 1e-10
        return vectors / norms

    def create_index(self, folder_id: int, dimension: int = 512):
        """
        Create a normalized FAISS index using cosine similarity (IndexFlatIP).
        """
        index_path = self._get_folder_path(folder_id)
        index = faiss.IndexIDMap(faiss.IndexFlatIP(dimension))
        faiss.write_index(index, index_path)

    def delete_index(self, folder_id: int):
        index_path = self._get_folder_path(folder_id)
        if os.path.exists(index_path):
            os.remove(index_path)
        else:
            raise FileNotFoundError(f"Index for folder {folder_id} not found")

    def add_vector(self, folder_id: int, vector: np.ndarray, vector_id: int):
        index_path = self._get_folder_path(folder_id)
        if not os.path.exists(index_path):
            raise FileNotFoundError(f"Index for folder {folder_id} not found")

        index = faiss.read_index(index_path)
        vector = np.array(vector, dtype='float32').reshape(1, -1)
        vector = self._normalize(vector)
        index.add_with_ids(vector, np.array([vector_id], dtype='int64'))
        faiss.write_index(index, index_path)

    def extract_vectors_and_ids(self, index: faiss.IndexIDMap):
        if index.ntotal == 0:
            return np.empty((0, index.d), dtype='float32'), np.empty((0,), dtype='int64')
        vectors = index.index.reconstruct_n(0, index.ntotal)
        ids = faiss.vector_to_array(index.id_map)
        vectors = self._normalize(vectors)
        return vectors, ids

    def search(self, query: str, folder_ids: list[int], k: int = 1):
        query_embedding = embed_text(query).astype('float32').reshape(1, -1)
        query_embedding = self._normalize(query_embedding)

        dimension = query_embedding.shape[1]
        merged_index = faiss.IndexIDMap(faiss.IndexFlatIP(dimension))

        for folder_id in folder_ids:
            index_path = self._get_folder_path(folder_id)
            if not os.path.exists(index_path):
                continue
            index = faiss.read_index(index_path)
            vectors, ids = self.extract_vectors_and_ids(index)
            if len(ids) > 0:
                merged_index.add_with_ids(vectors, ids)

        if merged_index.ntotal == 0:
            raise ValueError("No vectors found in selected indices.")

        distances, indices = merged_index.search(query_embedding, k)
        return distances, indices



def delete_faiss_index(user_id, faiss_folder=FAISS_FOLDER):
    """Delete a FAISS index for a user."""
    index_path = os.path.join(faiss_folder, f"{user_id}.faiss")
    if os.path.exists(index_path):
        os.remove(index_path)
        return True
    else:
        raise FileNotFoundError(f"FAISS index for user {user_id} does not exist.")

def create_user_faiss_folder(user_id: int, faiss_folder: str = FAISS_FOLDER):
    """Create a base FAISS folder for the user (without creating any index yet)."""
    folder_path = os.path.join(faiss_folder, str(user_id))
    if not os.path.exists(folder_path):
        os.makedirs(folder_path, exist_ok=True)


def create_faiss_index(folder_id, user_id, dimension=512, faiss_folder: str = FAISS_FOLDER):
    """Create a FAISS index for a user."""
    folder_path = os.path.join(faiss_folder, str(user_id))
    if not os.path.exists(folder_path):
        os.makedirs(folder_path, exist_ok=True)

    index_path = os.path.join(folder_path, f"{folder_id}.faiss")
    index = faiss.IndexIDMap(faiss.IndexFlatL2(dimension))  # Use L2 distance
    faiss.write_index(index, index_path)
    #a code to upload the FAISS to AWS 


def add_vector_to_faiss(folder_id, user_id, vector, vector_id, faiss_folder: str = FAISS_FOLDER):
    index_path = os.path.join(faiss_folder, str(user_id), f"{folder_id}.faiss")
    if not os.path.exists(index_path):
        raise FileNotFoundError(f"FAISS index for folder {folder_id} (user {user_id}) does not exist.")
    index = faiss.read_index(index_path)
    vector = np.array(vector, dtype='float32').reshape(1, -1)  # Ensure vector is 2D
    index.add_with_ids(vector, np.array([vector_id], dtype='int64'))  
    faiss.write_index(index, index_path) 
    return True

def extract_vectors_and_ids(index: faiss.IndexIDMap):
    """Extracts vectors and IDs from an IndexIDMap."""
    ntotal = index.ntotal
    if ntotal == 0:
        return np.empty((0, index.d), dtype='float32'), np.empty((0,), dtype='int64')
    vectors = index.index.reconstruct_n(0, index.ntotal) 
    ids = faiss.vector_to_array(index.id_map)
    return vectors, ids

def search_vector_in_many_faisses(user_id, query, folder_ids, k=1, faiss_folder="faisses_indexes"):
    query_embedding = embed_text(query).astype('float32').reshape(1, -1)
    merged_index = faiss.IndexIDMap(faiss.IndexFlatL2(query_embedding.shape[1]))

    print(f"Searching FAISS for user {user_id}, query='{query}', folders={folder_ids}")

    for folder_id in folder_ids:
        index_path = os.path.join(faiss_folder, str(user_id), f"{folder_id}.faiss")
        if not os.path.exists(index_path):
            raise FileNotFoundError(f"FAISS index for folder {folder_id} (user {user_id}) does not exist.")
        
        index = faiss.read_index(index_path)
        vectors, ids = extract_vectors_and_ids(index)
        merged_index.add_with_ids(vectors, ids)

    if merged_index.ntotal == 0:
        raise ValueError("No vectors found in the provided folder IDs.")

    distances, indices = merged_index.search(query_embedding, k)
    return distances, indices