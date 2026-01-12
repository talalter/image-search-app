package com.imagesearch.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Search history entity for storing user search queries.
 *
 * Implements LRU (Least Recently Used) behavior:
 * - Duplicate queries update timestamp (move to top)
 * - Each user limited to configurable max entries (default 50)
 */
@Entity
@Table(name = "search_history",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_user_query",
                           columnNames = {"user_id", "query_text"})
       },
       indexes = {
           @Index(name = "idx_search_history_user_time",
                  columnList = "user_id, searched_at DESC")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "query_text", nullable = false, length = 500)
    private String queryText;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    /**
     * Constructor for creating new search history entry.
     */
    public SearchHistory(User user, String queryText) {
        this.user = user;
        this.queryText = queryText;
        this.searchedAt = LocalDateTime.now();
    }

    /**
     * Update timestamp to current time (for LRU move-to-top).
     */
    public void updateTimestamp() {
        this.searchedAt = LocalDateTime.now();
    }
}
