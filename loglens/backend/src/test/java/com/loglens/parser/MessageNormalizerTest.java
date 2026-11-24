package com.loglens.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class MessageNormalizerTest {

    private final MessageNormalizer normalizer = new MessageNormalizer();

    @Test
    void replacesUuidsAndMemoryAddresses() {
        assertEquals("Request <UUID> failed at <ADDR>",
                normalizer.normalize("Request 3f2b8c1e-1111-2222-3333-444455556666 failed at 0x7f8a1b2c"));
    }

    @Test
    void sameErrorWithDifferentDynamicValuesNormalisesIdentically() {
        String a = normalizer.normalize("2026-10-22 10:00:01 Timeout requestId=abc123 order 12345678");
        String b = normalizer.normalize("2026-10-22 11:45:59,512 Timeout requestId=zzz999 order 87654321");
        assertEquals(a, b);
        assertEquals("<TS> Timeout requestId=<ID> order <NUM>", a);
    }

    @Test
    void normalisesObjectIdentityHashesAndWorkerNames() {
        assertEquals("Bad com.example.Foo@<HASH> in pool-<N>-thread-<N>",
                normalizer.normalize("Bad com.example.Foo@1a2b3c4d in pool-3-thread-12"));
    }

    @Test
    void doesNotMergeMeaningfullyDifferentValues() {
        assertNotEquals(normalizer.normalize("Connection refused: localhost:5432"),
                normalizer.normalize("Connection refused: localhost:6379"));
        assertNotEquals(normalizer.normalize("Connection refused: 10.0.0.1:5432"),
                normalizer.normalize("Connection refused: 10.0.0.2:5432"));
        assertEquals("Index 5 out of bounds for length 3",
                normalizer.normalize("Index 5 out of bounds for length 3"));
    }

    @Test
    void handlesNullBlankAndExtraWhitespace() {
        assertEquals("", normalizer.normalize(null));
        assertEquals("", normalizer.normalize("   "));
        assertEquals("a b c", normalizer.normalize("  a   b \t c "));
    }
}
