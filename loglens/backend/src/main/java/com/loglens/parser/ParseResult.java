package com.loglens.parser;

import com.loglens.model.LogEntry;
import java.util.List;

/**
 * Outcome of parsing a log.
 *
 * @param entries           logical entries (level line + continuation lines)
 * @param totalLines        every line read, including blank ones
 * @param blankLines        blank lines (ignored)
 * @param continuationLines lines attached to a previous entry (stack frames etc.)
 * @param unknownLines      non-blank lines that could not be classified
 */
public record ParseResult(
        List<LogEntry> entries,
        int totalLines,
        int blankLines,
        int continuationLines,
        int unknownLines) {

    public ParseResult {
        entries = List.copyOf(entries);
    }

    /** Lines that were understood: entry header lines plus attached continuation lines. */
    public int parsedLines() {
        return entries.size() + continuationLines;
    }
}
