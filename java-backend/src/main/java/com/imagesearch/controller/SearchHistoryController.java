package com.imagesearch.controller;

import com.imagesearch.model.dto.response.MessageResponse;
import com.imagesearch.model.dto.response.SearchHistoryResponse;
import com.imagesearch.service.SearchHistoryService;
import com.imagesearch.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for search history management.
 *
 * Endpoints:
 * - GET /api/search/history?token=xxx - Get user's search history
 * - POST /api/search/history - Add query to history
 * - DELETE /api/search/history?token=xxx - Clear all history
 */
@RestController
@RequestMapping("/api/search/history")
public class SearchHistoryController {

    private static final Logger logger = LoggerFactory.getLogger(SearchHistoryController.class);

    private final SearchHistoryService searchHistoryService;
    private final SessionService sessionService;

    public SearchHistoryController(
            SearchHistoryService searchHistoryService,
            SessionService sessionService) {
        this.searchHistoryService = searchHistoryService;
        this.sessionService = sessionService;
    }

    /**
     * Get search history for authenticated user.
     * GET /api/search/history?token=xxx
     *
     * @param token Session token
     * @return List of query strings ordered by most recent first
     */
    @GetMapping
    public ResponseEntity<SearchHistoryResponse> getHistory(@RequestParam("token") String token) {
        Long userId = sessionService.validateTokenAndGetUserId(token);
        logger.info("Fetching search history for user {}", userId);

        List<String> queries = searchHistoryService.getHistory(userId);
        return ResponseEntity.ok(new SearchHistoryResponse(queries));
    }

    /**
     * Add query to search history (LRU behavior).
     * POST /api/search/history
     * Body: { "token": "xxx", "query": "search text" }
     *
     * @param request Map containing token and query
     * @return Updated list of queries
     */
    @PostMapping
    public ResponseEntity<?> addToHistory(@RequestBody Map<String, String> request) {

        String token = request.get("token");
        String query = request.get("query");

        if (token == null || query == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Missing token or query"));
        }

        try {
            Long userId = sessionService.validateTokenAndGetUserId(token);
            logger.info("Adding query '{}' to history for user {}", query, userId);

            List<String> updatedQueries = searchHistoryService.addToHistory(userId, query);

            return ResponseEntity.ok(new SearchHistoryResponse.AddResponse(
                "Query added to history",
                updatedQueries
            ));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid query: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Clear all search history for user.
     * DELETE /api/search/history?token=xxx
     *
     * @param token Session token
     * @return Success message
     */
    @DeleteMapping
    public ResponseEntity<MessageResponse> clearHistory(@RequestParam("token") String token) {
        Long userId = sessionService.validateTokenAndGetUserId(token);
        logger.info("Clearing search history for user {}", userId);

        searchHistoryService.clearHistory(userId);

        return ResponseEntity.ok(new MessageResponse("Search history cleared"));
    }
}
