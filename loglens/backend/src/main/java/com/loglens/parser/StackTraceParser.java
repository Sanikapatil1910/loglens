package com.loglens.parser;

import com.loglens.model.ExceptionInfo;
import com.loglens.model.LogEntry;
import com.loglens.model.StackFrame;
import com.loglens.model.StackTraceInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Extracts exception information from a {@link LogEntry}: the main exception, the
 * "Caused by" chain, suppressed exceptions and stack frames. Exception names are extracted
 * dynamically, so custom and unknown exception types work.
 */
@Component
public class StackTraceParser {

    static final int MAX_RELEVANT_FRAMES = 5;
    static final int MAX_RAW_LINES = 100;

    private static final List<String> FRAMEWORK_PREFIXES = List.of(
            "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.", "kotlin.", "scala.",
            "org.springframework.", "org.apache.", "org.hibernate.", "org.eclipse.jetty.",
            "org.junit.", "io.netty.", "reactor.", "com.zaxxer.hikari.");

    private static final Pattern FRAME = Pattern.compile(
            "^\\s*at\\s+(?:[A-Za-z][\\w.\\-]*(?:@[\\w.\\-]+)?/)?(?<cm>[\\w.$<>/]+)\\((?<loc>[^)]*)\\)(?:\\s.*)?$");
    private static final Pattern INLINE_FRAME =
            Pattern.compile("\\bat\\s+(?<cm>[\\w.$<>/]+)\\((?<loc>[^)]*)\\)");
    private static final Pattern ELLIPSIS =
            Pattern.compile("^\\s*\\.\\.\\.\\s*\\d+\\s+(?:more|common frames omitted)\\s*$");
    private static final Pattern CAUSED_BY = Pattern.compile("^\\s*Caused by:\\s*\\S.*$");
    private static final Pattern SUPPRESSED = Pattern.compile("^\\s*Suppressed:\\s*\\S.*$");
    private static final Pattern EXCEPTION_HEADER = Pattern.compile(
            "^\\s*(?:Exception in thread \"[^\"]*\"\\s+)?(?:[A-Za-z_][\\w$]*\\.)*[A-Z][\\w$]*"
                    + "(?:Exception|Error|Throwable)(?::\\s.*|:)?\\s*$");
    private static final Pattern THROWABLE_PREFIX =
            Pattern.compile("^\\s*(?:Caused by:|Suppressed:|Exception in thread \"[^\"]*\")\\s*");
    private static final Pattern THROWABLE_LINE =
            Pattern.compile("^(?<type>[A-Za-z_][\\w$]*(?:\\.[\\w$]+)*)(?::\\s*(?<msg>.*))?$");
    private static final Pattern TYPE_IN_TEXT = Pattern.compile(
            "\\b(?<type>(?:[a-z_][\\w$]*\\.)*[A-Z][\\w$]*(?:Exception|Error|Throwable))\\b(?:\\s*:\\s*(?<msg>.*))?");

    private static final class Node {
        final ExceptionInfo info;
        final List<StackFrame> frames = new ArrayList<>();

        Node(ExceptionInfo info) {
            this.info = info;
        }
    }

