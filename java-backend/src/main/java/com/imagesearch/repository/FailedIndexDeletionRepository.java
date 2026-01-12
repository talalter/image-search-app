package com.imagesearch.repository;

import com.imagesearch.model.entity.FailedIndexDeletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FailedIndexDeletionRepository extends JpaRepository<FailedIndexDeletion, Long> {

    /**
     * Find all pending requests that need retry
     */
    List<FailedIndexDeletion> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * Find pending requests with retry count below threshold
     */
    @Query("SELECT f FROM FailedIndexDeletion f WHERE f.status = 'PENDING' AND f.retryCount < :maxRetries ORDER BY f.createdAt ASC")
    List<FailedIndexDeletion> findPendingRequestsForRetry(int maxRetries);

    /**
     * Find old succeeded requests for cleanup
     */
    List<FailedIndexDeletion> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);
}
