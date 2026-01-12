package com.imagesearch.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Response DTO for search history.
 * Returns list of query strings ordered by most recent first.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryResponse {
    private List<String> queries; // Array of query strings

    /**
     * Single query item response (for add operation).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddResponse {
        private String message;
        private List<String> queries; // Updated full list
    }
}
