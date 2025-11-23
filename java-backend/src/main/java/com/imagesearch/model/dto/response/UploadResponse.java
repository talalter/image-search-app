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
}
