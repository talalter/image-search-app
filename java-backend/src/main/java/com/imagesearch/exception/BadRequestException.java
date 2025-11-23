package com.imagesearch.exception;

/**
 * Exception thrown when request validation fails.
 * Results in HTTP 400 status.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
