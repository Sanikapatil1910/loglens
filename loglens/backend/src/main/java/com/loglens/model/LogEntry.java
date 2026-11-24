package com.loglens.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One logical log record: a line with a recognised level plus any continuation
 * lines (stack-trace frames, "Caused by:", etc.) that belong to it.
 *
 * @param timestampText original timestamp text, or null if none was found
 * @param timestamp     parsed wall-clock time as logged (time zone offset is not retained), or null
 */
public record LogEntry(
        int lineNumber,
        String timestampText,
        LocalDateTime timestamp,
        LogLevel level,
        String message,
        String rawLine,
        List<String> continuationLines) {

    public LogEntry {
        message = message == null ? "" : message;
        continuationLines = continuationLines == null ? List.of() : List.copyOf(continuationLines);
    }

    public boolean hasContinuation() {
        return !continuationLines.isEmpty();
    }
}
