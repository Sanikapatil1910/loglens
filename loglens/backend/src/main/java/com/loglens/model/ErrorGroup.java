package com.loglens.model;

import java.util.List;

/** A set of similar error occurrences collapsed into one entry. */
public record ErrorGroup(
        String id,
        LogLevel level,
        String exceptionType,
        String normalizedMessage,
        long occurrenceCount,
        Occurrence firstOccurrence,
        Occurrence lastOccurrence,
        ExceptionInfo rootCauseCandidate,
        StackTraceInfo stackTrace,
        List<LogLineRef> representativeLines,
        List<Suggestion> suggestions) {

    /** Exception type label used for ERROR/FATAL entries that carry no exception. */
    public static final String NO_EXCEPTION_TYPE = "(no exception type)";

    public ErrorGroup {
        representativeLines = List.copyOf(representativeLines);
        suggestions = List.copyOf(suggestions);
    }
}
