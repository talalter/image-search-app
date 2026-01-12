package com.imagesearch.controller;

import com.imagesearch.service.FailedRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST endpoints for monitoring the failed request retry queue.
 * Provides visibility into pending/failed embeddings and index deletions.
 */
@RestController
@RequestMapping("/api/admin/retry-queue")
@RequiredArgsConstructor
public class FailedRequestController {

    private final FailedRequestService failedRequestService;

    /**
     * Get statistics about the retry queue.
     *
     * Example response:
     * {
     *   "pending_embeds": 12,
     *   "pending_index_deletions": 3,
     *   "failed_embeds": 0,
     *   "failed_index_deletions": 1
     * }
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getRetryQueueStats() {
        FailedRequestService.RetryQueueStats stats = failedRequestService.getStats();

        Map<String, Long> response = Map.of(
            "pending_embeds", stats.pendingEmbeds(),
            "pending_index_deletions", stats.pendingIndexDeletions(),
            "failed_embeds", stats.failedEmbeds(),
            "failed_index_deletions", stats.failedIndexDeletions()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Manually trigger retry job for failed embeddings.
     * Useful for testing or immediate retry after service recovery.
     */
    @GetMapping("/trigger-embed-retry")
    public ResponseEntity<Map<String, String>> triggerEmbedRetry() {
        failedRequestService.retryFailedEmbeddings();
        return ResponseEntity.ok(Map.of(
            "message", "Embedding retry job triggered successfully"
        ));
    }

    /**
     * Manually trigger retry job for failed index deletions.
     * Useful for testing or immediate retry after service recovery.
     */
    @GetMapping("/trigger-index-deletion-retry")
    public ResponseEntity<Map<String, String>> triggerIndexDeletionRetry() {
        failedRequestService.retryFailedIndexDeletions();
        return ResponseEntity.ok(Map.of(
            "message", "Index deletion retry job triggered successfully"
        ));
    }
}
