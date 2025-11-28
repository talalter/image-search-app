package com.imagesearch.search.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for CLIP ONNX model.
 *
 * Best Practice: Externalize configuration using @ConfigurationProperties
 * for type-safe, validated configuration management.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "clip.model")
public class ClipModelProperties {

    /**
     * Path to CLIP text encoder ONNX model
     */
    @NotBlank
    private String textModelPath = "models/clip-vit-base-patch32-text.onnx";

    /**
     * Path to CLIP image encoder ONNX model
     */
    @NotBlank
    private String imageModelPath = "models/clip-vit-base-patch32-visual.onnx";

    /**
     * Path to CLIP vocabulary file (BPE vocab)
     */
    @NotBlank
    private String vocabPath = "models/bpe_simple_vocab_16e6.txt";

    /**
     * Path to CLIP merges file (BPE merges)
     */
    @NotBlank
    private String mergesPath = "models/bpe_simple_merges_16e6.txt";

    /**
     * Embedding dimension (default: 512 for ViT-B/32)
     */
    @Min(1)
    private int embeddingDimension = 512;

    /**
     * Maximum text sequence length (CLIP default: 77 tokens)
     */
    @Min(1)
    private int maxTextLength = 77;

    /**
     * Image input size (CLIP default: 224x224)
     */
    @Min(1)
    private int imageSize = 224;

    /**
     * Number of threads for ONNX Runtime inference
     * 0 = auto-detect based on CPU cores
     */
    @Min(0)
    private int inferenceThreads = 0;

    /**
     * Enable GPU acceleration (requires ONNX Runtime GPU build)
     */
    private boolean useGpu = false;

    /**
     * Download models automatically if not found
     */
    private boolean autoDownload = true;

    /**
     * Hugging Face model repository ID
     */
    private String huggingFaceModelId = "openai/clip-vit-base-patch32";
}
