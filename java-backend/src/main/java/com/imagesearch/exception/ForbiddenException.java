package com.imagesearch.exception;

/**
 * Exception thrown when a user tries to access a resource they don't own.
 * Results in HTTP 403 status.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
