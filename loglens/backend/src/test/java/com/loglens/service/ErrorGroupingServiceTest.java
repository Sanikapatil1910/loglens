package com.loglens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loglens.model.ErrorGroup;
import com.loglens.model.LogLevel;
import com.loglens.parser.LogLineParser;
import com.loglens.parser.MessageNormalizer;
import com.loglens.parser.StackTraceParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorGroupingServiceTest {

    private final LogLineParser parser = new LogLineParser();
    private final RedactionService redaction = new RedactionService();
    private final ErrorGroupingService service = new ErrorGroupingService(
            new StackTraceParser(), new MessageNormalizer(), new TroubleshootingEngine());

    private List<ErrorGroup> groupText(String... lines) {
        return service.group(parser.parseText(String.join("\n", lines)).entries());
    }

    private static String npe(String time, int line) {
        return time + " ERROR Failed to load user requestId=abc" + line + "\n"
                + "java.lang.NullPointerException: Cannot invoke \"User.getName()\" because \"user\" is null\n"
                + "\tat com.example.UserService.getUser(UserService.java:" + line + ")";
    }

    @Test
    void groupsRepeatedErrorsDespiteChangingTimestampsAndRequestIds() {
        List<ErrorGroup> groups = groupText(
                npe("2026-10-22 10:00:01", 42),
                npe("2026-10-22 10:05:09", 42),
                npe("2026-10-22 10:09:30", 42));

        assertEquals(1, groups.size());
        ErrorGroup g = groups.get(0);
        assertEquals(3, g.occurrenceCount());
        assertEquals("java.lang.NullPointerException", g.exceptionType());
        assertEquals(LogLevel.ERROR, g.level());
        assertEquals(1, g.firstOccurrence().lineNumber());
        assertEquals("2026-10-22 10:00:01", g.firstOccurrence().timestamp());
        assertEquals(7, g.lastOccurrence().lineNumber());
        assertEquals("2026-10-22 10:09:30", g.lastOccurrence().timestamp());
        assertEquals(3, g.representativeLines().size());
        assertNotNull(g.stackTrace());
        assertEquals("null-pointer", g.suggestions().get(0).ruleId());
        assertEquals(16, g.id().length());
    }

    @Test
    void doesNotMergeSameExceptionFromDifferentSourceLines() {
        List<ErrorGroup> groups = groupText(npe("2026-10-22 10:00:01", 42), npe("2026-10-22 10:00:02", 77));
        assertEquals(2, groups.size());
    }

    @Test
    void doesNotMergeDifferentExceptionTypesOrDifferentMessages() {
        List<ErrorGroup> byType = groupText(
                "ERROR a\njava.lang.IllegalArgumentException: bad\n\tat com.example.A.run(A.java:1)",
                "ERROR a\njava.lang.IllegalStateException: bad\n\tat com.example.A.run(A.java:1)");
        assertEquals(2, byType.size());

        List<ErrorGroup> byMessage = groupText(
                "ERROR a java.sql.SQLException: Connection refused: localhost:5432",
                "ERROR a java.sql.SQLException: Connection refused: localhost:6379");
        assertEquals(2, byMessage.size());
    }

    @Test
    void doesNotMergeWrappersWithDifferentRootCauses() {
        List<ErrorGroup> groups = groupText(
                "ERROR a\njava.lang.RuntimeException: wrapped\n\tat com.example.A.run(A.java:1)\nCaused by: java.io.IOException: disk\n\tat com.example.B.write(B.java:2)",
                "ERROR a\njava.lang.RuntimeException: wrapped\n\tat com.example.A.run(A.java:1)\nCaused by: java.net.ConnectException: Connection refused\n\tat com.example.C.dial(C.java:3)");
        assertEquals(2, groups.size());
    }

    @Test
    void groupsGenericErrorsWithoutExceptionsAfterNormalisation() {
        List<ErrorGroup> groups = groupText(
                "2026-10-22 10:00:01 ERROR Gateway failed for 3f2b8c1e-1111-2222-3333-444455556666",
                "2026-10-22 10:00:02 ERROR Gateway failed for 9a8b7c6d-aaaa-bbbb-cccc-ddddeeeeffff");
        assertEquals(1, groups.size());
        assertEquals(ErrorGroup.NO_EXCEPTION_TYPE, groups.get(0).exceptionType());
        assertEquals("Gateway failed for <UUID>", groups.get(0).normalizedMessage());
        assertEquals(2, groups.get(0).occurrenceCount());
        assertTrue(groups.get(0).suggestions().get(0).generic());
    }

    @Test
    void onlyErrorsAndWarningsWithExceptionsAreGrouped() {
        List<ErrorGroup> groups = groupText(
                "INFO all good",
                "DEBUG details",
                "WARN disk is getting full",
                "WARN retrying after java.io.IOException: reset",
                "FATAL cannot continue");
        assertEquals(2, groups.size());
        assertTrue(groups.stream().anyMatch(g -> g.level() == LogLevel.WARN
                && g.exceptionType().equals("java.io.IOException")));
        assertTrue(groups.stream().anyMatch(g -> g.level() == LogLevel.FATAL));
    }

    @Test
    void sortsByOccurrenceCountDescending() {
        List<ErrorGroup> groups = groupText(
                "ERROR rare problem",
                "ERROR common problem", "ERROR common problem", "ERROR common problem");
        assertEquals(2, groups.size());
        assertEquals(3, groups.get(0).occurrenceCount());
        assertEquals("common problem", groups.get(0).normalizedMessage());
        assertEquals(1, groups.get(1).occurrenceCount());
    }

    @Test
    void exposesRootCauseCandidateOfNestedTraces() {
        List<ErrorGroup> groups = groupText(
                "ERROR Request failed",
                "java.lang.IllegalStateException: Bad state",
                "\tat com.example.Foo.bar(Foo.java:10)",
                "Caused by: java.lang.NullPointerException: x is null",
                "\tat com.example.Baz.qux(Baz.java:5)");
        assertEquals(1, groups.size());
        assertEquals("java.lang.NullPointerException", groups.get(0).rootCauseCandidate().type());
        assertEquals("java.lang.IllegalStateException", groups.get(0).exceptionType());
    }

    @Test
    void sanitizerIsAppliedToEveryDisplayedTextButNotToGrouping() {
        List<ErrorGroup> groups = service.group(
                parser.parseText("ERROR Login failed password=hunter2\nERROR Login failed password=other99").entries(),
                redaction::redact);

        assertEquals(2, groups.size());
        for (ErrorGroup g : groups) {
            assertFalse(g.normalizedMessage().contains("hunter2") || g.normalizedMessage().contains("other99"));
            for (var line : g.representativeLines()) {
                assertFalse(line.text().contains("hunter2") || line.text().contains("other99"));
                assertTrue(line.text().contains("[REDACTED]"));
            }
        }
    }

    @Test
    void idsAreStableAcrossRuns() {
        String[] lines = {npe("2026-10-22 10:00:01", 42)};
        assertEquals(groupText(lines).get(0).id(), groupText(lines).get(0).id());
    }

    @Test
    void emptyInputGivesNoGroups() {
        assertTrue(service.group(List.of()).isEmpty());
    }
}
