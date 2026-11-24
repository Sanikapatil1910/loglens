package com.loglens.model;

import java.util.List;

/**
 * A troubleshooting hint. Always a possible explanation, never a confirmed diagnosis.
 *
 * @param generic true when no specific rule matched and this is the general fallback
 */
public record Suggestion(
        String ruleId,
        String title,
        String possibleCause,
        List<String> suggestedChecks,
        boolean generic,
        String disclaimer) {

    public Suggestion {
        suggestedChecks = List.copyOf(suggestedChecks);
    }
}
