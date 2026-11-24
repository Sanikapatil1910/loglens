package com.loglens.parser;

import com.loglens.model.LogEntry;
import com.loglens.model.LogLevel;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Splits raw log text into logical entries. A line with a recognised level starts a new
 * entry; stack-trace lines that follow are attached to it. Lines whose level cannot be
 * identified are counted as unknown instead of being guessed.
 */
@Component
public class LogLineParser {

    static final int MAX_LINE_LENGTH = 10_000;
    static final int MAX_CONTINUATION_LINES = 500;

    private static final String TS =
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?";

    /** [timestamp] [thread] LEVEL message, with every part except the level optional. */
    private static final Pattern LEVEL_LINE = Pattern.compile(
            "^\\s*(?:\\[?(?<ts>" + TS + ")\\]?[\\s|:-]*)?"
                    + "(?:\\[[^\\]]{1,80}\\]\\s+)?"
                    + "[\\[(]?(?<level>FATAL|SEVERE|ERROR|WARNING|WARN|INFO|DEBUG|TRACE)[\\])]?"
                    + "(?=[\\s:|\\-\\]]|$)"
                    + "[\\s:|-]*(?<msg>.*)$");

    private static final Pattern SPRING_BOOT_PREFIX = Pattern.compile("^\\d+\\s+---\\s+(?:\\[[^\\]]*\\]\\s*)+");
    private static final Pattern THREAD_PREFIX = Pattern.compile("^\\[[^\\]]{1,80}\\]\\s+");
    private static final Pattern LOGGER_PREFIX =
            Pattern.compile("^[A-Za-z_][\\w$]*(?:\\.[\\w$]+)+\\s+[:-]\\s+");

    private static final Pattern STACK_FRAME = Pattern.compile("^\\s+at\\s+\\S.*$");
    private static final Pattern CAUSED_BY = Pattern.compile("^\\s*Caused by:\\s*\\S.*$");
    private static final Pattern SUPPRESSED = Pattern.compile("^\\s*Suppressed:\\s*\\S.*$");
    private static final Pattern ELLIPSIS =
            Pattern.compile("^\\s*\\.\\.\\.\\s*\\d+\\s+(?:more|common frames omitted)\\s*$");
    private static final Pattern EXCEPTION_HEADER = Pattern.compile(
            "^\\s*(?:Exception in thread \"[^\"]*\"\\s+)?(?:[A-Za-z_][\\w$]*\\.)*[A-Z][\\w$]*"
                    + "(?:Exception|Error|Throwable)(?::\\s.*|:)?\\s*$");

    /** Result of matching a line that carries a log level. */
    public record LevelLine(String timestampText, LogLevel level, String message) {
    }

    public ParseResult parseText(String text) {
        if (text == null || text.isEmpty()) {
            return new ParseResult(List.of(), 0, 0, 0, 0);
        }
        return parse(text.lines()::iterator);
    }

    public ParseResult parse(Iterable<String> lines) {
        List<LogEntry> entries = new ArrayList<>();
        Pending current = null;
        int total = 0;
        int blank = 0;
        int continuation = 0;
        int unknown = 0;

        for (String rawLine : lines) {
            total++;
            String line = rawLine == null ? "" : rawLine;
            if (total == 1 && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }
            if (line.length() > MAX_LINE_LENGTH) {
                line = line.substring(0, MAX_LINE_LENGTH);
            }
            if (line.isBlank()) {
                blank++;
                continue;
            }

            Optional<LevelLine> levelLine = matchLevelLine(line);
            if (levelLine.isPresent()) {
                if (current != null) {
                    entries.add(current.toEntry());
                }
                current = new Pending(total, line, levelLine.get());
                continue;
            }
            if (current != null && isContinuation(line, current)) {
                if (current.continuation.size() < MAX_CONTINUATION_LINES) {
                    current.continuation.add(line);
                }
                continuation++;
                continue;
            }
            unknown++;
            if (current != null) {
                entries.add(current.toEntry());
                current = null;
            }
        }
        if (current != null) {
            entries.add(current.toEntry());
        }
        return new ParseResult(entries, total, blank, continuation, unknown);
    }

    public Optional<LevelLine> matchLevelLine(String line) {
        Matcher m = LEVEL_LINE.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        Optional<LogLevel> level = LogLevel.fromToken(m.group("level"));
        if (level.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LevelLine(m.group("ts"), level.get(), cleanMessage(m.group("msg"))));
    }

    /** Parses ISO-like timestamps; returns empty (never throws) for anything unparseable. */
    public static Optional<LocalDateTime> parseTimestamp(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String t = text.trim().replace(',', '.').replace(' ', 'T');
        t = t.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
        try {
            if (t.endsWith("Z") || t.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return Optional.of(OffsetDateTime.parse(t).toLocalDateTime());
            }
            return Optional.of(LocalDateTime.parse(t));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String cleanMessage(String message) {
        String m = message == null ? "" : message.trim();
        m = SPRING_BOOT_PREFIX.matcher(m).replaceFirst("");
        m = THREAD_PREFIX.matcher(m).replaceFirst("");
        m = LOGGER_PREFIX.matcher(m).replaceFirst("");
        return m.trim();
    }

    private static boolean isContinuation(String line, Pending current) {
        if (STACK_FRAME.matcher(line).matches()
                || CAUSED_BY.matcher(line).matches()
                || SUPPRESSED.matcher(line).matches()
                || ELLIPSIS.matcher(line).matches()) {
            return true;
        }
        if (EXCEPTION_HEADER.matcher(line).matches()) {
            return current.level == LogLevel.ERROR || current.level == LogLevel.FATAL
                    || current.level == LogLevel.WARN || !current.continuation.isEmpty();
        }
        return false;
    }

    private static final class Pending {
        final int lineNumber;
        final String rawLine;
        final LevelLine header;
        final LogLevel level;
        final List<String> continuation = new ArrayList<>();

        Pending(int lineNumber, String rawLine, LevelLine header) {
            this.lineNumber = lineNumber;
            this.rawLine = rawLine;
            this.header = header;
            this.level = header.level();
        }

        LogEntry toEntry() {
            LocalDateTime parsed = parseTimestamp(header.timestampText()).orElse(null);
            return new LogEntry(lineNumber, header.timestampText(), parsed, level,
                    header.message(), rawLine, continuation);
        }
    }
}
