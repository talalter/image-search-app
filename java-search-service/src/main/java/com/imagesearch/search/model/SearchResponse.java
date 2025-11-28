package com.imagesearch.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response containing search results
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    /**
     * List of images with similarity scores
     */
    @JsonProperty("results")
    private List<ScoredImage> results;

    /**
     * Total number of results found
     */
    @JsonProperty("total")
    private int total;

    /**
     * Single scored image result
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoredImage {
        @JsonProperty("image_id")
        private Long imageId;

        @JsonProperty("score")
        private Float score;

        @JsonProperty("folder_id")
        private Long folderId;
    }
}
