"""
CLIP embedding service.

Generates embeddings for images and text using OpenAI CLIP model.
"""

import torch
import clip
import logging
from PIL import Image
from typing import List
import numpy as np
from concurrent.futures import ThreadPoolExecutor
import os

logger = logging.getLogger(__name__)

class EmbeddingService:
    """Generates CLIP embeddings for images and text."""

    def __init__(self, model_name: str = "ViT-B/32", max_workers: int = 4):
        """
        Initialize CLIP model.

        Args:
            model_name: CLIP model variant (default: ViT-B/32)
            max_workers: Number of threads for parallel image loading (default: 8 for I/O)
        """
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.max_workers = max_workers
        logger.info(f"Loading CLIP model '{model_name}' on device: {self.device}")

        self.model, self.preprocess = clip.load(model_name, self.device)
        self.model.eval()  # Set to evaluation mode
        
        logger.info(f"CLIP model loaded (device: {self.device}, workers: {max_workers})")

    def embed_image(self, pil_image: Image.Image):
        """
        Generate embedding for a PIL image.

        Args:
            pil_image: PIL Image object

        Returns:
            numpy array of shape (512,) containing the image embedding
        """
        image = self.preprocess(pil_image).unsqueeze(0).to(self.device)
        with torch.no_grad():
            image_features = self.model.encode_image(image)
        return image_features.cpu().numpy()

    def embed_images_batch(self, pil_images: List[Image.Image], batch_size: int = 32):
        """
        Generate embeddings for multiple images efficiently using batching.
        
        This is 3-5x faster than calling embed_image() in a loop.
        
        Args:
            pil_images: List of PIL Image objects
            batch_size: Number of images to process in each batch
        
        Returns:
            numpy array of shape (N, 512) containing embeddings for all images
        """
        if not pil_images:
            return np.array([])
        
        all_embeddings = []
        
        # Process in batches to avoid OOM on GPU
        for i in range(0, len(pil_images), batch_size):
            batch = pil_images[i:i + batch_size]
            
            # Preprocess all images in batch
            image_tensors = torch.stack([
                self.preprocess(img) for img in batch
            ]).to(self.device)
            
            # Single forward pass for entire batch
            with torch.no_grad():
                batch_features = self.model.encode_image(image_tensors)
            
            all_embeddings.append(batch_features.cpu().numpy())
        
        return np.vstack(all_embeddings)

    def _load_single_image(self, filepath: str) -> Image.Image:
        """Load and convert a single image file to RGB."""
        resolved_path = self._resolve_image_path(filepath)
        return Image.open(resolved_path).convert("RGB")

    def _resolve_image_path(self, filepath: str) -> str:
        """Resolve image path to absolute path."""
        # If absolute path, use as-is
        if os.path.isabs(filepath):
            return filepath
        
        # If path starts with "images/", convert to data/uploads/images/
        if filepath.startswith("images/"):
            relative_path = filepath.replace("images/", "data/uploads/images/", 1)
            
            if os.path.exists("/app/data/uploads"):
                # Docker environment
                return os.path.join("/app", relative_path)
            else:
                # Local development
                project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
                return os.path.join(project_root, relative_path)
        
        # Other relative paths
        return filepath

    def embed_image_files_batch(self, filepaths: List[str], batch_size: int = 32):
        """
        Generate embeddings for multiple image files efficiently.
        
        Uses parallel I/O for loading + batch processing for embeddings.
        
        Args:
            filepaths: List of image file paths
            batch_size: Number of images to process in each CLIP batch
        
        Returns:
            numpy array of shape (N, 512) containing embeddings IN SAME ORDER as filepaths
        """
        import time
        
        if not filepaths:
            return np.array([])
        
        # Load all images in parallel - PRESERVE ORDER!
        load_start = time.time()
        with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
            # executor.map() preserves order AND runs in parallel
            pil_images = list(executor.map(self._load_single_image, filepaths))
        load_time = time.time() - load_start
        
        # Generate embeddings in batches
        embed_start = time.time()
        embeddings = self.embed_images_batch(pil_images, batch_size)
        embed_time = time.time() - embed_start
        
        logger.info(f"Batch processing: {len(filepaths)} images " +
                   f"(load: {load_time:.2f}s [{self.max_workers} threads], " +
                   f"embed: {embed_time:.2f}s [batch_size={batch_size}])")
        
        return embeddings

    def embed_text(self, text: str):
        """
        Generate embedding for text query.
        
        This is called during search to convert user's text query
        into a 512-dimensional vector that can be compared with
        image embeddings in FAISS.
        
        Args:
            text: Text string to embed (e.g., "a cute cat")
        
        Returns:
            numpy array containing the text embedding (shape: 1, 512)
        """
        # Tokenize text (max 77 tokens)
        text_tokens = clip.tokenize([text]).to(self.device)
        
        # Generate embedding using CLIP text encoder
        with torch.no_grad():
            text_features = self.model.encode_text(text_tokens)
            #                              ↑
            #                   CLIP Language Transformer encodes text
        
        return text_features.cpu().numpy()
