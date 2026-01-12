package com.imagesearch.exception;

/**
 * Exception thrown when the Python search service is unavailable.
 * This is used in circuit breaker fallback to indicate service degradation.
 */
public class SearchServiceUnavailableException extends RuntimeException {

    private final String query;
    private final int pendingRetries;

    public SearchServiceUnavailableException(String query, int pendingRetries) {
        super(String.format("Search service is temporarily unavailable. Your search for '%s' has been queued for retry. " +
                          "There are currently %d pending search requests.", query, pendingRetries));
        this.query = query;
        this.pendingRetries = pendingRetries;
    }

    public SearchServiceUnavailableException(String query, int pendingRetries, Throwable cause) {
        super(String.format("Search service is temporarily unavailable. Your search for '%s' has been queued for retry. " +
                          "There are currently %d pending search requests.", query, pendingRetries), cause);
        this.query = query;
        this.pendingRetries = pendingRetries;
    }

    public String getQuery() {
        return query;
    }

    public int getPendingRetries() {
        return pendingRetries;
    }
}
