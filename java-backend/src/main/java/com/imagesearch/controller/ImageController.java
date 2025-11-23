package com.imagesearch.controller;

import com.imagesearch.model.dto.response.SearchResponse;
import com.imagesearch.model.dto.response.UploadResponse;
import com.imagesearch.service.ImageService;
import com.imagesearch.service.SearchService;
import com.imagesearch.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * REST Controller for image management endpoints.
 *
 * RESTful API design:
 * - POST /api/images/upload - Upload images to a folder
 * - GET /api/images/search - Search images by text query
 *
 * Demonstrates:
 * - Multipart file upload handling
 * - Query parameter parsing
 * - Integration with search microservice
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    private final ImageService imageService;
    private final SearchService searchService;
    private final SessionService sessionService;

    public ImageController(
            ImageService imageService,
            SearchService searchService,
            SessionService sessionService) {
        this.imageService = imageService;
        this.searchService = searchService;
        this.sessionService = sessionService;
    }

    /**
     * Upload images to a folder.
     * POST /api/images/upload
     *
     * Multipart form data:
     * - token: Session token
     * - folderName: Target folder name
     * - files: Image files
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadImages(
            @RequestParam("token") String token,
            @RequestParam("folderName") String folderName,
            @RequestParam("files") MultipartFile[] files) {

        Long userId = sessionService.validateTokenAndGetUserId(token);
        logger.info("Upload images request: user={}, folder={}, count={}",
                    userId, folderName, files.length);

        List<MultipartFile> fileList = Arrays.asList(files);
        UploadResponse response = imageService.uploadImages(userId, folderName, fileList);

        return ResponseEntity.ok(response);
    }

    /**
     * Search images by text query.
     * GET /api/images/search?token=xxx&query=sunset&folder_ids=1,2&top_k=5
     *
     * Query params:
     * - token: Session token
     * - query: Search query text
     * - folder_ids: Optional comma-separated folder IDs
     * - top_k: Number of results (default 5)
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> searchImages(
            @RequestParam("token") String token,
            @RequestParam("query") String query,
            @RequestParam(value = "folder_ids", required = false) String folderIdsParam,
            @RequestParam(value = "top_k", defaultValue = "5") Integer topK) {

        Long userId = sessionService.validateTokenAndGetUserId(token);
        logger.info("Search images request: user={}, query='{}', topK={}",
                    userId, query, topK);

        // Parse folder_ids from comma-separated string
        List<Long> folderIds = null;
        if (folderIdsParam != null && !folderIdsParam.isEmpty()) {
            folderIds = Arrays.stream(folderIdsParam.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .toList();
        }

        SearchResponse response = searchService.searchImages(userId, query, folderIds, topK);
        return ResponseEntity.ok(response);
    }
}
