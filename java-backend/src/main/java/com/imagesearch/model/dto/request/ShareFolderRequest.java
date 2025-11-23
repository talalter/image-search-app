package com.imagesearch.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sharing a folder with another user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareFolderRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotNull(message = "Folder ID is required")
    private Long folderId;

    @NotBlank(message = "Target username is required")
    private String targetUsername;

    private String permission = "view";
}
