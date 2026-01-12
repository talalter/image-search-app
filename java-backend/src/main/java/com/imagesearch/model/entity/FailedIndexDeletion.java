package com.imagesearch.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity to track failed index deletion requests when Python search service is unavailable.
 * Allows retry mechanism to clean up orphaned FAISS index files.
 */
@Entity
@Table(name = "failed_index_deletions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FailedIndexDeletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /**
     * Status: PENDING, IN_PROGRESS, FAILED, SUCCEEDED
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (retryCount == null) {
            retryCount = 0;
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
