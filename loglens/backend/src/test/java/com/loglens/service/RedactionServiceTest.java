package com.loglens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedactionServiceTest {

    private final RedactionService redaction = new RedactionService();

    @Test
    void masksPasswordsAndApiKeys() {
        assertEquals("Login failed password=[REDACTED] for user bob",
                redaction.redact("Login failed password=hunter2 for user bob"));
        assertFalse(redaction.redact("api_key: sk_live_12345").contains("sk_live_12345"));
        assertFalse(redaction.redact("Password: \"my secret\"").contains("my secret"));
    }

    @Test
    void masksBearerTokensJwtsAndAuthorizationHeaders() {
        assertFalse(redaction.redact("Authorization: Bearer abc.def-123").contains("abc.def-123"));
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl";
        assertFalse(redaction.redact("token seen " + jwt).contains(jwt));
    }

    @Test
    void masksJsonStyleValues() {
        assertFalse(redaction.redact("{\"token\":\"abc123xyz\"}").contains("abc123xyz"));
    }

    @Test
    void masksCredentialsInUrlsAndAwsKeys() {
        String url = redaction.redact("jdbc:postgresql://admin:s3cr3t@db:5432/app");
        assertFalse(url.contains("s3cr3t"));
        assertTrue(url.contains("@db:5432/app"));
        assertFalse(redaction.redact("key AKIAABCDEFGHIJKLMNOP used").contains("AKIAABCDEFGHIJKLMNOP"));
    }

    @Test
    void leavesOrdinaryTextAndNullAlone() {
        assertEquals("User 42 logged in", redaction.redact("User 42 logged in"));
        assertEquals("", redaction.redact(null));
    }
}
