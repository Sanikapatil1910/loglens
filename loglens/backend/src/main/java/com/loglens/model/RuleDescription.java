package com.loglens.model;

import java.util.List;

/** Public description of one local troubleshooting rule (served by /api/logs/error-rules). */
public record RuleDescription(
        String id,
        String title,
        String appliesTo,
        String possibleCause,
        List<String> suggestedChecks) {

    public RuleDescription {
        suggestedChecks = List.copyOf(suggestedChecks);
    }
}
