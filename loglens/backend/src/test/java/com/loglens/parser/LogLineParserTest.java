package com.loglens.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loglens.model.LogEntry;
import com.loglens.model.LogLevel;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LogLineParserTest {

    private final LogLineParser parser = new LogLineParser();

    private LogLineParser.LevelLine match(String line) {
        Optional<LogLineParser.LevelLine> m = parser.matchLevelLine(line);
        assertTrue(m.isPresent(), "expected a level to be detected in: " + line);
        return m.get();
    }

    @Test
    void detectsLevelInSpaceSeparatedTimestampFormat() {
        LogLineParser.LevelLine m = match("2026-10-22 10:30:12 ERROR Failed to start");
        assertEquals(LogLevel.ERROR, m.level());
        assertEquals("2026-10-22 10:30:12", m.timestampText());
        assertEquals("Failed to start", m.message());
    }

    @Test
    void detectsLevelInIso8601Format() {
        LogLineParser.LevelLine m = match("2026-10-22T10:30:12.123Z ERROR boom");
        assertEquals(LogLevel.ERROR, m.level());
        assertEquals("2026-10-22T10:30:12.123Z", m.timestampText());
        assertEquals("boom", m.message());
    }

    @Test
    void detectsBracketedAndBareLevels() {
        assertEquals(LogLevel.ERROR, match("[ERROR] boom").level());
        assertEquals("boom", match("[ERROR] boom").message());
        assertEquals(LogLevel.ERROR, match("ERROR boom").level());
        assertEquals(LogLevel.FATAL, match("FATAL out of luck").level());
        assertEquals(LogLevel.INFO, match("INFO started").level());
        assertEquals(LogLevel.DEBUG, match("DEBUG x").level());
        assertEquals(LogLevel.TRACE, match("TRACE x").level());
    }

    @Test
    void mapsWarningAndSevereOntoStandardLevels() {
        assertEquals(LogLevel.WARN, match("WARNING disk low").level());
        assertEquals(LogLevel.WARN, match("WARN disk low").level());
        assertEquals(LogLevel.ERROR, match("SEVERE meltdown").level());
    }

    @Test
    void handlesThreadAndLoggerInLogbackStyleLines() {
        LogLineParser.LevelLine m = match("2026-10-22 10:30:12,123 [main] WARN c.e.Foo - slow query");
        assertEquals(LogLevel.WARN, m.level());
        assertEquals("slow query", m.message());
    }

    @Test
    void handlesSpringBootStyleLines() {
        LogLineParser.LevelLine m = match(
                "2026-10-22T10:30:12.123+05:30  ERROR 4242 --- [           main] c.e.UserService : Failed to load user");
        assertEquals(LogLevel.ERROR, m.level());
        assertEquals("Failed to load user", m.message());
    }

    @Test
    void doesNotGuessLevelsForUnrecognisedLines() {
        assertTrue(parser.matchLevelLine("just some text").isEmpty());
        assertTrue(parser.matchLevelLine("2026-10-22 10:30:12 something happened").isEmpty());
        assertTrue(parser.matchLevelLine("ERRORS were found").isEmpty());
        assertTrue(parser.matchLevelLine("error in lowercase is not a level").isEmpty());
    }

    @Test
    void parsesTimestampVariants() {
        assertEquals(Optional.of(LocalDateTime.of(2026, 10, 22, 10, 30, 12)),
                LogLineParser.parseTimestamp("2026-10-22 10:30:12"));
        assertEquals(Optional.of(LocalDateTime.of(2026, 10, 22, 10, 30, 12, 123_000_000)),
                LogLineParser.parseTimestamp("2026-10-22 10:30:12,123"));
        assertEquals(Optional.of(LocalDateTime.of(2026, 10, 22, 10, 30, 12, 123_000_000)),
                LogLineParser.parseTimestamp("2026-10-22T10:30:12.123Z"));
        assertEquals(Optional.of(LocalDateTime.of(2026, 10, 22, 10, 30, 12)),
                LogLineParser.parseTimestamp("2026-10-22T10:30:12+05:30"));
        assertEquals(Optional.of(LocalDateTime.of(2026, 10, 22, 10, 30, 12)),
                LogLineParser.parseTimestamp("2026-10-22T10:30:12+0530"));
    }

    @Test
    void unparseableTimestampsDoNotThrow() {
        assertTrue(LogLineParser.parseTimestamp("2026-13-45 99:99:99").isEmpty());
        assertTrue(LogLineParser.parseTimestamp("garbage").isEmpty());
        assertTrue(LogLineParser.parseTimestamp(null).isEmpty());

        ParseResult result = parser.parseText("2026-13-45 99:99:99 ERROR still parsed");
        assertEquals(1, result.entries().size());
        assertEquals(LogLevel.ERROR, result.entries().get(0).level());
        assertEquals(null, result.entries().get(0).timestamp());
        assertEquals("2026-13-45 99:99:99", result.entries().get(0).timestampText());
    }

    @Test
    void attachesStackTraceLinesToTheirEntry() {
        String text = String.join("\n",
                "2026-10-22 10:30:12 ERROR Request failed",
                "java.lang.IllegalStateException: Bad state",
                "\tat com.example.Foo.bar(Foo.java:10)",
                "Caused by: java.lang.NullPointerException: x",
                "\tat com.example.Baz.qux(Baz.java:5)",
                "\t... 3 more",
                "2026-10-22 10:30:13 INFO Next");
        ParseResult result = parser.parseText(text);

        assertEquals(2, result.entries().size());
        LogEntry first = result.entries().get(0);
        assertEquals(1, first.lineNumber());
        assertEquals(5, first.continuationLines().size());
        assertEquals(7, result.entries().get(1).lineNumber());
        assertTrue(result.entries().get(1).continuationLines().isEmpty());
        assertEquals(5, result.continuationLines());
        assertEquals(0, result.unknownLines());
        assertEquals(7, result.totalLines());
        assertEquals(7, result.parsedLines());
    }

    @Test
    void countsUnknownAndBlankLinesSeparately() {
        String text = String.join("\n",
                "\tat orphan.Frame.call(Frame.java:1)",
                "",
                "INFO started",
                "random garbage line",
                "\tat after.Garbage.call(Garbage.java:1)",
                "WARN careful");
        ParseResult result = parser.parseText(text);

        assertEquals(6, result.totalLines());
        assertEquals(1, result.blankLines());
        assertEquals(3, result.unknownLines());
        assertEquals(2, result.entries().size());
    }

    @Test
    void emptyAndNullInputProduceEmptyResult() {
        assertEquals(0, parser.parseText("").totalLines());
        assertEquals(0, parser.parseText(null).entries().size());
        ParseResult blankOnly = parser.parseText("\n\n   \n");
        assertEquals(0, blankOnly.entries().size());
        assertEquals(3, blankOnly.blankLines());
    }

    @Test
    void toleratesByteOrderMarkAndWindowsLineEndings() {
        ParseResult result = parser.parseText("\uFEFFERROR first\r\nINFO second\r\n");
        assertEquals(2, result.entries().size());
        assertEquals(LogLevel.ERROR, result.entries().get(0).level());
        assertNotNull(result.entries().get(1).message());
    }
}
