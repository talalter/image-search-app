package com.imagesearch.search.exception;

/**
 * Exception thrown when ONNX model fails to load.
 *
 * Best Practice: Custom exceptions for domain-specific error handling
 * with meaningful error messages for debugging.
 */
public class ModelLoadException extends RuntimeException {

    public ModelLoadException(String message) {
        super(message);
    }

    public ModelLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
