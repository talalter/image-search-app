package com.imagesearch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagesearch.client.SearchClient;
import com.imagesearch.client.dto.EmbedImagesRequest;
import com.imagesearch.model.entity.FailedEmbedRequest;
import com.imagesearch.model.entity.FailedIndexDeletion;
import com.imagesearch.repository.FailedEmbedRequestRepository;
import com.imagesearch.repository.FailedIndexDeletionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service to manage retry logic for failed embedding and index deletion requests.
 * Runs scheduled jobs to retry pending requests when Python search service recovers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FailedRequestService {

    private final FailedEmbedRequestRepository failedEmbedRequestRepository;
    private final FailedIndexDeletionRepository failedIndexDeletionRepository;
    private final SearchClient searchClient;
    private final ObjectMapper objectMapper;

    @Value("${search-service.retry.max-attempts:5}")
    private int maxRetryAttempts;

    @Value("${search-service.retry.cleanup-after-days:7}")
    private int cleanupAfterDays;

    /**
     * Save failed embedding request to retry queue
     */
    @Transactional
    public void recordFailedEmbed(Long userId, Long folderId,
                                  List<EmbedImagesRequest.ImageInfo> images,
                                  String errorMessage) {
        try {
            String imagesJson = objectMapper.writeValueAsString(images);

            FailedEmbedRequest failedRequest = new FailedEmbedRequest();
            failedRequest.setUserId(userId);
            failedRequest.setFolderId(folderId);
            failedRequest.setImagesJson(imagesJson);
            failedRequest.setImageCount(images.size());
            failedRequest.setErrorMessage(errorMessage);
            failedRequest.setStatus("PENDING");

            failedEmbedRequestRepository.save(failedRequest);
            log.info("Recorded failed embed request for folder {} ({} images)", folderId, images.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize images for failed embed request: {}", e.getMessage(), e);
        }
    }

    /**
     * Save failed index deletion to retry queue
     */
    @Transactional
    public void recordFailedIndexDeletion(Long userId, Long folderId, String errorMessage) {
        FailedIndexDeletion failedDeletion = new FailedIndexDeletion();
        failedDeletion.setUserId(userId);
        failedDeletion.setFolderId(folderId);
        failedDeletion.setErrorMessage(errorMessage);
        failedDeletion.setStatus("PENDING");

        failedIndexDeletionRepository.save(failedDeletion);
        log.info("Recorded failed index deletion for folder {} (user {})", folderId, userId);
    }

    /**
     * Scheduled job to retry failed embedding requests
     * Runs every 10 minutes
     */
    @SuppressWarnings("null")
    @Scheduled(cron = "${search-service.retry.embed-cron:0 */10 * * * *}")
    @Transactional
    public void retryFailedEmbeddings() {
        List<FailedEmbedRequest> pendingRequests =
                failedEmbedRequestRepository.findPendingRequestsForRetry(maxRetryAttempts);

        if (pendingRequests.isEmpty()) {
            return;
        }

        log.info("Starting retry job for {} failed embedding requests", pendingRequests.size());
        int successCount = 0;
        int failureCount = 0;

        for (FailedEmbedRequest request : pendingRequests) {
            try {
                // Mark as in progress to prevent concurrent retries
                request.setStatus("IN_PROGRESS");
                failedEmbedRequestRepository.save(request);

                // Deserialize images
                List<EmbedImagesRequest.ImageInfo> images = objectMapper.readValue(
                        request.getImagesJson(),
                        new TypeReference<List<EmbedImagesRequest.ImageInfo>>() {});

                // Build request and retry
                EmbedImagesRequest embedRequest = new EmbedImagesRequest();
                embedRequest.setUserId(request.getUserId());
                embedRequest.setFolderId(request.getFolderId());
                embedRequest.setImages(images);

                searchClient.embedImages(embedRequest);

                // Success - mark as succeeded
                request.setStatus("SUCCEEDED");
                request.setLastRetryAt(LocalDateTime.now());
                successCount++;

                log.info("Successfully retried embedding for folder {} ({} images)",
                        request.getFolderId(), images.size());

            } catch (Exception e) {
                // Failure - increment retry count and update error
                request.setRetryCount(request.getRetryCount() + 1);
                request.setLastRetryAt(LocalDateTime.now());
                request.setErrorMessage(e.getMessage());

                if (request.getRetryCount() >= maxRetryAttempts) {
                    request.setStatus("FAILED");
                    log.error("Failed embedding request exhausted retries (folder {}): {}",
                            request.getFolderId(), e.getMessage());
                } else {
                    request.setStatus("PENDING");
                    log.warn("Retry failed for embedding request (folder {}, attempt {}/{}): {}",
                            request.getFolderId(), request.getRetryCount(), maxRetryAttempts, e.getMessage());
                }
                failureCount++;
            } finally {
                failedEmbedRequestRepository.save(request);
            }
        }

        log.info("Completed embedding retry job: {} succeeded, {} failed", successCount, failureCount);
    }


    /**
     * Scheduled job to retry failed index deletions
     * Runs every 15 minutes
     */
    @Scheduled(cron = "${search-service.retry.index-deletion-cron:0 */15 * * * *}")
    @Transactional
    public void retryFailedIndexDeletions() {
        List<FailedIndexDeletion> pendingDeletions =
                failedIndexDeletionRepository.findPendingRequestsForRetry(maxRetryAttempts);

        if (pendingDeletions.isEmpty()) {
            return;
        }

        log.info("Starting retry job for {} failed index deletions", pendingDeletions.size());
        int successCount = 0;
        int failureCount = 0;

        for (FailedIndexDeletion deletion : pendingDeletions) {
            try {
                // Mark as in progress
                deletion.setStatus("IN_PROGRESS");
                failedIndexDeletionRepository.save(deletion);

                // Retry deletion
                searchClient.deleteIndex(deletion.getUserId(), deletion.getFolderId());

                // Success - mark as succeeded
                deletion.setStatus("SUCCEEDED");
                deletion.setLastRetryAt(LocalDateTime.now());
                successCount++;

                log.info("Successfully retried index deletion for folder {} (user {})",
                        deletion.getFolderId(), deletion.getUserId());

            } catch (Exception e) {
                // Failure - increment retry count
                deletion.setRetryCount(deletion.getRetryCount() + 1);
                deletion.setLastRetryAt(LocalDateTime.now());
                deletion.setErrorMessage(e.getMessage());

                if (deletion.getRetryCount() >= maxRetryAttempts) {
                    deletion.setStatus("FAILED");
                    log.error("Failed index deletion exhausted retries (folder {}, user {}): {}",
                            deletion.getFolderId(), deletion.getUserId(), e.getMessage());
                } else {
                    deletion.setStatus("PENDING");
                    log.warn("Retry failed for index deletion (folder {}, attempt {}/{}): {}",
                            deletion.getFolderId(), deletion.getRetryCount(), maxRetryAttempts, e.getMessage());
                }
                failureCount++;
            } finally {
                failedIndexDeletionRepository.save(deletion);
            }
        }

        log.info("Completed index deletion retry job: {} succeeded, {} failed", successCount, failureCount);
    }

    /**
     * Cleanup old succeeded/failed requests
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "${search-service.retry.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupOldRequests() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cleanupAfterDays);

        List<FailedEmbedRequest> oldEmbeds =
                failedEmbedRequestRepository.findByStatusAndCreatedAtBefore("SUCCEEDED", cutoffDate);
        List<FailedIndexDeletion> oldDeletions =
                failedIndexDeletionRepository.findByStatusAndCreatedAtBefore("SUCCEEDED", cutoffDate);

        failedEmbedRequestRepository.deleteAll(oldEmbeds);
        failedIndexDeletionRepository.deleteAll(oldDeletions);

        log.info("Cleaned up {} old embed requests and {} old index deletions",
                oldEmbeds.size(), oldDeletions.size());
    }

    /**
     * Get statistics about pending requests
     */
    public RetryQueueStats getStats() {
        long pendingEmbeds = failedEmbedRequestRepository.findByStatusOrderByCreatedAtAsc("PENDING").size();
        long pendingDeletions = failedIndexDeletionRepository.findByStatusOrderByCreatedAtAsc("PENDING").size();
        long failedEmbeds = failedEmbedRequestRepository.findByStatusOrderByCreatedAtAsc("FAILED").size();
        long failedDeletions = failedIndexDeletionRepository.findByStatusOrderByCreatedAtAsc("FAILED").size();

        return new RetryQueueStats(pendingEmbeds, pendingDeletions, failedEmbeds, failedDeletions);
    }

    public record RetryQueueStats(
            long pendingEmbeds,
            long pendingIndexDeletions,
            long failedEmbeds,
            long failedIndexDeletions
    ) {}
}
