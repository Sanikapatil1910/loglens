package com.loglens.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/logs/analyze-text.
 *
 * @param redact mask obvious secrets in the response; defaults to true when omitted
 */
public record AnalyzeTextRequest(
        @NotBlank(message = "must not be blank") String text,
        Boolean redact) {

    public boolean redactOrDefault() {
        return redact == null || redact;
    }
}
