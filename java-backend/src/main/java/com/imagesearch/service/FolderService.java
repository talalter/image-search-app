package com.imagesearch.service;

import com.imagesearch.client.PythonSearchClient;
import com.imagesearch.exception.DuplicateResourceException;
import com.imagesearch.exception.ForbiddenException;
import com.imagesearch.exception.ResourceNotFoundException;
import com.imagesearch.model.dto.request.DeleteFoldersRequest;
import com.imagesearch.model.dto.request.ShareFolderRequest;
import com.imagesearch.model.dto.response.FolderResponse;
import com.imagesearch.model.entity.Folder;
import com.imagesearch.model.entity.FolderShare;
import com.imagesearch.model.entity.User;
import com.imagesearch.repository.FolderRepository;
import com.imagesearch.repository.FolderShareRepository;
import com.imagesearch.repository.ImageRepository;
import com.imagesearch.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Service for folder management.
 *
 * Handles:
 * - Creating folders
 * - Listing accessible folders (owned + shared)
 * - Deleting folders (with filesystem and FAISS cleanup)
 * - Folder sharing
 */
@Service
public class FolderService {

    private static final Logger logger = LoggerFactory.getLogger(FolderService.class);

    private final FolderRepository folderRepository;
    private final FolderShareRepository folderShareRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final PythonSearchClient pythonSearchClient;

    public FolderService(
            FolderRepository folderRepository,
            FolderShareRepository folderShareRepository,
            ImageRepository imageRepository,
            UserRepository userRepository,
            PythonSearchClient pythonSearchClient) {
        this.folderRepository = folderRepository;
        this.folderShareRepository = folderShareRepository;
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.pythonSearchClient = pythonSearchClient;
    }

    /**
     * Get all folders accessible to a user (owned + shared).
     *
     * @param userId User ID
     * @return List of folder responses
     */
    public List<FolderResponse> getAllAccessibleFolders(@NonNull Long userId) {
        Objects.requireNonNull(userId, "userId cannot be null");

        List<Folder> folders = folderRepository.findAllAccessibleFolders(userId);
        List<FolderResponse> responses = new ArrayList<>();

        for (Folder folder : folders) {
            FolderResponse response = new FolderResponse();
            response.setId(folder.getId());
            response.setFolderName(folder.getFolderName());

            // Check if user owns this folder
            if (folder.getUser().getId().equals(userId)) {
                response.setIsOwner(true);
                response.setIsShared(false);
            } else {
                // User doesn't own it - must be shared
                response.setIsOwner(false);
                response.setIsShared(true);
                response.setOwnerId(folder.getUser().getId());
                response.setOwnerUsername(folder.getUser().getUsername());

                // Get share details
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
                folderShareRepository.findByFolderAndSharedWithUser(folder, user)
                        .ifPresent(share -> {
                            response.setPermission(share.getPermission());
                            response.setSharedAt(share.getCreatedAt());
                        });
            }

            responses.add(response);
        }

        return responses;
    }

