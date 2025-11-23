package com.imagesearch.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Request DTO for deleting folders.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteFoldersRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotEmpty(message = "At least one folder ID is required")
    private List<Long> folderIds;
}
