package com.imagesearch.search.exception;

/**
 * Exception thrown when embedding generation fails.
 *
 * Best Practice: Separate exceptions for different failure scenarios
 * to enable fine-grained error handling and monitoring.
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
