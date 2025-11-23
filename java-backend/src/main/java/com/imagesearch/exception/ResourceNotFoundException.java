package com.imagesearch.exception;

/**
 * Exception thrown when a requested resource is not found.
 * Results in HTTP 404 status.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Long id) {
        super(String.format("%s not found with id: %d", resource, id));
    }
}
