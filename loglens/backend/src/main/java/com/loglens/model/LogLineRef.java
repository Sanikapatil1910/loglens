package com.loglens.model;

/** A representative log line with its 1-based line number. */
public record LogLineRef(int lineNumber, String text) {
}
