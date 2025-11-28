#!/usr/bin/env python3
"""
Convert CLIP PyTorch model to ONNX format for Java Search Service

This script exports the OpenAI CLIP ViT-B/32 model to ONNX format
so it can be used with ONNX Runtime in the Java search service.

Requirements:
    pip install torch transformers onnx onnxruntime

Usage:
    python convert_clip_to_onnx.py
"""

import torch
import onnx
from transformers import CLIPModel, CLIPProcessor, CLIPTokenizer
import os

def export_clip_text_encoder():
    """Export CLIP text encoder to ONNX"""
    print("Loading CLIP model from HuggingFace...")
    model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32")
    tokenizer = CLIPTokenizer.from_pretrained("openai/clip-vit-base-patch32")

    # Set to eval mode
    model.eval()
    text_model = model.text_model
    text_projection = model.text_projection

    # Create dummy input
    dummy_text = "a photo of a cat"
    inputs = tokenizer(dummy_text, return_tensors="pt", padding=True, max_length=77, truncation=True)

    print("Exporting text encoder to ONNX...")
    output_path = "src/main/resources/models/clip-text-encoder.onnx"
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    # Export to ONNX
    torch.onnx.export(
        text_model,
        (inputs['input_ids'], inputs['attention_mask']),
        output_path,
        input_names=['input_ids', 'attention_mask'],
        output_names=['text_embeddings'],
        dynamic_axes={
            'input_ids': {0: 'batch_size', 1: 'sequence'},
            'attention_mask': {0: 'batch_size', 1: 'sequence'},
            'text_embeddings': {0: 'batch_size'}
        },
        opset_version=14
    )
    print(f"✓ Text encoder exported to {output_path}")

    # Verify the model
    onnx_model = onnx.load(output_path)
    onnx.checker.check_model(onnx_model)
    print("✓ ONNX model verified")

    return output_path

def export_clip_vision_encoder():
    """Export CLIP vision encoder to ONNX"""
    print("\nLoading CLIP vision model...")
    model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32")
    processor = CLIPProcessor.from_pretrained("openai/clip-vit-base-patch32")

    # Set to eval mode
    model.eval()
    vision_model = model.vision_model

    # Create dummy input (batch_size=1, channels=3, height=224, width=224)
    dummy_image = torch.randn(1, 3, 224, 224)

    print("Exporting vision encoder to ONNX...")
    output_path = "src/main/resources/models/clip-vision-encoder.onnx"

    # Export to ONNX
    torch.onnx.export(
        vision_model,
        dummy_image,
        output_path,
        input_names=['pixel_values'],
        output_names=['image_embeddings'],
        dynamic_axes={
            'pixel_values': {0: 'batch_size'},
            'image_embeddings': {0: 'batch_size'}
        },
        opset_version=14
    )
    print(f"✓ Vision encoder exported to {output_path}")

    # Verify the model
    onnx_model = onnx.load(output_path)
    onnx.checker.check_model(onnx_model)
    print("✓ ONNX model verified")

    return output_path

def export_full_clip_model():
    """Export full CLIP model (combined text + vision) to ONNX"""
    print("\nLoading full CLIP model...")
    model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32")

    # Set to eval mode
    model.eval()

    # Create a wrapper class that combines both encoders
    class CLIPWrapper(torch.nn.Module):
        def __init__(self, clip_model):
            super().__init__()
            self.model = clip_model

        def forward(self, input_ids, attention_mask, pixel_values):
            text_outputs = self.model.text_model(input_ids=input_ids, attention_mask=attention_mask)
            vision_outputs = self.model.vision_model(pixel_values=pixel_values)

            text_embeds = text_outputs.pooler_output
            image_embeds = vision_outputs.pooler_output

            # Apply projections and normalize
            text_embeds = self.model.text_projection(text_embeds)
            image_embeds = self.model.visual_projection(image_embeds)

            text_embeds = text_embeds / text_embeds.norm(p=2, dim=-1, keepdim=True)
            image_embeds = image_embeds / image_embeds.norm(p=2, dim=-1, keepdim=True)

            return text_embeds, image_embeds

    wrapper = CLIPWrapper(model)

    # Create dummy inputs
    dummy_input_ids = torch.randint(0, 49408, (1, 77))
    dummy_attention_mask = torch.ones(1, 77)
    dummy_pixel_values = torch.randn(1, 3, 224, 224)

    print("Exporting full CLIP model to ONNX...")
    output_path = "src/main/resources/models/clip-vit-base-patch32.onnx"

    # Export to ONNX
    torch.onnx.export(
        wrapper,
        (dummy_input_ids, dummy_attention_mask, dummy_pixel_values),
        output_path,
        input_names=['input_ids', 'attention_mask', 'pixel_values'],
        output_names=['text_embeddings', 'image_embeddings'],
        dynamic_axes={
            'input_ids': {0: 'batch_size'},
            'attention_mask': {0: 'batch_size'},
            'pixel_values': {0: 'batch_size'}
        },
        opset_version=14
    )
    print(f"✓ Full CLIP model exported to {output_path}")

    # Verify the model
    onnx_model = onnx.load(output_path)
    onnx.checker.check_model(onnx_model)
    print("✓ ONNX model verified")

    # Print model info
    print(f"\n✓ Model size: {os.path.getsize(output_path) / (1024*1024):.1f} MB")
    print(f"✓ Embedding dimension: 512")

    return output_path

if __name__ == "__main__":
    print("=" * 60)
    print("CLIP PyTorch → ONNX Conversion Tool")
    print("=" * 60)

    try:
        # Export separate encoders (more flexible)
        text_path = export_clip_text_encoder()
        vision_path = export_clip_vision_encoder()

        # Also export combined model (simpler for some use cases)
        # full_path = export_full_clip_model()

        print("\n" + "=" * 60)
        print("✓ Conversion completed successfully!")
        print("=" * 60)
        print(f"\nExported models:")
        print(f"  - Text encoder: {text_path}")
        print(f"  - Vision encoder: {vision_path}")
        print(f"\nYou can now use these models in the Java Search Service.")

    except Exception as e:
        print(f"\n✗ Error during conversion: {e}")
        print("\nMake sure you have installed the required packages:")
        print("  pip install torch transformers onnx onnxruntime")
        exit(1)
