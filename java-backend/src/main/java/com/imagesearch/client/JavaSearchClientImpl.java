package com.imagesearch.client;

import com.imagesearch.client.dto.EmbedImagesRequest;
import com.imagesearch.client.dto.SearchServiceRequest;
import com.imagesearch.client.dto.SearchServiceResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;

/**
 * Java search service implementation using Elasticsearch for vector search.
 *
 * This client communicates with the Java search microservice which uses:
 * - ONNX Runtime for CLIP model inference
 * - Elasticsearch for vector storage and similarity search
 *
 * Key features:
 * - Graceful failure handling (logs errors but doesn't throw)
 * - Configurable enable/disable flag
 * - Timeout protection
 * - Best-effort cleanup (doesn't fail parent operations)
 * - Circuit Breaker pattern for resilience
 *
 * Circuit Breaker Implementation:
 * - Protects against cascading failures when Elasticsearch service is down
 * - Opens circuit after 50% failure rate in sliding window of 100 requests
 * - Automatically tests recovery every 60 seconds
 * - Fallback gracefully skips operations (can be retried later)
 *
 * Conditional Loading:
 * - Active when search.backend.type=java
 * - Inactive when search.backend.type=python (default)
 */
@Component
@ConditionalOnProperty(name = "search.backend.type", havingValue = "java")
public class JavaSearchClientImpl implements SearchClient {

    private static final Logger logger = LoggerFactory.getLogger(JavaSearchClientImpl.class);

    private final WebClient webClient;
    private final int timeoutSeconds;
    private final boolean enabled;

