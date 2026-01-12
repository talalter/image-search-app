package com.imagesearch.repository;

import com.imagesearch.model.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for search history operations.
 */
@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    /**
     * Find all history for a user, ordered by most recent first.
     * Returns top N entries (limit provided by caller).
     *
     * @param userId User ID
     * @return List of search history entries ordered by searched_at DESC
     */
    @Query("SELECT sh FROM SearchHistory sh " +
           "WHERE sh.user.id = :userId " +
           "ORDER BY sh.searchedAt DESC")
    List<SearchHistory> findByUserIdOrderBySearchedAtDesc(@Param("userId") Long userId);

    /**
     * Find specific history entry by user and query text.
     * Used to check if query already exists (for LRU update).
     *
     * @param userId User ID
     * @param queryText Query string
     * @return Optional containing the history entry if found
     */
    @Query("SELECT sh FROM SearchHistory sh " +
           "WHERE sh.user.id = :userId " +
           "AND sh.queryText = :queryText")
    Optional<SearchHistory> findByUserIdAndQueryText(
        @Param("userId") Long userId,
        @Param("queryText") String queryText
    );

    /**
     * Count history entries for a user.
     * Used to determine if cleanup is needed after insert.
     *
     * @param userId User ID
     * @return Number of history entries
     */
    @Query("SELECT COUNT(sh) FROM SearchHistory sh WHERE sh.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    /**
     * Delete oldest entries beyond limit for a user.
     * Efficient batch delete using subquery.
     *
     * @param userId User ID
     * @param limit Max entries to keep
     * @return Number of deleted entries
     */
    @Modifying
    @Query(value =
        "DELETE FROM search_history " +
        "WHERE id IN (" +
        "  SELECT id FROM search_history " +
        "  WHERE user_id = :userId " +
        "  ORDER BY searched_at DESC " +
        "  OFFSET :limit" +
        ")",
        nativeQuery = true)
    int deleteOldestBeyondLimit(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * Delete all history for a user.
     *
     * @param userId User ID
     */
    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
