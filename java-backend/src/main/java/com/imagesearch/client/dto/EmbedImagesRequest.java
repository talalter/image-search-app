package com.imagesearch.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Request DTO for embedding images in Python service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbedImagesRequest {
    private Long userId;
    private Long folderId;
    private List<ImageInfo> images;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageInfo {
        private Long imageId;
        private String filePath;
    }
}
