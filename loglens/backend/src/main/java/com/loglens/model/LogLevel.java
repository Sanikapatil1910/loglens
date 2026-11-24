package com.loglens.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Normalised log levels. Vendor-specific names are mapped onto this set
 * (WARNING -> WARN, SEVERE -> ERROR). Anything else is UNKNOWN, never guessed.
 */
public enum LogLevel {
    FATAL(5), ERROR(4), WARN(3), INFO(2), DEBUG(1), TRACE(0), UNKNOWN(-1);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public boolean isError() {
        return this == ERROR || this == FATAL;
    }

    public static Optional<LogLevel> fromToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "FATAL" -> Optional.of(FATAL);
            case "ERROR", "SEVERE" -> Optional.of(ERROR);
            case "WARN", "WARNING" -> Optional.of(WARN);
            case "INFO" -> Optional.of(INFO);
            case "DEBUG" -> Optional.of(DEBUG);
            case "TRACE" -> Optional.of(TRACE);
            default -> Optional.empty();
        };
    }
}