    /** Returns empty when the entry contains no recognisable exception or stack trace. */
    public Optional<StackTraceInfo> parse(LogEntry entry) {
        Node main = null;
        boolean mainFromHeaderLine = false;
        List<Node> causes = new ArrayList<>();
        List<ExceptionInfo> suppressed = new ArrayList<>();
        Node current = null;
        boolean inSuppressed = false;

        // 1. Exception mentioned on the log line itself (single-line errors).
        Matcher typeMatch = TYPE_IN_TEXT.matcher(entry.message());
        if (typeMatch.find()) {
            String message = typeMatch.group("msg") == null ? "" : typeMatch.group("msg");
            Matcher inline = INLINE_FRAME.matcher(entry.message());
            StackFrame inlineFrame = null;
            if (inline.find()) {
                inlineFrame = toFrame(inline);
                if (!message.isEmpty() && message.contains(inline.group())) {
                    message = message.substring(0, message.indexOf(inline.group())).trim();
                }
            }
            main = new Node(new ExceptionInfo(typeMatch.group("type"), message));
            if (inlineFrame != null) {
                main.frames.add(inlineFrame);
            }
            current = main;
        }

        // 2. Continuation lines: headers, frames, causes, suppressed.
        for (String line : entry.continuationLines()) {
            if (ELLIPSIS.matcher(line).matches()) {
                continue;
            }
            Matcher frame = FRAME.matcher(line);
            if (frame.matches()) {
                if (!inSuppressed) {
                    if (current == null) {
                        main = new Node(new ExceptionInfo(null, ""));
                        current = main;
                    }
                    current.frames.add(toFrame(frame));
                }
                continue;
            }
            boolean indented = Character.isWhitespace(line.charAt(0));
            boolean causedBy = CAUSED_BY.matcher(line).matches();
            if (SUPPRESSED.matcher(line).matches() || (causedBy && indented)) {
                suppressed.add(parseThrowableLine(line));
                inSuppressed = true;
            } else if (causedBy) {
                Node cause = new Node(parseThrowableLine(line));
                causes.add(cause);
                current = cause;
                inSuppressed = false;
            } else if (EXCEPTION_HEADER.matcher(line).matches()
                    && causes.isEmpty() && !mainFromHeaderLine && !inSuppressed) {
                Node header = new Node(parseThrowableLine(line));
                if (main != null) {
                    header.frames.addAll(main.frames);
                }
                main = header;
                mainFromHeaderLine = true;
                current = header;
            }
        }

        if (main == null && causes.isEmpty()) {
            return Optional.empty();
        }
        if (main == null) {
            main = new Node(new ExceptionInfo(null, ""));
        }

        List<Node> chain = new ArrayList<>();
        chain.add(main);
        chain.addAll(causes);
        Node root = chain.get(chain.size() - 1);

        String explanation = chain.size() > 1
                ? "Heuristic: the deepest 'Caused by' entry is treated as the root-cause candidate. "
                  + "It is a likely starting point, not a confirmed root cause."
                : "Heuristic: no 'Caused by' entries were found, so the main exception is the only candidate. "
                  + "It is not a confirmed root cause.";

        List<StackFrame> sourceFrames = root.frames.isEmpty() ? main.frames : root.frames;
        List<StackFrame> relevant = selectRelevantFrames(sourceFrames);

        List<String> raw = entry.continuationLines();
        boolean truncated = raw.size() > MAX_RAW_LINES;
        List<String> rawLines = truncated ? raw.subList(0, MAX_RAW_LINES) : raw;

        return Optional.of(new StackTraceInfo(
                main.info, root.info, explanation,
                chain.stream().map(n -> n.info).toList(),
                suppressed, relevant, rawLines, truncated));
    }

    static ExceptionInfo parseThrowableLine(String line) {
        String body = THROWABLE_PREFIX.matcher(line).replaceFirst("").trim();
        Matcher m = THROWABLE_LINE.matcher(body);
        if (m.matches()) {
            return new ExceptionInfo(m.group("type"), m.group("msg") == null ? "" : m.group("msg").trim());
        }
        return new ExceptionInfo(null, body);
    }

    private static List<StackFrame> selectRelevantFrames(List<StackFrame> frames) {
        List<StackFrame> app = frames.stream().filter(StackFrame::applicationFrame).toList();
        List<StackFrame> chosen = app.isEmpty() ? frames : app;
        return chosen.size() > MAX_RELEVANT_FRAMES ? chosen.subList(0, MAX_RELEVANT_FRAMES) : chosen;
    }

    private static StackFrame toFrame(Matcher m) {
        String classAndMethod = m.group("cm");
        int dot = classAndMethod.lastIndexOf('.');
        String className = dot > 0 ? classAndMethod.substring(0, dot) : classAndMethod;
        String methodName = dot > 0 ? classAndMethod.substring(dot + 1) : "?";

        String location = m.group("loc").trim();
        String file = null;
        Integer lineNumber = null;
        int colon = location.lastIndexOf(':');
        String afterColon = colon > 0 ? location.substring(colon + 1) : "";
        if (colon > 0 && afterColon.matches("\\d{1,9}")) {
            file = location.substring(0, colon);
            lineNumber = Integer.valueOf(afterColon);
        } else if (!location.isEmpty() && !location.equals("Native Method") && !location.equals("Unknown Source")) {
            file = location;
        }
        return new StackFrame(className, methodName, file, lineNumber, isApplicationClass(className));
    }

    private static boolean isApplicationClass(String className) {
        if (className.contains("$$")) {
            return false;
        }
        return FRAMEWORK_PREFIXES.stream().noneMatch(className::startsWith);
    }
}
