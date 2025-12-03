package com.imagesearch.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for image upload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String message;
    private Long folderId;
    private Integer uploadedCount;
    private Long jobId; // RabbitMQ job ID for tracking embedding progress

    public UploadResponse(String message, Long folderId, Integer uploadedCount) {
        this.message = message;
        this.folderId = folderId;
        this.uploadedCount = uploadedCount;
    }
}
