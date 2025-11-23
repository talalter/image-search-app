package com.imagesearch.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Response DTO for folder information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FolderResponse {
    private Long id;
    private String folderName;
    private Boolean isOwner;
    private Boolean isShared;
    private Long ownerId;
    private String ownerUsername;
    private String permission;
    private LocalDateTime sharedAt;
}
