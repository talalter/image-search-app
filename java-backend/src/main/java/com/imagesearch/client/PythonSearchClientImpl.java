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
 * Python search service implementation using FAISS for vector search.
 *
 * This demonstrates microservices communication patterns:
 * - Service-to-service HTTP calls
 * - DTO mapping between services
 * - Error handling for external service failures
 * - Timeout configuration
 * - Circuit Breaker pattern for resilience
 *
 * Uses Spring WebClient (reactive, non-blocking HTTP client).
 * This is the modern replacement for RestTemplate.
 *
 * Circuit Breaker Implementation:
 * - Protects against cascading failures when Python service is down
 * - Opens circuit after 50% failure rate in sliding window of 100 requests
 * - Automatically tests recovery every 60 seconds
 * - Provides fallback responses when circuit is open
 *
 * Conditional Loading:
 * - Active when search.backend.type=python (default)
 * - Inactive when search.backend.type=java
 */
@Component
@ConditionalOnProperty(name = "search.backend.type", havingValue = "python", matchIfMissing = true)
public class PythonSearchClientImpl implements SearchClient {

    private static final Logger logger = LoggerFactory.getLogger(PythonSearchClientImpl.class);

    private final WebClient webClient;
    private final int timeoutSeconds;

    @SuppressWarnings("null")
    public PythonSearchClientImpl(
            WebClient.Builder webClientBuilder,
            @Value("${search-service.base-url}") String baseUrl,
            @Value("${search-service.timeout-seconds}") int timeoutSeconds) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.timeoutSeconds = timeoutSeconds;
        logger.info("PythonSearchClientImpl initialized with base URL: {} (FAISS backend)", baseUrl);
    }

    /**
     * Call Python service to perform semantic image search.
     *
     * Circuit Breaker Applied:
     * - Name: "pythonSearchService" (configured in application.yml)
     * - Fallback: searchFallback() - returns empty results when circuit is OPEN
     * - Opens circuit if 50% of requests fail or are slow (>10s)
     * - Protects thread pool from exhaustion when Python service is down
     *
     * @param request Search parameters (query, folders, etc.)
     * @return Search results with image IDs and similarity scores
     * @throws RuntimeException if service call fails and circuit is CLOSED
     */
    @Override
    @CircuitBreaker(name = "pythonSearchService", fallbackMethod = "searchFallback")
    public SearchServiceResponse search(SearchServiceRequest request) {
        logger.info("Calling Python search service: query='{}', folders={}",
                    request.getQuery(), request.getFolderIds());

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
    }

    /**
     * Fallback method for search() when circuit is OPEN.
     *
     * This is called when:
     * - Circuit breaker is OPEN (too many failures)
     * - Python service is down or slow
     * - Prevents waiting for timeout (fail fast)
     *
     * Interview talking point:
     * "When the search service is down, instead of making users wait 30 seconds,
     * we fail immediately and return an empty result set. This prevents thread
     * pool exhaustion and provides better UX (fast error > slow error)."
     *
     * Future improvement: Return cached search results instead of empty list
     *
     * @param request Original search request
     * @param exception The exception that triggered the fallback
     * @return Empty search response
     */
    public SearchServiceResponse searchFallback(SearchServiceRequest request, Exception exception) {
        logger.warn("Python search service unavailable (circuit OPEN), returning empty results. " +
                   "Query: '{}', Folders: {}, Error: {}",
                   request.getQuery(), request.getFolderIds(), exception.getMessage());

        // Return empty results instead of throwing exception
        // This allows the user to see that search is unavailable without crashing the app
        SearchServiceResponse emptyResponse = new SearchServiceResponse();
        emptyResponse.setResults(Collections.emptyList());
        return emptyResponse;
    }

    /**
     * Call Python service to generate embeddings and add to FAISS index.
     *
     * This is called asynchronously after image upload.
     *
     * Circuit Breaker Applied:
     * - Fallback: embedImagesFallback() - logs warning but doesn't fail upload
     * - This is already async, but circuit breaker prevents resource exhaustion
     *
     * @param request Image information for embedding
     */
    @Override
    @CircuitBreaker(name = "pythonSearchService", fallbackMethod = "embedImagesFallback")
    public void embedImages(EmbedImagesRequest request) {
        logger.info("Calling Python service to embed {} images for folder {}",
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
     *
     * Since embedding is async and non-critical for upload success,
     * we just log a warning. Images can be re-indexed later via batch job.
     *
     * @param request Original embed request
     * @param exception The exception that triggered fallback
     */
    public void embedImagesFallback(EmbedImagesRequest request, Exception exception) {
        logger.warn("Python search service unavailable (circuit OPEN), skipping embedding for {} images in folder {}. " +
                   "Images uploaded successfully but not searchable yet. Error: {}",
                   request.getImages().size(), request.getFolderId(), exception.getMessage());
        // TODO: Add images to retry queue for later indexing
    }

    /**
     * Call Python service to create FAISS index for a new folder.
     *
     * Circuit Breaker Applied:
     * - Fallback: createFaissIndexFallback() - logs warning, index created on first upload
     *
     * @param userId User ID
     * @param folderId Folder ID
     */
    @Override
    @CircuitBreaker(name = "pythonSearchService", fallbackMethod = "createIndexFallback")
    public void createIndex(Long userId, Long folderId) {
        logger.info("Creating FAISS index for user {} folder {}", userId, folderId);

        webClient.post()
                .uri("/api/create-index")
                .bodyValue(new CreateIndexRequest(userId, folderId))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        logger.info("Successfully created FAISS index for user {} folder {}", userId, folderId);
    }

    /**
     * Fallback for createIndex() when circuit is OPEN.
     *
     * Index creation is non-critical - it will be auto-created on first image upload.
     *
     * @param userId User ID
     * @param folderId Folder ID
     * @param exception The exception that triggered fallback
     */
    public void createIndexFallback(Long userId, Long folderId, Exception exception) {
        logger.warn("Python search service unavailable (circuit OPEN), skipping FAISS index creation for user {} folder {}. " +
                   "Index will be created automatically on first image upload. Error: {}",
                   userId, folderId, exception.getMessage());
    }

    /**
     * Call Python service to delete FAISS index for a folder.
     *
     * Circuit Breaker Applied:
     * - Fallback: deleteFaissIndexFallback() - logs warning, best-effort cleanup
     *
     * @param userId User ID
     * @param folderId Folder ID
     */
    @Override
    @CircuitBreaker(name = "pythonSearchService", fallbackMethod = "deleteIndexFallback")
    public void deleteIndex(Long userId, Long folderId) {
        logger.info("Deleting FAISS index for user {} folder {}", userId, folderId);

        webClient.delete()
                .uri("/api/delete-index/{userId}/{folderId}", userId, folderId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        logger.info("Successfully deleted FAISS index for user {} folder {}", userId, folderId);
    }

    /**
     * Fallback for deleteIndex() when circuit is OPEN.
     *
     * Deletion is best-effort cleanup. If it fails, orphaned index files can be
     * cleaned up later by a background cleanup job.
     *
     * Interview talking point:
     * "We don't want user deletion to fail just because the search service is down.
     * The circuit breaker allows us to skip index cleanup gracefully - orphaned
     * indexes are small and can be cleaned up by a scheduled job later."
     *
     * @param userId User ID
     * @param folderId Folder ID
     * @param exception The exception that triggered fallback
     */
    public void deleteIndexFallback(Long userId, Long folderId, Exception exception) {
        logger.warn("Python search service unavailable (circuit OPEN), skipping FAISS index deletion for user {} folder {}. " +
                   "Index file may be orphaned (can be cleaned up by background job). Error: {}",
                   userId, folderId, exception.getMessage());
    }

    // Helper DTO for create index request
    private record CreateIndexRequest(Long userId, Long folderId) {}
}
