package com.imagesearch.model.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("token")
    private String token;

    @NotNull(message = "Folder ID is required")
    @JsonProperty("folderId")
    private Long folderId;

    @NotBlank(message = "Target username is required")
    @JsonProperty("targetUsername")
    private String targetUsername;

    @JsonProperty("permission")
    private String permission = "view";
}
