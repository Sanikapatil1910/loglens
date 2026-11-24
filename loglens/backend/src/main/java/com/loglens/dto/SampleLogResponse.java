package com.loglens.dto;

/** The bundled demo log and its analysis. Always clearly labelled as sample data. */
public record SampleLogResponse(String filename, String notice, String content, AnalysisResponse analysis) {
}
