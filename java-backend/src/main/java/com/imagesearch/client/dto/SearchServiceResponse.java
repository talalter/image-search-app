package com.imagesearch.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Response DTO from Python search microservice.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchServiceResponse {
    private List<SearchResult> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResult {
        private Long imageId;
        private Double score;
    }
}
