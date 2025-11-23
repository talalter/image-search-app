package com.imagesearch.exception;

/**
 * Exception thrown when authentication fails.
 * Results in HTTP 401 status.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
