package com.imagesearch.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for semantic image search
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    /**
     * User ID for folder access control
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * Text search query (e.g., "sunset over mountains")
     */
    @JsonProperty("query")
    private String query;

    /**
     * List of folder IDs to search within
     */
    @JsonProperty("folder_ids")
    private List<Long> folderIds;

    /**
     * Number of top results to return (default: 5)
     */
    @JsonProperty("top_k")
    private Integer topK = 5;
}
