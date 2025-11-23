package com.imagesearch.exception;

/**
 * Exception thrown when attempting to create a resource that already exists.
 * Results in HTTP 409 status.
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
