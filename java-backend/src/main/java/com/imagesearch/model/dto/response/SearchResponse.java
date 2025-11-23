package com.imagesearch.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Response DTO for image search results.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private List<ImageSearchResult> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageSearchResult {
        private String image;      // Image URL/path
        private Double similarity; // Similarity score
    }
}
