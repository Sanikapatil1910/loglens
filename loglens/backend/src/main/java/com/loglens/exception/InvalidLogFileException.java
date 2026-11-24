package com.loglens.exception;

/**
 * Thrown when an upload or submitted text is empty, binary, of an unsupported type, or otherwise
 * unusable (HTTP 400). Messages must be safe to show to users and must never echo log content.
 */
public class InvalidLogFileException extends RuntimeException {

    public InvalidLogFileException(String message) {
        super(message);
    }
}
