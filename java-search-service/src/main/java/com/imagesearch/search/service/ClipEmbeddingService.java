package com.imagesearch.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * CLIP Embedding Service
 *
 * Generates embeddings for text and images using CLIP model.
 *
 * Current Implementation: Delegates to Python search service for CLIP embeddings
 * Future Enhancement: Use ONNX Runtime for direct Java inference
 *
 * Why Python delegation for now:
 * - Python CLIP library is mature and well-tested
 * - Allows immediate functionality while ONNX model is being prepared
 * - Easy to swap out later with ONNX implementation
 *
 * TODO: Implement ONNX-based embedding generation:
 * - Download CLIP ONNX model (openai/clip-vit-base-patch32)
 * - Use ONNX Runtime Java API for inference
 * - Implement custom tokenizer for text
 * - Implement image preprocessing (resize, normalize)
 */
@Service
@Slf4j
public class ClipEmbeddingService {

    @Value("${clip.embedding.dimension:512}")
    private int embeddingDimension;

    @Value("${python.search.service.url:http://localhost:5000}")
    private String pythonSearchServiceUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClipEmbeddingService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        log.info("CLIP Embedding Service initialized (using Python delegation)");
    }

    /**
     * Generate embedding for text query
     *
     * @param text Text query (e.g., "sunset over mountains")
     * @return 512-dimensional embedding vector
     * @throws Exception if embedding generation fails
     */
    public float[] embedText(String text) throws Exception {
        log.debug("Generating text embedding for: {}", text);

        // Prepare request to Python search service
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("text", text);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pythonSearchServiceUrl + "/embed-text"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to generate text embedding: " + response.body());
        }

        // Parse response
        JsonNode jsonNode = objectMapper.readTree(response.body());
        JsonNode embeddingNode = jsonNode.get("embedding");

        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new RuntimeException("Invalid embedding response format");
        }

        // Convert JSON array to float array
        float[] embedding = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = (float) embeddingNode.get(i).asDouble();
        }

        // Normalize to L2 norm = 1 (if not already normalized)
        embedding = normalizeVector(embedding);

        log.debug("Generated text embedding: {} dimensions", embedding.length);
        return embedding;
    }

    /**
     * Generate embedding for image file
     *
     * @param imagePath Path to image file (relative or absolute)
     * @return 512-dimensional embedding vector
     * @throws Exception if embedding generation fails
     */
    public float[] embedImage(String imagePath) throws Exception {
        log.debug("Generating image embedding for: {}", imagePath);

        // Prepare request to Python search service
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("image_path", imagePath);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pythonSearchServiceUrl + "/embed-image"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to generate image embedding: " + response.body());
        }

        // Parse response
        JsonNode jsonNode = objectMapper.readTree(response.body());
        JsonNode embeddingNode = jsonNode.get("embedding");

        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new RuntimeException("Invalid embedding response format");
        }

        // Convert JSON array to float array
        float[] embedding = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = (float) embeddingNode.get(i).asDouble();
        }

        // Normalize to L2 norm = 1 (if not already normalized)
        embedding = normalizeVector(embedding);

        log.debug("Generated image embedding: {} dimensions", embedding.length);
        return embedding;
    }

    /**
     * Normalize vector to L2 norm = 1
     *
     * After normalization, cosine similarity = dot product
     */
    private float[] normalizeVector(float[] vector) {
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm < 1e-6) {
            log.warn("Vector norm too small, returning as-is");
            return vector;
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }

        return normalized;
    }

    /**
     * Check if Python search service is available
     */
    public boolean isPythonServiceAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonSearchServiceUrl + "/health"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Python search service not available: {}", e.getMessage());
            return false;
        }
    }
}
