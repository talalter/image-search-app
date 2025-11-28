package com.imagesearch.search.controller;

import com.imagesearch.search.model.CreateIndexRequest;
import com.imagesearch.search.model.EmbedRequest;
import com.imagesearch.search.model.SearchRequest;
import com.imagesearch.search.model.SearchResponse;
import com.imagesearch.search.service.embedding.OnnxClipEmbeddingService;
import com.imagesearch.search.service.ElasticsearchSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for Java Search Service
 *
 * Endpoints:
 * - POST /api/create-index: Create Elasticsearch index for a folder
 * - POST /api/embed-images: Generate and store embeddings for images
 * - POST /api/search: Semantic search across folders
 * - DELETE /api/delete-index/{userId}/{folderId}: Delete folder index
 * - GET /health: Health check endpoint
 * - GET /api/index-info/{userId}/{folderId}: Get index information
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class SearchController {

    private final OnnxClipEmbeddingService onnxClipService;
    private final ElasticsearchSearchService elasticsearchService;

    /**
     * Constructor injection - Best Practice for testability
     */
    public SearchController(
            OnnxClipEmbeddingService onnxClipService,
            ElasticsearchSearchService elasticsearchService) {

        this.onnxClipService = onnxClipService;
        this.elasticsearchService = elasticsearchService;

        log.info("Search Controller initialized with ONNX CLIP service and Elasticsearch");
    }

    /**
     * Create a new Elasticsearch index for a folder
     *
     * Request body:
     * {
     *   "user_id": 1,
     *   "folder_id": 5
     * }
     *
     * Response:
     * {
     *   "message": "Index created successfully"
     * }
     */
    @PostMapping("/create-index")
    public ResponseEntity<Map<String, Object>> createIndex(@RequestBody CreateIndexRequest request) {
        try {
            log.info("Creating index for user {} folder {}", request.getUserId(), request.getFolderId());

            elasticsearchService.createIndex(request.getUserId(), request.getFolderId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Index created successfully");
            response.put("user_id", request.getUserId());
            response.put("folder_id", request.getFolderId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to create index", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Generate and store embeddings for images
     *
     * Request body:
     * {
     *   "user_id": 1,
     *   "folder_id": 5,
     *   "images": [
     *     {"image_id": 10, "file_path": "data/uploads/images/1/5/image1.jpg"},
     *     {"image_id": 11, "file_path": "data/uploads/images/1/5/image2.jpg"}
     *   ]
     * }
     *
     * Response:
     * {
     *   "message": "Embeddings generated successfully",
     *   "count": 2
     * }
     */
    @PostMapping("/embed-images")
    public ResponseEntity<Map<String, Object>> embedImages(@RequestBody EmbedRequest request) {
        try {
            log.info("Embedding {} images for user {} folder {}",
                    request.getImages().size(), request.getUserId(), request.getFolderId());

            List<Long> imageIds = new ArrayList<>();
            List<float[]> embeddings = new ArrayList<>();

            // Generate embeddings for each image using ONNX
            for (EmbedRequest.ImageInfo img : request.getImages()) {
                try {
                    float[] embedding = onnxClipService.embedImage(img.getFilePath());
                    imageIds.add(img.getImageId());
                    embeddings.add(embedding);
                    log.debug("Generated ONNX embedding for image {}: {}", img.getImageId(), img.getFilePath());
                } catch (Exception e) {
                    log.error("Failed to embed image {}: {}", img.getImageId(), e.getMessage());
                    // Continue with other images
                }
            }

            // Store embeddings in Elasticsearch index
            if (!imageIds.isEmpty()) {
                elasticsearchService.addVectors(
                        request.getUserId(),
                        request.getFolderId(),
                        imageIds,
                        embeddings
                );
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Embeddings generated successfully");
            response.put("count", imageIds.size());
            response.put("failed", request.getImages().size() - imageIds.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to embed images", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Semantic search across folders
     *
     * Request body:
     * {
     *   "user_id": 1,
     *   "query": "sunset over mountains",
     *   "folder_ids": [5, 7],
     *   "top_k": 5
     * }
     *
     * Response:
     * {
     *   "results": [
     *     {"image_id": 10, "score": 0.95, "folder_id": 5},
     *     {"image_id": 15, "score": 0.87, "folder_id": 7}
     *   ],
     *   "total": 2
     * }
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest request) {
        try {
            log.info("Searching for '{}' in {} folders (top_k={})",
                    request.getQuery(), request.getFolderIds().size(), request.getTopK());

            // Generate query embedding using ONNX
            float[] queryEmbedding = onnxClipService.embedText(request.getQuery());

            // Search across folders
            List<ElasticsearchSearchService.ScoredImage> results = elasticsearchService.search(
                    request.getUserId(),
                    queryEmbedding,
                    request.getFolderIds(),
                    request.getTopK()
            );

            // Convert to response format
            List<SearchResponse.ScoredImage> responseResults = results.stream()
                    .map(r -> new SearchResponse.ScoredImage(r.imageId, r.score, r.folderId))
                    .collect(Collectors.toList());

            SearchResponse response = new SearchResponse(responseResults, responseResults.size());

            log.info("Found {} results for query '{}'", response.getTotal(), request.getQuery());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Search failed for query: {}", request.getQuery(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            errorResponse.put("query", request.getQuery());
            return ResponseEntity.status(500).body(
                new SearchResponse(new ArrayList<>(), 0)
            );
        }
    }

    /**
     * Delete index for a folder
     *
     * DELETE /api/delete-index/1/5
     *
     * Response:
     * {
     *   "message": "Index deleted successfully"
     * }
     */
    @DeleteMapping("/delete-index/{userId}/{folderId}")
    public ResponseEntity<Map<String, Object>> deleteIndex(
            @PathVariable Long userId,
            @PathVariable Long folderId) {
        try {
            log.info("Deleting index for user {} folder {}", userId, folderId);

            elasticsearchService.deleteIndex(userId, folderId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Index deleted successfully");
            response.put("user_id", userId);
            response.put("folder_id", folderId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to delete index", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get index information
     *
     * GET /api/index-info/1/5
     *
     * Response:
     * {
     *   "exists": true,
     *   "size": 42,
     *   "user_id": 1,
     *   "folder_id": 5
     * }
     */
    @GetMapping("/index-info/{userId}/{folderId}")
    public ResponseEntity<Map<String, Object>> getIndexInfo(
            @PathVariable Long userId,
            @PathVariable Long folderId) {
        try {
            boolean exists = elasticsearchService.indexExists(userId, folderId);
            long size = exists ? elasticsearchService.getIndexSize(userId, folderId) : 0;

            Map<String, Object> response = new HashMap<>();
            response.put("exists", exists);
            response.put("size", size);
            response.put("user_id", userId);
            response.put("folder_id", folderId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get index info", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     *
     * GET /health
     *
     * Response:
     * {
     *   "status": "healthy",
     *   "service": "java-search-service",
     *   "timestamp": "2024-11-26T10:30:00Z",
     *   "python_service_available": true
     * }
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "java-search-service");
        response.put("timestamp", Instant.now().toString());
        response.put("onnx_models_loaded", onnxClipService.isReady());
        response.put("embedding_dimension", onnxClipService.getEmbeddingDimension());

        return ResponseEntity.ok(response);
    }
}
