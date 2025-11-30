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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final FolderService folderService;
    private final SearchClient searchClient;

    public ImageService(
            ImageRepository imageRepository,
            UserRepository userRepository,
            FolderService folderService,
            SearchClient searchClient) {
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.folderService = folderService;
        this.searchClient = searchClient;
    }

    /**
     * Upload multiple images to a folder.
     *
     * Process:
     * 1. Create or get folder
     * 2. Save image files to filesystem
     * 3. Create database records
     * 4. Trigger background embedding generation (async call to Python service)
     *
     * @param userId User ID
     * @param folderName Folder name
     * @param files Image files
     * @return Upload response
     */
    @Transactional
    public UploadResponse uploadImages(Long userId, String folderName, List<MultipartFile> files) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (folderName == null) {
            throw new IllegalArgumentException("folderName cannot be null");
        }

        logger.info("Uploading {} images to folder '{}' for user {}", files.size(), folderName, userId);

        if (files == null || files.isEmpty()) {
            throw new BadRequestException("No files provided");
        }

        // Get or create folder
        Folder folder = folderService.createOrGetFolder(userId, folderName);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        List<EmbedImagesRequest.ImageInfo> imagesToEmbed = new ArrayList<>();
        int uploadedCount = 0;

        for (MultipartFile file : files) {
            // Validate file type
            String filename = file.getOriginalFilename();
            if (filename == null || !isValidImageFile(filename)) {
                throw new BadRequestException("Invalid file type: " + filename);
            }

            try {
                // Save file to filesystem
                String filepath = saveImageFile(userId, folder.getId(), file);

                // Create database record
                Image image = new Image();
                image.setUser(user);
                image.setFolder(folder);
                image.setFilepath(filepath);
                Image savedImage = imageRepository.save(image);

                // Add to embedding list
                imagesToEmbed.add(new EmbedImagesRequest.ImageInfo(
                    savedImage.getId(),
                    filepath
                ));

                uploadedCount++;

            } catch (IOException e) {
                logger.error("Failed to save image file: {}", filename, e);
                throw new BadRequestException("Failed to save image: " + filename);
            }
        }

        // Trigger background embedding generation in batches
        // Process images in batches to avoid timeout issues with large uploads
        if (!imagesToEmbed.isEmpty()) {
            embedImagesInBatches(userId, folder.getId(), imagesToEmbed);
        }

        logger.info("Uploaded {} images successfully", uploadedCount);
        return new UploadResponse(
            "Successfully uploaded " + uploadedCount + " images. Processing embeddings in background...",
            folder.getId(),
            uploadedCount
        );
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
        Path imagesRoot = projectRoot.resolve("data").resolve("uploads").resolve("images");

        // Create directory structure: {project-root}/data/uploads/images/{userId}/{folderId}/
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

        // Return relative path for database storage
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
     * Process image embeddings in batches asynchronously.
     * This prevents timeouts when uploading large numbers of images (100s-1000s).
     * 
     * Strategy:
     * - Splits images into batches of 50 (configurable)
     * - Each batch is sent to search service sequentially
     * - Runs in background thread so upload API returns immediately
     * - Users can search images as batches complete
     * 
     * @param userId User ID
     * @param folderId Folder ID
     * @param imagesToEmbed List of all images to embed
     */
    @Async
    public void embedImagesInBatches(Long userId, Long folderId, List<EmbedImagesRequest.ImageInfo> imagesToEmbed) {
        final int BATCH_SIZE = 50; // Process 50 images at a time
        int totalImages = imagesToEmbed.size();
        int totalBatches = (int) Math.ceil((double) totalImages / BATCH_SIZE);
        
        logger.info("Starting async batch embedding: {} images in {} batches for user {} folder {}", 
                   totalImages, totalBatches, userId, folderId);
        
        for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
            int startIdx = batchNum * BATCH_SIZE;
            int endIdx = Math.min(startIdx + BATCH_SIZE, totalImages);
            List<EmbedImagesRequest.ImageInfo> batch = imagesToEmbed.subList(startIdx, endIdx);
            
            logger.info("Processing batch {}/{}: {} images (total progress: {}/{})", 
                       batchNum + 1, totalBatches, batch.size(), endIdx, totalImages);
            
            try {
                EmbedImagesRequest request = new EmbedImagesRequest(userId, folderId, batch);
                searchClient.embedImages(request);
                
                logger.info("Batch {}/{} completed successfully", batchNum + 1, totalBatches);
                
                // Small delay between batches to avoid overwhelming the search service
                if (batchNum < totalBatches - 1) {
                    Thread.sleep(1000); // 1 second between batches
                }
                
            } catch (Exception e) {
                logger.error("Failed to embed batch {}/{} for user {} folder {}: {}", 
                           batchNum + 1, totalBatches, userId, folderId, e.getMessage());
                // Continue with next batch even if one fails
            }
        }
        
        logger.info("Completed async batch embedding for user {} folder {}: {}/{} images processed", 
                   userId, folderId, totalImages, totalImages);
    }
}
