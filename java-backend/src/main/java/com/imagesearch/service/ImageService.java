package com.imagesearch.service;

import com.imagesearch.client.SearchClient;
import com.imagesearch.client.dto.EmbedImagesRequest;
import com.imagesearch.exception.BadRequestException;
import com.imagesearch.model.dto.response.UploadResponse;
import com.imagesearch.model.entity.Folder;
import com.imagesearch.model.entity.Image;
import com.imagesearch.model.entity.User;
import com.imagesearch.repository.ImageRepository;
import com.imagesearch.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Service for image upload and management.
 *
 * Handles:
 * - Image file upload to filesystem
 * - Database record creation
 * - Triggering embedding generation in Python service
 */
@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
    private static final int UPLOAD_THREAD_POOL_SIZE = 4;
    private static final int BATCH_SIZE = 32;  // Process 32 files per batch (file upload & embedding)

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final FolderService folderService;
    private final SearchClient searchClient;
    private final FailedRequestService failedRequestService;
    private final ExecutorService uploadExecutor;

    public ImageService(
            ImageRepository imageRepository,
            UserRepository userRepository,
            FolderService folderService,
            SearchClient searchClient,
            FailedRequestService failedRequestService) {
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.folderService = folderService;
        this.searchClient = searchClient;
        this.failedRequestService = failedRequestService;
        this.uploadExecutor = Executors.newFixedThreadPool(
            UPLOAD_THREAD_POOL_SIZE,
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("image-upload-" + counter++);
                    return thread;
                }
            }
        );
    }

    /**
     * Result of a single file upload attempt.
     */
    private static class FileUploadResult {
        final String filename;
        final String filepath;
        final boolean success;
        final String errorMessage;

        static FileUploadResult success(String filename, String filepath) {
            return new FileUploadResult(filename, filepath, true, null);
        }

        static FileUploadResult failure(String filename, String errorMessage) {
            return new FileUploadResult(filename, null, false, errorMessage);
        }

        private FileUploadResult(String filename, String filepath, boolean success, String errorMessage) {
            this.filename = filename;
            this.filepath = filepath;
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Upload multiple images to a folder with parallel file I/O in batches.
     *
     * Process (for batches of 32 files):
     * 1. Validate inputs and create/get folder
     * 2. Process files in batches of 32:
     *    - Save batch to filesystem IN PARALLEL (4 threads)
     *    - Create database records for successful uploads
     *    - Trigger background embedding generation
     * 3. Return response after all batches complete
     *
     * Note: No @Transactional - each batch commits independently.
     * This means if batch 3 fails, batches 1-2 are already committed.
     *
     * @param userId User ID
     * @param folderName Folder name
     * @param files Image files
     * @return Upload response with success/failure details
     */
    public UploadResponse uploadImages(Long userId, String folderName, List<MultipartFile> files) {
        validateUploadInputs(userId, folderName, files);

        logger.info("Uploading {} images to folder '{}' for user {} (batch size: {})",
                   files.size(), folderName, userId, BATCH_SIZE);

        // Step 1: Get or create folder
        Folder folder = folderService.createOrGetFolder(userId, folderName);
        User user = getUserById(userId);

        // Step 2: Process files in batches
        int totalFiles = files.size();
        int totalBatches = (int) Math.ceil((double) totalFiles / BATCH_SIZE);

        List<FileUploadResult> allUploadResults = new ArrayList<>();
        List<EmbedImagesRequest.ImageInfo> allImagesToEmbed = new ArrayList<>();

        for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
            int startIdx = batchNum * BATCH_SIZE;
            int endIdx = Math.min(startIdx + BATCH_SIZE, totalFiles);
            List<MultipartFile> batch = files.subList(startIdx, endIdx);

            logger.info("Processing upload batch {}/{}: {} files (total progress: {}/{})",
                       batchNum + 1, totalBatches, batch.size(), endIdx, totalFiles);

            // Step 2a: Save batch to filesystem in parallel
            List<FileUploadResult> batchResults = saveFilesInParallel(userId, folder.getId(), batch);
            allUploadResults.addAll(batchResults);

            // Step 2b: Create database records for successful uploads in this batch
            List<EmbedImagesRequest.ImageInfo> batchImagesToEmbed = createDatabaseRecords(user, folder, batchResults);
            allImagesToEmbed.addAll(batchImagesToEmbed);

            logger.info("Batch {}/{} completed: {} files processed", batchNum + 1, totalBatches, batch.size());
        }

        // Step 3: Trigger background embedding generation for all successfully uploaded images
        // Note: Embedding runs asynchronously - failures here don't affect the upload result
        if (!allImagesToEmbed.isEmpty()) {
            try {
                embedImagesInBatches(userId, folder.getId(), allImagesToEmbed);
                logger.info("Started background embedding for {} images", allImagesToEmbed.size());
            } catch (Exception e) {
                // Don't fail the entire upload if embedding service can't be reached
                // Images are already saved to disk and database - embeddings can be retried later
                logger.error("Failed to start embedding process for user {} folder {}: {}",
                           userId, folder.getId(), e.getMessage());
            }
        }

        // Step 4: Build response with success/failure details
        return buildUploadResponse(folder.getId(), allUploadResults);
    }

    /**
     * Validate upload inputs.
     */
    private void validateUploadInputs(Long userId, String folderName, List<MultipartFile> files) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (folderName == null) {
            throw new IllegalArgumentException("folderName cannot be null");
        }
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("No files provided");
        }
    }

    /**
     * Get user by ID or throw exception.
     */
    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
    }

    /**
     * Save multiple files to filesystem in parallel using thread pool.
     * Returns results for both successful and failed uploads.
     */
    private List<FileUploadResult> saveFilesInParallel(Long userId, Long folderId, List<MultipartFile> files) {
        List<Future<FileUploadResult>> futures = new ArrayList<>();

        // Submit all file save tasks to thread pool
        for (MultipartFile file : files) {
            Future<FileUploadResult> future = uploadExecutor.submit(() -> saveFileTask(userId, folderId, file));
            futures.add(future);
        }

        // Collect results
        List<FileUploadResult> results = new ArrayList<>();
        for (Future<FileUploadResult> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("File upload interrupted", e);
                results.add(FileUploadResult.failure("unknown", "Upload interrupted"));
            } catch (ExecutionException e) {
                logger.error("File upload execution error", e);
                results.add(FileUploadResult.failure("unknown", "Upload execution failed: " + e.getMessage()));
            }
        }

        return results;
    }

    /**
     * Task for saving a single file (runs in thread pool).
     */
    private FileUploadResult saveFileTask(Long userId, Long folderId, MultipartFile file) {
        String filename = file.getOriginalFilename();

        try {
            // Validate file type
            if (filename == null || !isValidImageFile(filename)) {
                return FileUploadResult.failure(filename, "Invalid file type");
            }

            // Save file to filesystem
            String filepath = saveImageFile(userId, folderId, file);

            logger.debug("Thread {} saved file: {}", Thread.currentThread().getName(), filename);
            return FileUploadResult.success(filename, filepath);

        } catch (IOException e) {
            logger.error("Failed to save file {}: {}", filename, e.getMessage());
            return FileUploadResult.failure(filename, "I/O error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error saving file {}: {}", filename, e.getMessage());
            return FileUploadResult.failure(filename, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Create database records for successfully uploaded files.
     * Returns list of images to embed.
     */
    private List<EmbedImagesRequest.ImageInfo> createDatabaseRecords(
            User user, Folder folder, List<FileUploadResult> uploadResults) {

        List<EmbedImagesRequest.ImageInfo> imagesToEmbed = new ArrayList<>();

        for (FileUploadResult result : uploadResults) {
            if (result.success) {
                try {
                    // Create database record
                    Image image = new Image();
                    image.setUser(user);
                    image.setFolder(folder);
                    image.setFilepath(result.filepath);
                    Image savedImage = imageRepository.save(image);

                    // Add to embedding list
                    imagesToEmbed.add(new EmbedImagesRequest.ImageInfo(
                        savedImage.getId(),
                        result.filepath
                    ));

                } catch (Exception e) {
                    logger.error("Failed to create database record for {}: {}", result.filename, e.getMessage());
                }
            }
        }

        return imagesToEmbed;
    }

    /**
     * Build upload response with success/failure details.
     */
    private UploadResponse buildUploadResponse(Long folderId, List<FileUploadResult> uploadResults) {
        int successCount = 0;
        int failureCount = 0;
        List<String> failedFiles = new ArrayList<>();

        for (FileUploadResult result : uploadResults) {
            if (result.success) {
                successCount++;
            } else {
                failureCount++;
                failedFiles.add(result.filename + ": " + result.errorMessage);
            }
        }

        String message;
        if (failureCount == 0) {
            message = "Successfully uploaded " + successCount + " images. Processing embeddings in background...";
            logger.info("Upload completed: {} images uploaded successfully", successCount);
        } else {
            message = "Uploaded " + successCount + " images successfully, " + failureCount + " failed. " +
                     "Failed files: " + String.join(", ", failedFiles);
            logger.warn("Upload completed with errors: {} success, {} failed", successCount, failureCount);
        }

        return new UploadResponse(message, folderId, successCount);
    }

    /**
     * Save image file to filesystem.
     *
     * @param userId User ID
     * @param folderId Folder ID
     * @param file Image file
     * @return Saved file path
     */
    @SuppressWarnings("null")
    private String saveImageFile(Long userId, Long folderId, MultipartFile file) throws IOException {
        if (userId == null || folderId == null) {
            throw new IllegalArgumentException("userId and folderId cannot be null");
        }
        // Get absolute path - running from java-backend/, so go up one level to project root
        Path currentDir = Paths.get("").toAbsolutePath();
        Path projectRoot = currentDir.getFileName().toString().equals("java-backend")
            ? currentDir.getParent()
            : currentDir;
        Path imagesRoot = projectRoot.resolve("data").resolve("uploads");

        // Create directory structure: {project-root}/data/uploads/{userId}/{folderId}/
        Path directoryPath = imagesRoot.resolve(userId.toString()).resolve(folderId.toString());
        Files.createDirectories(directoryPath);

        // Get filename without directory path (handles "views/israel.jpeg" -> "israel.jpeg")
        String originalFilename = file.getOriginalFilename();
        String filename = originalFilename;
        if (originalFilename != null) {
            // Extract just the filename, removing any directory path
            int lastSeparator = Math.max(
                originalFilename.lastIndexOf('/'),
                originalFilename.lastIndexOf('\\')
            );
            if (lastSeparator >= 0) {
                filename = originalFilename.substring(lastSeparator + 1);
            }
        }

        // Save file
        Path filePath = directoryPath.resolve(filename);
        file.transferTo(filePath.toFile());

        logger.info("Saved image file to: {}", filePath.toAbsolutePath());

        // Return relative path for database storage (matches URL mapping in StaticResourceConfig)
        return "images/" + userId + "/" + folderId + "/" + filename;
    }

    /**
     * Validate image file extension.
     */
    private boolean isValidImageFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    /**
     * Get image by ID.
     */
    public Image getImageById(Long imageId) {
        if (imageId == null) {
            return null;
        }
        return imageRepository.findById(imageId)
                .orElse(null);
    }

    /**
     * Batch lookup images by IDs - performs single database query.
     *
     * This is much faster than calling getImageById() in a loop,
     * especially for search results with many images.
     *
     * @param imageIds Set of image IDs
     * @return Map of imageId -> Image (only includes images that exist)
     */
    public Map<Long, Image> getImagesByIds(Set<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return Map.of();
        }

        List<Image> images = imageRepository.findAllByIdIn(imageIds);
        return images.stream()
                .collect(Collectors.toMap(Image::getId, img -> img));
    }

    /**
     * Process image embeddings in batches asynchronously.
     * This prevents timeouts when uploading large numbers of images (100s-1000s).
     *
     * Strategy:
     * - Splits images into batches of 32
     * - Each batch is sent to search service sequentially
     * - Runs in background thread so upload API returns immediately
     * - Users can search images as batches complete
     *
     * @param userId User ID
     * @param folderId Folder ID
     * @param imagesToEmbed List of all images to embed
     *
     * Process image embeddings asynchronously in background thread.
     * This allows the upload request to return immediately while embeddings
     * are generated in the background.
     */
    @Async("embeddingExecutor")
    public void embedImagesInBatches(Long userId, Long folderId, List<EmbedImagesRequest.ImageInfo> imagesToEmbed) {
        int totalImages = imagesToEmbed.size();
        int totalBatches = (int) Math.ceil((double) totalImages / BATCH_SIZE);

        logger.info("[ASYNC-THREAD] Starting batch embedding: {} images in {} batches (batch size: {}) for user {} folder {}",
                   totalImages, totalBatches, BATCH_SIZE, userId, folderId);

        for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
            int startIdx = batchNum * BATCH_SIZE;
            int endIdx = Math.min(startIdx + BATCH_SIZE, totalImages);
            List<EmbedImagesRequest.ImageInfo> batch = imagesToEmbed.subList(startIdx, endIdx);

            logger.info("[ASYNC-THREAD] Processing embedding batch {}/{}: {} images (total progress: {}/{})",
                       batchNum + 1, totalBatches, batch.size(), endIdx, totalImages);

            try {
                EmbedImagesRequest request = new EmbedImagesRequest(userId, folderId, batch);
                searchClient.embedImages(request);

                logger.info("[ASYNC-THREAD] Embedding batch {}/{} completed successfully", batchNum + 1, totalBatches);

                // Small delay between batches to avoid overwhelming the search service
                if (batchNum < totalBatches - 1) {
                    Thread.sleep(1000); // 1 second between batches
                }

            } catch (Exception e) {
                logger.error("[ASYNC-THREAD] Failed to embed batch {}/{} for user {} folder {}: {}",
                           batchNum + 1, totalBatches, userId, folderId, e.getMessage());

                // Add failed batch to retry queue
                // Scheduled job will retry every 10 minutes until success or max attempts reached
                failedRequestService.recordFailedEmbed(userId, folderId, batch, e.getMessage());
                logger.info("[ASYNC-THREAD] Added batch {}/{} to retry queue ({} images)",
                           batchNum + 1, totalBatches, batch.size());

                // Continue with next batch even if one fails
            }
        }

        logger.info("[ASYNC-THREAD] Completed batch embedding for user {} folder {}: {}/{} images processed",
                   userId, folderId, totalImages, totalImages);
    }
}
