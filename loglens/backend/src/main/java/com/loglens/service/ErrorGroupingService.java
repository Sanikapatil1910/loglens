package com.loglens.service;

import com.loglens.model.ErrorGroup;
import com.loglens.model.ExceptionInfo;
import com.loglens.model.LogEntry;
import com.loglens.model.LogLevel;
import com.loglens.model.LogLineRef;
import com.loglens.model.Occurrence;
import com.loglens.model.StackFrame;
import com.loglens.model.StackTraceInfo;
import com.loglens.model.Suggestion;
import com.loglens.parser.MessageNormalizer;
import com.loglens.parser.StackTraceParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;

/**
 * Groups similar errors. Eligible entries: ERROR and FATAL, plus WARN entries that carry an
 * exception. The group key combines exception type, normalised message, root-cause candidate
 * and top relevant stack frame, so different errors are not merged just because they share a type.
 */
@Service
public class ErrorGroupingService {

    static final int MAX_DISPLAY_MESSAGE = 300;
    static final int MAX_REPRESENTATIVE_LINE = 500;

    private final StackTraceParser stackTraceParser;
    private final MessageNormalizer normalizer;
    private final TroubleshootingEngine troubleshootingEngine;

    public ErrorGroupingService(StackTraceParser stackTraceParser,
                                MessageNormalizer normalizer,
                                TroubleshootingEngine troubleshootingEngine) {
        this.stackTraceParser = stackTraceParser;
        this.normalizer = normalizer;
        this.troubleshootingEngine = troubleshootingEngine;
    }

    public List<ErrorGroup> group(List<LogEntry> entries) {
        return group(entries, UnaryOperator.identity());
    }

    /**
     * @param sanitizer applied to every free-text field placed in the result (for redaction).
     *                  Group keys are computed from the original text, so redaction never merges groups.
     */
    public List<ErrorGroup> group(List<LogEntry> entries, UnaryOperator<String> sanitizer) {
        Map<String, Accumulator> groups = new LinkedHashMap<>();

        for (LogEntry entry : entries) {
            LogLevel level = entry.level();
            if (!level.isError() && level != LogLevel.WARN) {
                continue;
            }
            Optional<StackTraceInfo> trace = stackTraceParser.parse(entry);
            if (level == LogLevel.WARN && trace.isEmpty()) {
                continue;
            }
            String key = buildKey(entry, trace.orElse(null));
            groups.computeIfAbsent(key, k -> new Accumulator(k, entry, trace.orElse(null))).add(entry);
        }

        return groups.values().stream()
                .map(acc -> toGroup(acc, sanitizer))
                .sorted(Comparator.comparingLong(ErrorGroup::occurrenceCount).reversed()
                        .thenComparingInt(g -> g.firstOccurrence().lineNumber()))
                .toList();
    }

    String buildKey(LogEntry entry, StackTraceInfo trace) {
        if (trace == null) {
            return "L|" + entry.level() + "|" + normalizer.normalize(entry.message());
        }
        ExceptionInfo main = trace.mainException();
        StringBuilder key = new StringBuilder("E|")
                .append(main.type()).append('|').append(normalizer.normalize(main.message()));
        if (trace.causeChain().size() > 1) {
            ExceptionInfo root = trace.rootCauseCandidate();
            key.append("|R|").append(root.type()).append('|').append(normalizer.normalize(root.message()));
        }
        Optional<StackFrame> top = trace.topFrame();
        top.ifPresent(f -> key.append("|F|").append(f.className()).append('.').append(f.methodName())
                .append(':').append(f.lineNumber()));
        if (main.message().isBlank() && top.isEmpty()) {
            key.append("|M|").append(normalizer.normalize(entry.message()));
        }
        return key.toString();
    }

    private ErrorGroup toGroup(Accumulator acc, UnaryOperator<String> sanitizer) {
        StackTraceInfo trace = acc.trace;
        String exceptionType = trace == null ? ErrorGroup.NO_EXCEPTION_TYPE : trace.mainException().type();

        String sourceMessage = trace != null && !trace.mainException().message().isBlank()
                ? trace.mainException().message()
                : acc.first.message();
        String display = truncate(normalizer.normalize(sourceMessage), MAX_DISPLAY_MESSAGE);

        StackTraceInfo safeTrace = trace == null ? null : trace.withSanitizedText(sanitizer);
        ExceptionInfo root = safeTrace == null ? null : safeTrace.rootCauseCandidate();
        List<Suggestion> suggestions = troubleshootingEngine.suggest(trace, acc.first.message());

        return new ErrorGroup(
                stableId(acc.key),
                acc.level,
                exceptionType,
                sanitizer.apply(display),
                acc.count,
                new Occurrence(acc.first.lineNumber(), acc.first.timestampText()),
                new Occurrence(acc.last.lineNumber(), acc.last.timestampText()),
                root,
                safeTrace,
                representativeLines(acc, sanitizer),
                suggestions);
    }

    private static List<LogLineRef> representativeLines(Accumulator acc, UnaryOperator<String> sanitizer) {
        Map<Integer, LogEntry> picked = new LinkedHashMap<>();
        for (LogEntry e : acc.sample) {
            picked.put(e.lineNumber(), e);
        }
        picked.put(acc.last.lineNumber(), acc.last);
        List<LogLineRef> refs = new ArrayList<>();
        for (LogEntry e : picked.values()) {
            refs.add(new LogLineRef(e.lineNumber(),
                    sanitizer.apply(truncate(e.rawLine(), MAX_REPRESENTATIVE_LINE))));
        }
        return refs;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static String stableId(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Mutable per-group state; keeps only the first two entries and the last one. */
    private static final class Accumulator {
        final String key;
        final LogEntry first;
        final StackTraceInfo trace;
        final List<LogEntry> sample = new ArrayList<>(2);
        LogEntry last;
        LogLevel level;
        long count;

        Accumulator(String key, LogEntry first, StackTraceInfo trace) {
            this.key = key;
            this.first = first;
            this.trace = trace;
            this.last = first;
            this.level = first.level();
        }

        void add(LogEntry entry) {
            count++;
            last = entry;
            if (sample.size() < 2) {
                sample.add(entry);
            }
            if (entry.level().severity() > level.severity()) {
                level = entry.level();
            }
        }
    }
}
