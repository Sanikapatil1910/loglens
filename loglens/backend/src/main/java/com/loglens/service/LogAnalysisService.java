package com.loglens.service;

import com.loglens.dto.AnalysisResponse;
import com.loglens.dto.AnalysisSummary;
import com.loglens.dto.FrequentItem;
import com.loglens.dto.LevelCount;
import com.loglens.model.ErrorGroup;
import com.loglens.model.LogEntry;
import com.loglens.model.LogLevel;
import com.loglens.parser.LogLineParser;
import com.loglens.parser.ParseResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates parsing, grouping and statistics. Works purely in memory: nothing is stored,
 * and only counts (never log content) are written to the application log.
 */
@Service
public class LogAnalysisService {

    public static final String PRIVACY_NOTE =
            "Logs can contain passwords, tokens, e-mail addresses, IP addresses and other sensitive data. "
                    + "This analysis ran on your own backend and nothing was stored. Redaction is a best-effort "
                    + "pattern filter and does not guarantee that every secret is hidden.";

    static final int MAX_TOP_EXCEPTIONS = 10;

    private static final Logger log = LoggerFactory.getLogger(LogAnalysisService.class);

    private final LogLineParser parser;
    private final ErrorGroupingService groupingService;
    private final RedactionService redactionService;

    public LogAnalysisService(LogLineParser parser,
                              ErrorGroupingService groupingService,
                              RedactionService redactionService) {
        this.parser = parser;
        this.groupingService = groupingService;
        this.redactionService = redactionService;
    }

    public AnalysisResponse analyze(String text, String sourceName, boolean redact) {
        return doAnalyze(text, sourceName, redact, false);
    }

    public AnalysisResponse analyzeSample(String content) {
        return doAnalyze(content, SampleLogService.FILENAME + " (sample data)", true, true);
    }

    private AnalysisResponse doAnalyze(String text, String sourceName, boolean redact, boolean sample) {
        ParseResult parsed = parser.parseText(text);
        List<LogEntry> entries = parsed.entries();

        Map<LogLevel, Long> counts = new EnumMap<>(LogLevel.class);
        for (LogLevel level : LogLevel.values()) {
            counts.put(level, 0L);
        }
        for (LogEntry entry : entries) {
            counts.merge(entry.level(), 1L, Long::sum);
        }
        counts.merge(LogLevel.UNKNOWN, (long) parsed.unknownLines(), Long::sum);

        long records = entries.size() + (long) parsed.unknownLines();
        List<LevelCount> levelCounts = new ArrayList<>();
        for (LogLevel level : LogLevel.values()) {
            long count = counts.get(level);
            double pct = records == 0 ? 0.0 : Math.round(count * 10_000.0 / records) / 100.0;
            levelCounts.add(new LevelCount(level, count, pct));
        }

        UnaryOperator<String> sanitizer = redact ? redactionService::redact : UnaryOperator.identity();
        List<ErrorGroup> groups = groupingService.group(entries, sanitizer);

        List<FrequentItem> exceptions = exceptionTotals(groups);
        FrequentItem topMessage = groups.stream()
                .max(Comparator.comparingLong(ErrorGroup::occurrenceCount))
                .map(g -> new FrequentItem(g.normalizedMessage(), g.occurrenceCount()))
                .orElse(null);

        AnalysisSummary summary = new AnalysisSummary(
                sourceName, sample, Instant.now(), redact,
                parsed.totalLines(), parsed.parsedLines(), parsed.blankLines(), parsed.unknownLines(),
                counts.get(LogLevel.ERROR) + counts.get(LogLevel.FATAL),
                counts.get(LogLevel.WARN),
                counts.get(LogLevel.INFO),
                counts.get(LogLevel.DEBUG) + counts.get(LogLevel.TRACE),
                groups.size(),
                exceptions.isEmpty() ? null : exceptions.get(0),
                topMessage);

        log.info("Analysis finished: {} lines, {} entries, {} error groups",
                parsed.totalLines(), entries.size(), groups.size());

        return new AnalysisResponse(
                summary, levelCounts, groups.size(), groups,
                exceptions.size() > MAX_TOP_EXCEPTIONS ? exceptions.subList(0, MAX_TOP_EXCEPTIONS) : exceptions,
                buildWarnings(entries, parsed, redact),
                parsed.unknownLines(), PRIVACY_NOTE);
    }

    private static List<FrequentItem> exceptionTotals(List<ErrorGroup> groups) {
        Map<String, Long> totals = new HashMap<>();
        for (ErrorGroup g : groups) {
            if (!ErrorGroup.NO_EXCEPTION_TYPE.equals(g.exceptionType())) {
                totals.merge(g.exceptionType(), g.occurrenceCount(), Long::sum);
            }
        }
        return totals.entrySet().stream()
                .map(e -> new FrequentItem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(FrequentItem::count).reversed().thenComparing(FrequentItem::name))
                .toList();
    }

    private static List<String> buildWarnings(List<LogEntry> entries, ParseResult parsed, boolean redact) {
        List<String> warnings = new ArrayList<>();
        if (!redact) {
            warnings.add("Redaction is turned off: displayed lines and any exported report may contain sensitive values.");
        }
        if (entries.isEmpty()) {
            warnings.add("No log levels were recognised. Check that the log uses a supported format, "
                    + "for example '2026-10-22 10:30:12 ERROR message'.");
        }
        long badTimestamps = entries.stream()
                .filter(e -> e.timestampText() != null && e.timestamp() == null)
                .count();
        if (badTimestamps > 0) {
            warnings.add(badTimestamps + " entries had a timestamp that could not be parsed; they were still analysed.");
        }
        long nonBlank = parsed.totalLines() - parsed.blankLines();
        if (nonBlank > 0 && parsed.unknownLines() * 2L > nonBlank) {
            warnings.add("More than half of the lines could not be classified, so the results may be incomplete.");
        }
        return warnings;
    }
}
