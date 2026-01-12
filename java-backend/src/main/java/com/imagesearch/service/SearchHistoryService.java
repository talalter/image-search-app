package com.imagesearch.service;

import com.imagesearch.model.entity.SearchHistory;
import com.imagesearch.model.entity.User;
import com.imagesearch.repository.SearchHistoryRepository;
import com.imagesearch.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing user search history with LRU behavior.
 *
 * Key features:
 * - Global history (not folder-specific)
 * - Configurable max entries per user (from application.yml or env var)
 * - Duplicate queries move to top (update timestamp)
 * - Efficient batch cleanup
 */
@Service
public class SearchHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(SearchHistoryService.class);
    private static final int MAX_QUERY_LENGTH = 500;

    @Value("${search-history.max-entries:50}")
    private int maxHistorySize;

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    public SearchHistoryService(
            SearchHistoryRepository searchHistoryRepository,
            UserRepository userRepository) {
        this.searchHistoryRepository = searchHistoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get search history for a user.
     *
     * @param userId User ID
     * @return List of query strings ordered by most recent first (up to maxHistorySize)
     */
    @Transactional(readOnly = true)
    public List<String> getHistory(Long userId) {
        logger.debug("Fetching search history for user {}", userId);

        List<SearchHistory> history = searchHistoryRepository
            .findByUserIdOrderBySearchedAtDesc(userId);

        // Limit to maxHistorySize
        List<String> queries = history.stream()
            .limit(maxHistorySize)
            .map(SearchHistory::getQueryText)
            .collect(Collectors.toList());

        logger.debug("Found {} history entries for user {}", queries.size(), userId);
        return queries;
    }

    /**
     * Add query to search history with LRU behavior.
     *
     * Algorithm:
     * 1. Validate and trim query
     * 2. Check if query already exists for user
     * 3. If exists: update timestamp (move to top)
     * 4. If not exists: create new entry
     * 5. If total > maxHistorySize: delete oldest entries
     * 6. Return updated history list
     *
     * @param userId User ID
     * @param query Search query
     * @return Updated list of queries
     * @throws IllegalArgumentException if query is invalid
     */
    @Transactional
    public List<String> addToHistory(Long userId, String query) {
        // Validate query
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }

        String trimmedQuery = query.trim();
        if (trimmedQuery.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Query too long (max %d characters)", MAX_QUERY_LENGTH)
            );
        }

        logger.debug("Adding query '{}' to history for user {} (max size: {})",
                    trimmedQuery, userId, maxHistorySize);

        // Get user entity (for FK relationship)
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if query already exists
        Optional<SearchHistory> existingEntry = searchHistoryRepository
            .findByUserIdAndQueryText(userId, trimmedQuery);

        if (existingEntry.isPresent()) {
            // LRU: Update timestamp to move to top
            SearchHistory entry = existingEntry.get();
            entry.updateTimestamp();
            searchHistoryRepository.save(entry);
            logger.debug("Updated timestamp for existing query '{}'", trimmedQuery);
        } else {
            // Create new entry
            SearchHistory newEntry = new SearchHistory(user, trimmedQuery);
            searchHistoryRepository.save(newEntry);
            logger.debug("Created new history entry for query '{}'", trimmedQuery);

            // Cleanup: If > maxHistorySize entries, delete oldest
            long count = searchHistoryRepository.countByUserId(userId);
            if (count > maxHistorySize) {
                int deleted = searchHistoryRepository.deleteOldestBeyondLimit(
                    userId, maxHistorySize
                );
                logger.debug("Deleted {} oldest entries for user {}", deleted, userId);
            }
        }

        // Return updated history
        return getHistory(userId);
    }

    /**
     * Clear all search history for a user.
     *
     * @param userId User ID
     */
    @Transactional
    public void clearHistory(Long userId) {
        logger.info("Clearing search history for user {}", userId);
        searchHistoryRepository.deleteAllByUserId(userId);
    }
}
