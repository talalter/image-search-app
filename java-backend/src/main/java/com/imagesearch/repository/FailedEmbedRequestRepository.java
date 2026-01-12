package com.imagesearch.repository;

import com.imagesearch.model.entity.FailedEmbedRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FailedEmbedRequestRepository extends JpaRepository<FailedEmbedRequest, Long> {

    /**
     * Find all pending requests that need retry
     */
    List<FailedEmbedRequest> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * Find pending requests with retry count below threshold
     */
    @Query("SELECT f FROM FailedEmbedRequest f WHERE f.status = 'PENDING' AND f.retryCount < :maxRetries ORDER BY f.createdAt ASC")
    List<FailedEmbedRequest> findPendingRequestsForRetry(int maxRetries);

    /**
     * Find old succeeded requests for cleanup
     */
    List<FailedEmbedRequest> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);

    /**
     * Count pending requests by folder (for monitoring)
     */
    long countByFolderIdAndStatus(Long folderId, String status);

    /**
     * Find all pending requests for a specific folder
     */
    List<FailedEmbedRequest> findByFolderIdAndStatus(Long folderId, String status);
}
