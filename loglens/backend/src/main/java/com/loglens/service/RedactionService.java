package com.loglens.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Best-effort masking of obvious secrets (passwords, tokens, API keys, credentials in URLs).
 * This is a simple pattern-based safeguard. It will miss some secrets and can mask harmless
 * text, so logs must still be reviewed before being shared.
 */
@Service
public class RedactionService {

    public static final String MASK = "[REDACTED]";

    private static final Pattern AUTH_SCHEME =
            Pattern.compile("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9\\-._~+/]+=*");
    private static final Pattern JWT =
            Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
    private static final Pattern AWS_KEY = Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b");
    private static final Pattern URL_CREDENTIALS = Pattern.compile("(?<=://)[^/\\s:@]+:[^/\\s@]+@");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|passphrase|secret|token|api[_-]?key|access[_-]?key|authorization)\\b"
                    + "[\"']?\\s*[=:]\\s*(\"[^\"]*\"|'[^']*'|[^\\s,;&]+)");

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String s = AUTH_SCHEME.matcher(text).replaceAll("$1 " + MASK);
        s = JWT.matcher(s).replaceAll(MASK);
        s = AWS_KEY.matcher(s).replaceAll(MASK);
        s = URL_CREDENTIALS.matcher(s).replaceAll(MASK + "@");
        s = KEY_VALUE.matcher(s).replaceAll("$1=" + MASK);
        return s;
    }
}
