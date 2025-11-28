package com.imagesearch.client;

import com.imagesearch.client.dto.EmbedImagesRequest;
import com.imagesearch.client.dto.SearchServiceRequest;
import com.imagesearch.client.dto.SearchServiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * HTTP client for communicating with Python search microservice.
 *
 * This demonstrates microservices communication patterns:
 * - Service-to-service HTTP calls
 * - DTO mapping between services
 * - Error handling for external service failures
 * - Timeout configuration
 *
 * Uses Spring WebClient (reactive, non-blocking HTTP client).
 * This is the modern replacement for RestTemplate.
 */
@Component
public class PythonSearchClient {

    private static final Logger logger = LoggerFactory.getLogger(PythonSearchClient.class);

    private final WebClient webClient;
    private final int timeoutSeconds;

    @SuppressWarnings("null")
    public PythonSearchClient(
            WebClient.Builder webClientBuilder,
            @Value("${search-service.base-url}") String baseUrl,
            @Value("${search-service.timeout-seconds}") int timeoutSeconds) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.timeoutSeconds = timeoutSeconds;
        logger.info("PythonSearchClient initialized with base URL: {}", baseUrl);
    }

    /**
     * Call Python service to perform semantic image search.
     *
     * @param request Search parameters (query, folders, etc.)
     * @return Search results with image IDs and similarity scores
     * @throws RuntimeException if service call fails
     */
    public SearchServiceResponse search(SearchServiceRequest request) {
        logger.info("Calling Python search service: query='{}', folders={}",
                    request.getQuery(), request.getFolderIds());

        try {
            SearchServiceResponse response = webClient.post()
                    .uri("/api/search")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SearchServiceResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block(); // Block for synchronous behavior

            logger.info("Python search service returned {} results",
                        response != null ? response.getResults().size() : 0);
            return response;

        } catch (Exception e) {
            logger.error("Failed to call Python search service", e);
            throw new RuntimeException("Search service unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Call Python service to generate embeddings and add to FAISS index.
     *
     * This is called asynchronously after image upload.
     *
     * @param request Image information for embedding
     */
    public void embedImages(EmbedImagesRequest request) {
        logger.info("Calling Python service to embed {} images for folder {}",
                    request.getImages().size(), request.getFolderId());

        try {
            webClient.post()
                    .uri("/api/embed-images")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .subscribe(
                        result -> logger.info("Successfully embedded images for folder {}",
                                            request.getFolderId()),
                        error -> logger.error("Failed to embed images for folder {}",
                                            request.getFolderId(), error)
                    );

        } catch (Exception e) {
            logger.error("Failed to call Python embed service", e);
            // Don't throw - this is a background operation
        }
    }

    /**
     * Call Python service to create FAISS index for a new folder.
     *
     * @param userId User ID
     * @param folderId Folder ID
     */
    public void createFaissIndex(Long userId, Long folderId) {
        logger.info("Creating FAISS index for user {} folder {}", userId, folderId);

        try {
            webClient.post()
                    .uri("/api/create-index")
                    .bodyValue(new CreateIndexRequest(userId, folderId))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            logger.info("Successfully created FAISS index");

        } catch (Exception e) {
            logger.error("Failed to create FAISS index", e);
            // Don't throw - non-critical for folder creation
        }
    }

    /**
     * Call Python service to delete FAISS index for a folder.
     *
     * @param userId User ID
     * @param folderId Folder ID
     */
    public void deleteFaissIndex(Long userId, Long folderId) {
        logger.info("Deleting FAISS index for user {} folder {}", userId, folderId);

        try {
            webClient.delete()
                    .uri("/api/delete-index/{userId}/{folderId}", userId, folderId)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            logger.info("Successfully deleted FAISS index");

        } catch (Exception e) {
            logger.error("Failed to delete FAISS index", e);
            // Don't throw - best effort cleanup
        }
    }

    // Helper DTO for create index request
    private record CreateIndexRequest(Long userId, Long folderId) {}
}
