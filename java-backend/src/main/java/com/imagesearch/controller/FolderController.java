package com.imagesearch.controller;

import com.imagesearch.model.dto.request.DeleteFoldersRequest;
import com.imagesearch.model.dto.request.ShareFolderRequest;
import com.imagesearch.model.dto.response.FolderResponse;
import com.imagesearch.model.dto.response.MessageResponse;
import com.imagesearch.service.FolderService;
import com.imagesearch.service.SessionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for folder management endpoints.
 *
 * RESTful API design:
 * - GET /api/folders?token= - Get all accessible folders
 * - DELETE /api/folders - Delete folders
 * - POST /api/folders/share - Share folder with another user
 * - GET /api/folders/shared - Get folders shared with me
 *
 * Demonstrates resource-based routing and proper HTTP methods.
 */
@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private static final Logger logger = LoggerFactory.getLogger(FolderController.class);

    private final FolderService folderService;
    private final SessionService sessionService;

    public FolderController(FolderService folderService, SessionService sessionService) {
        this.folderService = folderService;
        this.sessionService = sessionService;
    }

    /**
     * Get all folders accessible to the user (owned + shared).
     * GET /api/folders?token=xxx
     */
    @GetMapping
    public ResponseEntity<FoldersResponse> getFolders(@RequestParam String token) {
        Long userId = sessionService.validateTokenAndGetUserId(token);
        logger.info("Get folders request for user: {}", userId);

        List<FolderResponse> folders = folderService.getAllAccessibleFolders(userId);

        // Separate owned and shared for backward compatibility
        List<FolderResponse> owned = folders.stream()
                .filter(f -> f.getIsOwner() != null && f.getIsOwner())
                .toList();

        List<FolderResponse> shared = folders.stream()
                .filter(f -> f.getIsShared() != null && f.getIsShared())
                .toList();

        return ResponseEntity.ok(new FoldersResponse(folders, owned, shared));
    }

    /**
     * Delete folders.
     * DELETE /api/folders
     */
    @DeleteMapping
    public ResponseEntity<MessageResponse> deleteFolders(@Valid @RequestBody DeleteFoldersRequest request) {
        Long userId = sessionService.validateTokenAndGetUserId(request.getToken());
        logger.info("Delete folders request for user: {}", userId);

        folderService.deleteFolders(request, userId);
        return ResponseEntity.ok(new MessageResponse(
            "Successfully deleted " + request.getFolderIds().size() + " folder(s)"
        ));
    }

    /**
     * Share a folder with another user.
     * POST /api/folders/share
     */
    @PostMapping("/share")
    public ResponseEntity<MessageResponse> shareFolder(@Valid @RequestBody ShareFolderRequest request) {
        Long userId = sessionService.validateTokenAndGetUserId(request.getToken());
        logger.info("Share folder request from user: {}", userId);

        folderService.shareFolder(request, userId);
        return ResponseEntity.ok(new MessageResponse("Folder shared successfully"));
    }

    /**
     * Get folders shared with the current user.
     * GET /api/folders/shared?token=xxx
     */
    @GetMapping("/shared")
    public ResponseEntity<List<FolderResponse>> getSharedFolders(@RequestParam String token) {
        Long userId = sessionService.validateTokenAndGetUserId(token);
        logger.info("Get shared folders request for user: {}", userId);

        List<FolderResponse> folders = folderService.getAllAccessibleFolders(userId);
        List<FolderResponse> shared = folders.stream()
                .filter(f -> f.getIsShared() != null && f.getIsShared())
                .toList();

        return ResponseEntity.ok(shared);
    }

    // Response DTO for backward compatibility with frontend
    private record FoldersResponse(
        List<FolderResponse> folders,
        List<FolderResponse> owned,
        List<FolderResponse> shared
    ) {}
}
