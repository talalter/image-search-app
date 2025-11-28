package com.imagesearch.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to generate embeddings for images
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbedRequest {

    /**
     * User ID for access control
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * Folder ID for organizing embeddings
     */
    @JsonProperty("folder_id")
    private Long folderId;

    /**
     * List of images to embed
     */
    @JsonProperty("images")
    private List<ImageInfo> images;

    /**
     * Image information for embedding
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageInfo {
        @JsonProperty("image_id")
        private Long imageId;

        @JsonProperty("file_path")
        private String filePath;
    }
}
