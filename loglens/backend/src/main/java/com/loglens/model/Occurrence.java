package com.loglens.model;

/** Where an error was seen: 1-based line number and the original timestamp text, if any. */
public record Occurrence(int lineNumber, String timestamp) {
}
