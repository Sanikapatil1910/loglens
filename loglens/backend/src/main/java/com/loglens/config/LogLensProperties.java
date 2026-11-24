package com.loglens.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalised LogLens settings (prefix "loglens"), with safe defaults. */
@ConfigurationProperties(prefix = "loglens")
public record LogLensProperties(
        long maxUploadBytes,
        List<String> allowedExtensions,
        List<String> allowedOrigins) {

    public LogLensProperties {
        if (maxUploadBytes <= 0) {
            maxUploadBytes = 10L * 1024 * 1024;
        }
        allowedExtensions = allowedExtensions == null || allowedExtensions.isEmpty()
                ? List.of("log", "txt")
                : List.copyOf(allowedExtensions);
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ? List.of("http://localhost:5173")
                : List.copyOf(allowedOrigins);
    }
}
