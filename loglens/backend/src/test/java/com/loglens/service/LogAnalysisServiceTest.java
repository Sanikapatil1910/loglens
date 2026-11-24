package com.loglens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loglens.dto.AnalysisResponse;
import com.loglens.dto.LevelCount;
import com.loglens.model.ErrorGroup;
import com.loglens.model.LogLevel;
import com.loglens.parser.LogLineParser;
import com.loglens.parser.MessageNormalizer;
import com.loglens.parser.StackTraceParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Deterministic expectations for samples/sample-application.log (counts verified by hand against the file). */
class LogAnalysisServiceTest {

    private static final String SECRET = "demo-not-a-real-secret";

    private final SampleLogService sampleService = new SampleLogService();
    private final LogAnalysisService service = new LogAnalysisService(
            new LogLineParser(),
            new ErrorGroupingService(new StackTraceParser(), new MessageNormalizer(), new TroubleshootingEngine()),
            new RedactionService());

    private AnalysisResponse sample() {
        return service.analyzeSample(sampleService.loadContent());
    }

    private static long countOf(AnalysisResponse r, LogLevel level) {
        return r.logLevelCounts().stream().filter(c -> c.level() == level).findFirst().orElseThrow().count();
    }

    private static ErrorGroup groupOfType(AnalysisResponse r, String type, long occurrences) {
        return r.errorGroups().stream()
                .filter(g -> g.exceptionType().equals(type) && g.occurrenceCount() == occurrences)
                .findFirst().orElseThrow();
    }

    @Test
    void sampleLogLineAndLevelCountsMatchTheFile() {
        AnalysisResponse r = sample();
        assertEquals(72, r.summary().totalLines());
        assertEquals(68, r.summary().parsedLines());
        assertEquals(0, r.summary().blankLines());
        assertEquals(4, r.summary().unknownLines());
        assertEquals(4, r.unknownLineCount());

        assertEquals(1, countOf(r, LogLevel.FATAL));
        assertEquals(12, countOf(r, LogLevel.ERROR));
        assertEquals(3, countOf(r, LogLevel.WARN));
        assertEquals(6, countOf(r, LogLevel.INFO));
        assertEquals(1, countOf(r, LogLevel.DEBUG));
        assertEquals(1, countOf(r, LogLevel.TRACE));
        assertEquals(4, countOf(r, LogLevel.UNKNOWN));

        assertEquals(13, r.summary().errorCount());
        assertEquals(3, r.summary().warningCount());
        assertEquals(6, r.summary().infoCount());
        assertEquals(2, r.summary().debugTraceCount());
    }

    @Test
    void levelPercentagesAreBasedOnRecordsAndSumToAboutOneHundred() {
        AnalysisResponse r = sample();
        assertEquals(7, r.logLevelCounts().size());
        double sum = r.logLevelCounts().stream().mapToDouble(LevelCount::percentage).sum();
        assertTrue(Math.abs(sum - 100.0) < 0.1, "sum was " + sum);
        // 28 records in total: 24 entries + 4 unknown lines; 12 ERROR entries = 42.86 %
        assertEquals(42.86, r.logLevelCounts().stream()
                .filter(c -> c.level() == LogLevel.ERROR).findFirst().orElseThrow().percentage());
    }

    @Test
    void sampleProducesTheExpectedErrorGroups() {
        AnalysisResponse r = sample();
        assertEquals(9, r.uniqueErrorGroups());
        assertEquals(9, r.errorGroups().size());
        assertTrue(r.summary().sampleData());

        ErrorGroup top = r.errorGroups().get(0);
        assertEquals("java.lang.NullPointerException", top.exceptionType());
        assertEquals(3, top.occurrenceCount());
        assertEquals(6, top.firstOccurrence().lineNumber());
        assertEquals(19, top.lastOccurrence().lineNumber());

        // The second NullPointerException (OrderService) must stay separate from the UserService one.
        assertEquals(1, groupOfType(r, "java.lang.NullPointerException", 1).occurrenceCount());
        assertEquals(2, groupOfType(r, "java.net.ConnectException", 2).occurrenceCount());
        assertEquals("java.lang.OutOfMemoryError", groupOfType(r, "java.lang.OutOfMemoryError", 1).exceptionType());
        assertEquals(2, groupOfType(r, ErrorGroup.NO_EXCEPTION_TYPE, 2).occurrenceCount());
    }