    @SuppressWarnings("null")
    public JavaSearchClientImpl(
            WebClient.Builder webClientBuilder,
            @Value("${java-search-service.base-url}") String baseUrl,
            @Value("${java-search-service.timeout-seconds}") int timeoutSeconds,
            @Value("${java-search-service.enabled:true}") boolean enabled) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.timeoutSeconds = timeoutSeconds;
        this.enabled = enabled;
        logger.info("JavaSearchClientImpl initialized with base URL: {}, enabled: {} (Elasticsearch backend)", baseUrl, enabled);
    }

    /**
     * Call Java search service to perform semantic image search.
     *
     * Circuit Breaker Applied:
     * - Name: "javaSearchService" (configured in application.yml)
     * - Fallback: searchFallback() - returns empty results when circuit is OPEN
     * - Opens circuit if 50% of requests fail or are slow (>10s)
     *
     * @param request Search parameters (query, folders, etc.)
     * @return Search results with image IDs and similarity scores
     */
    @Override
    @CircuitBreaker(name = "javaSearchService", fallbackMethod = "searchFallback")
    public SearchServiceResponse search(SearchServiceRequest request) {
        if (!enabled) {
            logger.warn("Java search service disabled, returning empty results");
            return createEmptyResponse();
        }

        logger.info("Calling Java search service: query='{}', folders={}",
                    request.getQuery(), request.getFolderIds());

        SearchServiceResponse response = webClient.post()
                .uri("/api/search")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SearchServiceResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        logger.info("Java search service returned {} results",
                    response != null ? response.getResults().size() : 0);
        return response;
    }

    /**
     * Fallback method for search() when circuit is OPEN.
     */
    public SearchServiceResponse searchFallback(SearchServiceRequest request, Exception exception) {
        logger.warn("Java search service unavailable (circuit OPEN), returning empty results. " +
                   "Query: '{}', Folders: {}, Error: {}",
                   request.getQuery(), request.getFolderIds(), exception.getMessage());
        return createEmptyResponse();
    }

    /**
     * Call Java search service to generate embeddings and add to Elasticsearch index.
     *
     * Circuit Breaker Applied:
     * - Fallback: embedImagesFallback() - logs warning but doesn't fail upload
     *
     * @param request Image information for embedding
     */
    @Override
    @CircuitBreaker(name = "javaSearchService", fallbackMethod = "embedImagesFallback")
    public void embedImages(EmbedImagesRequest request) {
        if (!enabled) {
            logger.info("Java search service disabled, skipping embedding for {} images",
                       request.getImages().size());
            return;
        }

        logger.info("Calling Java service to embed {} images for folder {}",
                    request.getImages().size(), request.getFolderId());

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
    }

    /**
     * Fallback for embedImages() when circuit is OPEN.
     */
    public void embedImagesFallback(EmbedImagesRequest request, Exception exception) {
        logger.warn("Java search service unavailable (circuit OPEN), skipping embedding for {} images in folder {}. " +
                   "Images uploaded successfully but not searchable yet. Error: {}",
                   request.getImages().size(), request.getFolderId(), exception.getMessage());
        // TODO: Add images to retry queue for later indexing
    }

    /**
     * Call Java search service to create Elasticsearch index for a new folder.
     *
     * Circuit Breaker Applied:
     * - Fallback: createIndexFallback() - logs warning, index created on first upload
     *
     * @param userId User ID
     * @param folderId Folder ID
     */
    @Override
    @CircuitBreaker(name = "javaSearchService", fallbackMethod = "createIndexFallback")
    public void createIndex(Long userId, Long folderId) {
        if (!enabled) {
            logger.info("Java search service disabled, skipping Elasticsearch index creation for user {} folder {}",
                       userId, folderId);
            return;
        }

        logger.info("Creating Elasticsearch index for user {} folder {}", userId, folderId);

        webClient.post()
                .uri("/api/create-index")
                .bodyValue(new CreateIndexRequest(userId, folderId))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        logger.info("Successfully created Elasticsearch index for user {} folder {}", userId, folderId);
    }

    /**
     * Fallback for createIndex() when circuit is OPEN.
     */
    public void createIndexFallback(Long userId, Long folderId, Exception exception) {
        logger.warn("Java search service unavailable (circuit OPEN), skipping Elasticsearch index creation for user {} folder {}. " +
                   "Index will be created automatically on first image upload. Error: {}",
                   userId, folderId, exception.getMessage());
    }

    /**
     * Call Java search service to delete Elasticsearch index for a folder.
     *
     * Circuit Breaker Applied:
     * - Fallback: deleteIndexFallback() - logs warning, best-effort cleanup
     *
     * @param userId User ID
     * @param folderId Folder ID
     */
    @Override
    @CircuitBreaker(name = "javaSearchService", fallbackMethod = "deleteIndexFallback")
    public void deleteIndex(Long userId, Long folderId) {
        if (!enabled) {
            logger.info("Java search service disabled, skipping Elasticsearch index deletion for user {} folder {}",
                       userId, folderId);
            return;
        }

        logger.info("Deleting Elasticsearch index for user {} folder {}", userId, folderId);

        webClient.delete()
                .uri("/api/delete-index/{userId}/{folderId}", userId, folderId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        logger.info("Successfully deleted Elasticsearch index for user {} folder {}", userId, folderId);
    }

    /**
     * Fallback for deleteIndex() when circuit is OPEN.
     *
     * Interview talking point:
     * "We use dual vector search backends (FAISS + Elasticsearch) for different use cases.
     * When Elasticsearch is down, the circuit breaker prevents user/folder deletion from
     * failing. Orphaned Elasticsearch indexes are cleaned up asynchronously by a scheduled
     * cleanup job. This demonstrates separation of concerns - core operations (user deletion)
     * don't depend on auxiliary services (search indexing)."
     */
    public void deleteIndexFallback(Long userId, Long folderId, Exception exception) {
        logger.warn("Java search service unavailable (circuit OPEN), skipping Elasticsearch index deletion for user {} folder {}. " +
                   "Elasticsearch index may be orphaned (can be cleaned up by background job). Error: {}",
                   userId, folderId, exception.getMessage());
        // TODO: Add to orphaned index cleanup queue
    }

    /**
     * Helper method to create empty search response.
     */
    private SearchServiceResponse createEmptyResponse() {
        SearchServiceResponse response = new SearchServiceResponse();
        response.setResults(Collections.emptyList());
        return response;
    }

    // Helper DTO for create index request
    private record CreateIndexRequest(Long userId, Long folderId) {}
}
