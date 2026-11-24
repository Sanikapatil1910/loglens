package com.loglens.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loglens.model.LogEntry;
import com.loglens.model.StackFrame;
import com.loglens.model.StackTraceInfo;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StackTraceParserTest {

    private final LogLineParser lineParser = new LogLineParser();
    private final StackTraceParser traceParser = new StackTraceParser();

    private Optional<StackTraceInfo> parse(String... lines) {
        LogEntry entry = lineParser.parseText(String.join("\n", lines)).entries().get(0);
        return traceParser.parse(entry);
    }

    @Test
    void picksDeepestCausedByAsRootCauseCandidateAndExplainsItIsAHeuristic() {
        StackTraceInfo trace = parse(
                "2026-10-22 10:30:12 ERROR Request failed",
                "java.lang.IllegalStateException: Bad state",
                "\tat com.example.Foo.bar(Foo.java:10)",
                "Caused by: java.lang.NullPointerException: x is null",
                "\tat com.example.Baz.qux(Baz.java:5)",
                "\t... 3 more").orElseThrow();

        assertEquals("java.lang.IllegalStateException", trace.mainException().type());
        assertEquals("Bad state", trace.mainException().message());
        assertEquals("java.lang.NullPointerException", trace.rootCauseCandidate().type());
        assertEquals("x is null", trace.rootCauseCandidate().message());
        assertEquals(2, trace.causeChain().size());
        assertTrue(trace.rootCauseExplanation().startsWith("Heuristic"));

        StackFrame top = trace.topFrame().orElseThrow();
        assertEquals("com.example.Baz", top.className());
        assertEquals("qux", top.methodName());
        assertEquals("Baz.java", top.fileName());
        assertEquals(Integer.valueOf(5), top.lineNumber());
        assertTrue(top.applicationFrame());
    }

    @Test
    void parsesSingleLineErrorsWithInlineFrame() {
        StackTraceInfo trace = parse(
                "2026-10-22 10:30:12 ERROR java.lang.NullPointerException at com.example.UserService.getUser(UserService.java:42)")
                .orElseThrow();

        assertEquals("java.lang.NullPointerException", trace.mainException().type());
        assertEquals("", trace.mainException().message());
        assertEquals(1, trace.relevantFrames().size());
        StackFrame f = trace.relevantFrames().get(0);
        assertEquals("com.example.UserService", f.className());
        assertEquals("getUser", f.methodName());
        assertEquals("UserService.java", f.fileName());
        assertEquals(Integer.valueOf(42), f.lineNumber());
        assertEquals("com.example.UserService.getUser(UserService.java:42)", f.displayName());
    }

    @Test
    void extractsExceptionFromLogMessageWithMessageText() {
        StackTraceInfo trace = parse(
                "ERROR Could not connect: java.sql.SQLException: Connection refused: localhost:5432").orElseThrow();
        assertEquals("java.sql.SQLException", trace.mainException().type());
        assertEquals("Connection refused: localhost:5432", trace.mainException().message());
    }

    @Test
    void supportsCustomAndUnknownExceptionTypes() {
        StackTraceInfo trace = parse(
                "ERROR Order failed",
                "com.acme.billing.InvoiceMismatchException: totals differ",
                "\tat com.acme.billing.InvoiceService.check(InvoiceService.java:77)").orElseThrow();
        assertEquals("com.acme.billing.InvoiceMismatchException", trace.mainException().type());
        assertEquals("InvoiceMismatchException", trace.mainException().simpleName());
    }

    @Test
    void recordsSuppressedExceptionsSeparatelyFromTheCauseChain() {
        StackTraceInfo trace = parse(
                "ERROR Close failed",
                "java.lang.RuntimeException: primary",
                "\tat com.example.A.run(A.java:1)",
                "\tSuppressed: java.io.IOException: close failed",
                "\t\tat com.example.B.close(B.java:9)").orElseThrow();

        assertEquals(1, trace.causeChain().size());
        assertEquals(1, trace.suppressedExceptions().size());
        assertEquals("java.io.IOException", trace.suppressedExceptions().get(0).type());
        assertEquals(1, trace.relevantFrames().size());
        assertEquals("com.example.A", trace.relevantFrames().get(0).className());
    }

    @Test
    void prefersApplicationFramesOverFrameworkFrames() {
        StackTraceInfo trace = parse(
                "ERROR Failed",
                "java.lang.RuntimeException: x",
                "\tat org.springframework.web.Foo.handle(Foo.java:1)",
                "\tat com.example.Api.call(Api.java:2)",
                "\tat jdk.internal.reflect.X.invoke(X.java:3)").orElseThrow();

        assertEquals(1, trace.relevantFrames().size());
        assertEquals("com.example.Api", trace.relevantFrames().get(0).className());
    }

    @Test
    void fallsBackToFirstFramesWhenNoApplicationFrameExists() {
        StackTraceInfo trace = parse(
                "ERROR Failed",
                "java.lang.RuntimeException: x",
                "\tat java.base/java.util.Objects.requireNonNull(Objects.java:208)").orElseThrow();

        StackFrame f = trace.relevantFrames().get(0);
        assertEquals("java.util.Objects", f.className());
        assertEquals("requireNonNull", f.methodName());
        assertFalse(f.applicationFrame());
    }

    @Test
    void returnsEmptyWhenEntryHasNoException() {
        assertTrue(parse("2026-10-22 10:30:12 ERROR Something failed").isEmpty());
    }

    @Test
    void limitsRawLinesAndFlagsTruncation() {
        StringBuilder sb = new StringBuilder("ERROR Deep\njava.lang.RuntimeException: x");
        for (int i = 0; i < 150; i++) {
            sb.append("\n\tat com.example.Deep.call").append(i).append("(Deep.java:").append(i + 1).append(")");
        }
        StackTraceInfo trace = parse(sb.toString().split("\n")).orElseThrow();
        assertEquals(StackTraceParser.MAX_RAW_LINES, trace.rawLines().size());
        assertTrue(trace.truncated());
        assertEquals(StackTraceParser.MAX_RELEVANT_FRAMES, trace.relevantFrames().size());
    }
}
