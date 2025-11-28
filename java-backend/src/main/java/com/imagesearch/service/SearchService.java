package com.imagesearch.service;

import com.imagesearch.client.PythonSearchClient;
import com.imagesearch.client.dto.SearchServiceRequest;
import com.imagesearch.client.dto.SearchServiceResponse;
import com.imagesearch.model.dto.response.SearchResponse;
import com.imagesearch.model.entity.Folder;
import com.imagesearch.model.entity.Image;
import com.imagesearch.repository.FolderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for semantic image search.
 *
 * This is the key integration point between Java backend and Python microservice:
 * 1. Gets accessible folders from database
 * 2. Builds folder ownership map
 * 3. Calls Python service for FAISS search
 * 4. Enriches results with image metadata from database
 * 5. Returns complete response to frontend
 *
 * This demonstrates microservices orchestration - a common interview topic!
 */
@Service
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private final PythonSearchClient pythonSearchClient;
    private final FolderRepository folderRepository;
    private final FolderService folderService;
    private final ImageService imageService;

    @Value("${storage.backend:local}")
    private String storageBackend;

    public SearchService(
            PythonSearchClient pythonSearchClient,
            FolderRepository folderRepository,
            FolderService folderService,
            ImageService imageService) {
        this.pythonSearchClient = pythonSearchClient;
        this.folderRepository = folderRepository;
        this.folderService = folderService;
        this.imageService = imageService;
    }

    /**
     * Search for images using text query.
     *
     * Orchestration flow:
     * 1. Determine which folders to search (user-specified or all accessible)
     * 2. Build folder ownership map (needed for FAISS index paths)
     * 3. Call Python microservice for semantic search
     * 4. Enrich results with database metadata (image paths)
     * 5. Return complete response
     *
     * @param userId User ID (for authorization)
     * @param query Search query text
     * @param folderIds Optional list of folder IDs to search (null = all accessible)
     * @param topK Number of results to return
     * @return Search response with image URLs and similarity scores
     */
    public SearchResponse searchImages(Long userId, String query, List<Long> folderIds, Integer topK) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (query == null || query.trim().isEmpty()) {
            logger.info("Empty search query - returning empty results");
            return new SearchResponse(new ArrayList<>());
        }

        logger.info("Searching images: user={}, query='{}', folders={}, topK={}",
                    userId, query, folderIds, topK);

        // Step 1: Determine which folders to search
        List<Long> searchFolderIds;
        Map<Long, Long> folderOwnerMap = new HashMap<>();

        if (folderIds == null || folderIds.isEmpty()) {
            // Search all accessible folders (owned + shared)
            List<Folder> accessibleFolders = folderRepository.findAllAccessibleFolders(userId);
            searchFolderIds = new ArrayList<>();

            for (Folder folder : accessibleFolders) {
                searchFolderIds.add(folder.getId());
                folderOwnerMap.put(folder.getId(), folder.getUser().getId());
            }

        } else {
            // Search specified folders (verify access)
            searchFolderIds = new ArrayList<>();

            for (Long folderId : folderIds) {
                if (folderId != null) {
                    // This throws if user doesn't have access
                    Folder folder = folderService.checkFolderAccess(userId, folderId);
                    searchFolderIds.add(folderId);
                    folderOwnerMap.put(folderId, folder.getUser().getId());
                }
            }
        }

        if (searchFolderIds.isEmpty()) {
            logger.warn("No folders to search for user {}", userId);
            return new SearchResponse(List.of());
        }

        // Step 2: Call Python microservice for FAISS search
        SearchServiceRequest searchRequest = new SearchServiceRequest(
            userId,
            query,
            searchFolderIds,
            folderOwnerMap,
            topK != null ? topK : 5
        );

        SearchServiceResponse pythonResponse = pythonSearchClient.search(searchRequest);

        // Step 3: Enrich results with database metadata
        List<SearchResponse.ImageSearchResult> results = new ArrayList<>();

        for (SearchServiceResponse.SearchResult result : pythonResponse.getResults()) {
            Image image = imageService.getImageById(result.getImageId());
            if (image != null) {
                String imageUrl = getImageUrl(image.getFilepath());
                results.add(new SearchResponse.ImageSearchResult(
                    imageUrl,
                    result.getScore()
                ));
            }
        }

        logger.info("Search completed: {} results found", results.size());
        return new SearchResponse(results);
    }

    /**
     * Convert filepath to URL based on storage backend.
     */
    private String getImageUrl(String filepath) {
        // For local storage, serve from /images static endpoint
        // Return full URL with http://localhost:8080 prefix for frontend
        // For S3, this would return the S3 URL
        return "http://localhost:8080/" + filepath;
    }
}
