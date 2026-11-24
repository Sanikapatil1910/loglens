package com.loglens.dto;

import java.time.Instant;

/**
 * Dashboard numbers. errorCount is ERROR + FATAL entries, warningCount is WARN entries,
 * debugTraceCount is DEBUG + TRACE entries. All counts are per log record, not per unique error.
 */
public record AnalysisSummary(
        String sourceName,
        boolean sampleData,
        Instant analyzedAt,
        boolean redactionApplied,
        long totalLines,
        long parsedLines,
        long blankLines,
        long unknownLines,
        long errorCount,
        long warningCount,
        long infoCount,
        long debugTraceCount,
        int uniqueErrorGroups,
        FrequentItem mostFrequentException,
        FrequentItem mostFrequentErrorMessage) {
}
