"""
CLIP embedding service.

Generates embeddings for images and text using OpenAI CLIP model.
"""

import torch
import clip
import logging
from PIL import Image

logger = logging.getLogger(__name__)

class EmbeddingService:
    """Generates CLIP embeddings for images and text."""

    def __init__(self, model_name: str = "ViT-B/32"):
        """
        Initialize CLIP model.

        Args:
            model_name: CLIP model variant (default: ViT-B/32)
        """
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        logger.info(f"Loading CLIP model '{model_name}' on device: {self.device}")

        self.model, self.preprocess = clip.load(model_name, self.device)
        logger.info("CLIP model loaded successfully")

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

    def embed_image_file(self, filepath: str):
        """
        Generate embedding for an image file.

        Args:
            filepath: Path to image file
                      - Can be absolute path
                      - Can be relative path starting with "images/" (will be resolved from project root)

        Returns:
            numpy array containing the image embedding
        """
        try:
            import os

            # If absolute path, use as-is
            if os.path.isabs(filepath):
                resolved_path = filepath
            # If path starts with "images/", it's stored in data/uploads/images/ but DB has "images/" prefix
            elif filepath.startswith("images/"):
                # Convert images/ -> data/uploads/images/
                relative_path = filepath.replace("images/", "data/uploads/images/", 1)

                if os.path.exists("/app/data/uploads"):
                    # Docker environment
                    resolved_path = os.path.join("/app", relative_path)
                else:
                    # Local development - go up from python-search-service/ to project root
                    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
                    resolved_path = os.path.join(project_root, relative_path)
            else:
                # Other relative paths - resolve from current directory
                resolved_path = filepath

            logger.debug(f"Resolving image path: {filepath} -> {resolved_path}")
            pil_image = Image.open(resolved_path).convert("RGB")
            return self.embed_image(pil_image)
        except Exception as e:
            logger.error(f"Failed to embed image {filepath}: {e}")
            raise

    def embed_text(self, text: str):
        """
        Generate embedding for text query.

        Args:
            text: Text string to embed

        Returns:
            numpy array containing the text embedding
        """
        text_tokens = clip.tokenize([text]).to(self.device)
        with torch.no_grad():
            text_features = self.model.encode_text(text_tokens)
        return text_features.cpu().numpy()