    @Test
    void nestedCauseIsReportedAsRootCauseCandidate() {
        ErrorGroup g = groupOfType(sample(), "org.springframework.dao.DataAccessResourceFailureException", 2);
        assertEquals("java.net.SocketTimeoutException", g.rootCauseCandidate().type());
        assertEquals(3, g.stackTrace().causeChain().size());
        assertEquals("com.example.db.ConnectionHelper", g.stackTrace().topFrame().orElseThrow().className());
        assertEquals("socket-timeout", g.suggestions().get(0).ruleId());
    }

    @Test
    void summaryNamesTheMostFrequentExceptionAndMessage() {
        AnalysisResponse r = sample();
        assertEquals("java.lang.NullPointerException", r.summary().mostFrequentException().name());
        assertEquals(4, r.summary().mostFrequentException().count());
        assertEquals(3, r.summary().mostFrequentErrorMessage().count());
        assertEquals("java.lang.NullPointerException", r.topExceptions().get(0).name());
        assertTrue(r.topExceptions().stream().noneMatch(e -> e.name().equals(ErrorGroup.NO_EXCEPTION_TYPE)));
    }

    @Test
    void redactionMasksSecretsByDefaultAndCanBeSwitchedOff() {
        String text = sampleService.loadContent();

        AnalysisResponse masked = service.analyze(text, "app.log", true);
        assertTrue(masked.summary().redactionApplied());
        assertFalse(allText(masked).contains(SECRET));
        assertTrue(allText(masked).contains("[REDACTED]"));
        assertTrue(masked.warnings().stream().noneMatch(w -> w.contains("Redaction is turned off")));

        AnalysisResponse raw = service.analyze(text, "app.log", false);
        assertFalse(raw.summary().redactionApplied());
        assertTrue(allText(raw).contains(SECRET));
        assertTrue(raw.warnings().stream().anyMatch(w -> w.contains("Redaction is turned off")));
    }

    @Test
    void logsWithoutRecognisableLevelsProduceAWarningNotACrash() {
        AnalysisResponse r = service.analyze("hello\nworld\nthis is not a log\n", "x.log", true);
        assertEquals(0, r.uniqueErrorGroups());
        assertEquals(3, r.unknownLineCount());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("No log levels were recognised")));
        assertEquals(100.0, countPercent(r, LogLevel.UNKNOWN));
        assertNotNull(r.privacyNote());
    }

    @Test
    void unparseableTimestampsAreReportedButEntriesStillAnalysed() {
        AnalysisResponse r = service.analyze("2026-13-45 99:99:99 ERROR still counted\n", "x.log", true);
        assertEquals(1, r.summary().errorCount());
        assertEquals(1, r.uniqueErrorGroups());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("could not be parsed")));
    }

    @Test
    void emptyPercentagesAreZeroWhenThereAreNoRecords() {
        AnalysisResponse r = service.analyze("\n\n", "x.log", true);
        assertEquals(0.0, r.logLevelCounts().stream().mapToDouble(LevelCount::percentage).sum());
    }

    @Test
    void repeatedAnalysisIsDeterministic() {
        AnalysisResponse a = sample();
        AnalysisResponse b = sample();
        assertEquals(a.errorGroups().stream().map(ErrorGroup::id).toList(),
                b.errorGroups().stream().map(ErrorGroup::id).toList());
    }

    private static double countPercent(AnalysisResponse r, LogLevel level) {
        return r.logLevelCounts().stream().filter(c -> c.level() == level).findFirst().orElseThrow().percentage();
    }

    private static String allText(AnalysisResponse r) {
        StringBuilder sb = new StringBuilder();
        for (ErrorGroup g : r.errorGroups()) {
            sb.append(g.normalizedMessage()).append('\n');
            g.representativeLines().forEach(l -> sb.append(l.text()).append('\n'));
            if (g.stackTrace() != null) {
                g.stackTrace().rawLines().forEach(l -> sb.append(l).append('\n'));
            }
        }
        return sb.toString();
    }
}
