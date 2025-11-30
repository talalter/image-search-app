package com.imagesearch.client;

import com.imagesearch.client.dto.EmbedImagesRequest;
import com.imagesearch.client.dto.SearchServiceRequest;
import com.imagesearch.client.dto.SearchServiceResponse;

/**
 * Abstraction for search service clients.
 *
 * Allows switching between Python (FAISS) and Java (Elasticsearch) backends
 * via configuration without changing service layer code.
 *
 * This demonstrates the Strategy Pattern - a key design pattern interview topic:
 * - Dependency Inversion Principle: Services depend on abstraction, not concrete implementations
 * - Open/Closed Principle: Can add new search backends without modifying existing services
 * - Single Responsibility: Each implementation handles one backend technology
 *
 * Configuration:
 * - Set environment variable SEARCH_BACKEND=python for Python/FAISS backend (default)
 * - Set environment variable SEARCH_BACKEND=java for Java/Elasticsearch backend
 *
 * Spring's @ConditionalOnProperty ensures only ONE implementation loads at runtime.
 */
public interface SearchClient {

    /**
     * Perform semantic image search using text query.
     *
     * @param request Search parameters (query, folders, etc.)
     * @return Search results with image IDs and similarity scores
     */
    SearchServiceResponse search(SearchServiceRequest request);

    /**
     * Generate embeddings for uploaded images and add to search index.
     *
     * This is called asynchronously after image upload.
     *
     * @param request Image information for embedding
     */
    void embedImages(EmbedImagesRequest request);

    /**
     * Create search index for a new folder.
     *
     * @param userId User ID
     * @param folderId Folder ID
     */
    void createIndex(Long userId, Long folderId);

    /**
     * Delete search index for a folder.
     *
     * @param userId User ID
     * @param folderId Folder ID
     */
    void deleteIndex(Long userId, Long folderId);
}
