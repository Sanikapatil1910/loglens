package com.loglens.exception;

/** Thrown when an upload or submitted text exceeds the configured size limit (HTTP 413). */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(String message) {
        super(message);
    }
}
