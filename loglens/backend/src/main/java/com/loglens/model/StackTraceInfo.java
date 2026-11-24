package com.loglens.model;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Structured view of a Java stack trace attached to a log entry.
 *
 * @param rootCauseCandidate deepest "Caused by" entry (a heuristic, not a proven root cause)
 * @param relevantFrames     frames of the root-cause candidate, application code first
 */
public record StackTraceInfo(
        ExceptionInfo mainException,
        ExceptionInfo rootCauseCandidate,
        String rootCauseExplanation,
        List<ExceptionInfo> causeChain,
        List<ExceptionInfo> suppressedExceptions,
        List<StackFrame> relevantFrames,
        List<String> rawLines,
        boolean truncated) {

    public StackTraceInfo {
        causeChain = List.copyOf(causeChain);
        suppressedExceptions = List.copyOf(suppressedExceptions);
        relevantFrames = List.copyOf(relevantFrames);
        rawLines = List.copyOf(rawLines);
    }

    public Optional<StackFrame> topFrame() {
        return relevantFrames.isEmpty() ? Optional.empty() : Optional.of(relevantFrames.get(0));
    }

    /** Returns a copy whose free-text fields (messages, raw lines) pass through the sanitizer. */
    public StackTraceInfo withSanitizedText(UnaryOperator<String> sanitizer) {
        return new StackTraceInfo(
                sanitize(mainException, sanitizer),
                sanitize(rootCauseCandidate, sanitizer),
                rootCauseExplanation,
                causeChain.stream().map(e -> sanitize(e, sanitizer)).toList(),
                suppressedExceptions.stream().map(e -> sanitize(e, sanitizer)).toList(),
                relevantFrames,
                rawLines.stream().map(sanitizer).toList(),
                truncated);
    }

    private static ExceptionInfo sanitize(ExceptionInfo info, UnaryOperator<String> sanitizer) {
        return new ExceptionInfo(info.type(), sanitizer.apply(info.message()));
    }
}