    /**
     * Create a new folder or get existing one.
     *
     * @param userId User ID
     * @param folderName Folder name
     * @return Created or existing folder
     */
    @Transactional
    public Folder createOrGetFolder(@NonNull Long userId, @NonNull String folderName) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(folderName, "folderName cannot be null");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Check if folder already exists
        return folderRepository.findByUserAndFolderName(user, folderName)
                .orElseGet(() -> {
                    // Create new folder
                    Folder folder = new Folder();
                    folder.setUser(user);
                    folder.setFolderName(folderName);
                    Folder savedFolder = folderRepository.save(folder);

                    // Create FAISS index in Python service
                    pythonSearchClient.createFaissIndex(userId, savedFolder.getId());

                    logger.info("Created new folder: id={}, name={}", savedFolder.getId(), folderName);
                    return savedFolder;
                });
    }

    /**
     * Delete multiple folders.
     *
     * Performs complete cleanup:
     * 1. Delete database records (folders, images, shares)
     * 2. Delete physical image files from filesystem
     * 3. Delete FAISS indexes via Python service
     *
     * @param request Delete request with folder IDs
     * @param userId User ID (for authorization)
     */
    @Transactional
    public void deleteFolders(@NonNull DeleteFoldersRequest request, @NonNull Long userId) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");

        logger.info("Deleting {} folders for user {}", request.getFolderIds().size(), userId);

        // Verify user exists (not actually used, just validation)
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        for (Long folderId : request.getFolderIds()) {
            Objects.requireNonNull(folderId, "folderId cannot be null");

            Folder folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder", folderId));

            // Authorization check
            if (!folder.getUser().getId().equals(userId)) {
                throw new ForbiddenException("You don't own this folder");
            }

            // 1. Delete database records
            imageRepository.deleteByFolder(folder);
            folderShareRepository.deleteByFolder(folder);
            folderRepository.delete(folder);

            // 2. Delete physical files
            deletePhysicalFolder(userId, folderId);

            // 3. Delete FAISS index
            pythonSearchClient.deleteFaissIndex(userId, folderId);

            logger.info("Deleted folder: id={}", folderId);
        }
    }

    /**
     * Share a folder with another user.
     *
     * @param request Share request
     * @param userId Owner user ID
     */
    @Transactional
    public void shareFolder(@NonNull ShareFolderRequest request, @NonNull Long userId) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");

        logger.info("Sharing folder {} with user {}", request.getFolderId(), request.getTargetUsername());

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Long folderId = Objects.requireNonNull(request.getFolderId(), "folderId cannot be null");
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder", folderId));

        // Authorization check
        if (!folder.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You don't own this folder");
        }

        // Find target user
        User targetUser = userRepository.findByUsername(request.getTargetUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getTargetUsername()));

        // Don't allow sharing with yourself
        if (owner.getId().equals(targetUser.getId())) {
            throw new ForbiddenException("Cannot share folder with yourself");
        }

        // Check if already shared
        if (folderShareRepository.existsByFolderAndSharedWithUser(folder, targetUser)) {
            throw new DuplicateResourceException("Folder already shared with this user");
        }

        // Create share
        FolderShare share = new FolderShare();
        share.setFolder(folder);
        share.setOwner(owner);
        share.setSharedWithUser(targetUser);
        share.setPermission(request.getPermission());

        folderShareRepository.save(share);
        logger.info("Folder shared successfully");
    }

    /**
     * Check if user has access to a folder (owns or has share).
     *
     * @param userId User ID
     * @param folderId Folder ID
     * @return Folder if user has access
     * @throws ForbiddenException if no access
     */
    public Folder checkFolderAccess(@NonNull Long userId, @NonNull Long folderId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(folderId, "folderId cannot be null");

        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder", folderId));

        // Check ownership
        if (folder.getUser().getId().equals(userId)) {
            return folder;
        }

        // Check if shared
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        boolean hasShare = folderShareRepository.existsByFolderAndSharedWithUser(folder, user);

        if (!hasShare) {
            throw new ForbiddenException("You don't have access to this folder");
        }

        return folder;
    }

    /**
     * Delete physical folder from filesystem.
     */
    private void deletePhysicalFolder(@NonNull Long userId, @NonNull Long folderId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(folderId, "folderId cannot be null");

        try {
            // Get absolute path - running from java-backend/, so go up one level to project root
            Path currentDir = Paths.get("").toAbsolutePath();
            Path projectRoot = currentDir.getFileName().toString().equals("java-backend")
                ? currentDir.getParent()
                : currentDir;
            Path imagesRoot = projectRoot.resolve("images");

            Path folderPath = imagesRoot.resolve(userId.toString()).resolve(folderId.toString());
            if (Files.exists(folderPath)) {
                try (Stream<Path> paths = Files.walk(folderPath)) {
                    paths.sorted(Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                }
                logger.info("Deleted physical folder: {}", folderPath);
            }
        } catch (IOException e) {
            logger.error("Failed to delete physical folder", e);
            // Don't throw - best effort cleanup
        }
    }
}
