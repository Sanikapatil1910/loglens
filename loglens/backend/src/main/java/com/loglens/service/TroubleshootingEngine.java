package com.loglens.service;

import com.loglens.model.ExceptionInfo;
import com.loglens.model.RuleDescription;
import com.loglens.model.StackFrame;
import com.loglens.model.StackTraceInfo;
import com.loglens.model.Suggestion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

/**
 * Local, deterministic, rule-based troubleshooting. No machine learning and no network calls:
 * every suggestion comes from a rule listed in {@link #describeRules()}, and every one is
 * labelled as a possible explanation rather than a diagnosis.
 */
@Service
public class TroubleshootingEngine {

    public static final String DISCLAIMER =
            "This is a possible explanation based on local rules, not a confirmed diagnosis.";

    static final int MAX_SUGGESTIONS = 3;

    private record Context(Set<String> simpleTypes, String text) {
        boolean hasType(String... names) {
            for (String n : names) {
                if (simpleTypes.contains(n)) {
                    return true;
                }
            }
            return false;
        }

        boolean hasTypeContaining(String fragment) {
            return simpleTypes.stream().anyMatch(t -> t.contains(fragment));
        }

        boolean textHas(String... fragments) {
            for (String f : fragments) {
                if (text.contains(f)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Rule(RuleDescription description, Predicate<Context> matcher) {
    }

    private final List<Rule> rules = List.of(
            rule("null-pointer", "NullPointerException", "NullPointerException",
                    "Code may be accessing a method or field on a reference that is null.",
                    List.of("Inspect the reported source line and identify which reference is null.",
                            "Verify the object is initialised or injected before it is used.",
                            "Add appropriate null handling or validation where null is a legitimate value."),
                    c -> c.hasType("NullPointerException")),
            rule("connection-refused", "Connection refused", "ConnectException, or 'connection refused' in a message",
                    "The destination service may not be running, may not be listening on the expected port, "
                            + "or may be blocked by a firewall.",
                    List.of("Verify the target service is running.",
                            "Check the host and port in the connection configuration.",
                            "Test connectivity to that host and port from the machine running the application."),
                    c -> c.hasType("ConnectException") || c.textHas("connection refused")),
            rule("socket-timeout", "Network timeout", "SocketTimeoutException, or 'timed out' in a message",
                    "An operation did not complete within the configured timeout.",
                    List.of("Check network connectivity between the caller and the remote service.",
                            "Check how long the remote service takes to respond.",
                            "Review connect and read timeout settings."),
                    c -> c.hasType("SocketTimeoutException") || c.textHas("timed out")),
            rule("sql-exception", "Database error", "Exceptions with SQL in the name (SQLException and subclasses)",
                    "A database operation or database connection may have failed.",
                    List.of("Read the SQL exception message, SQL state and vendor error code.",
                            "Check database availability, credentials and connection pool settings.",
                            "Check that the schema matches what the code expects (tables, columns, migrations)."),
                    c -> c.hasTypeContaining("SQL")),
            rule("out-of-memory", "Out of memory", "OutOfMemoryError",
                    "The JVM may not have been able to allocate the memory it needed.",
                    List.of("Inspect memory usage around the time of the error.",
                            "Review heap configuration such as -Xmx and the container memory limit.",
                            "Look for memory leaks or unusually large data being loaded into memory."),
                    c -> c.hasType("OutOfMemoryError")),
            rule("class-not-found", "Missing class on the classpath", "ClassNotFoundException, NoClassDefFoundError",
                    "A required class may be missing from the runtime classpath.",
                    List.of("Verify the dependency that provides the class is declared and packaged.",
                            "Check how the application is packaged and which classpath it runs with.",
                            "For NoClassDefFoundError, also look for an earlier failure while initialising that class."),
                    c -> c.hasType("ClassNotFoundException", "NoClassDefFoundError")),
            rule("no-such-method", "Binary incompatibility", "NoSuchMethodError, NoSuchFieldError, AbstractMethodError",
                    "Code may have been compiled against a different library version than the one used at runtime.",
                    List.of("Compare dependency versions used at compile time and at runtime.",
                            "Look for duplicate or conflicting versions of the same library on the classpath."),
                    c -> c.hasType("NoSuchMethodError", "NoSuchFieldError", "AbstractMethodError")),
            rule("illegal-argument", "Illegal argument", "IllegalArgumentException",
                    "A method may have received an argument value it does not accept.",
                    List.of("Read the exception message to see which argument was rejected.",
                            "Trace where that value comes from and validate input earlier."),
                    c -> c.hasType("IllegalArgumentException")),
            rule("illegal-state", "Illegal state", "IllegalStateException",
                    "An operation may have been called when the object was not in a state that allows it.",
                    List.of("Read the exception message to see which state was expected.",
                            "Check the order of initialisation and lifecycle calls."),
                    c -> c.hasType("IllegalStateException")),
            rule("index-out-of-bounds", "Index out of bounds",
                    "ArrayIndexOutOfBoundsException, IndexOutOfBoundsException, StringIndexOutOfBoundsException",
                    "Code may be using an index outside the valid range of an array, list or string.",
                    List.of("Compare the index and length reported in the message.",
                            "Check loop bounds and empty-collection cases at the reported source line."),
                    c -> c.hasType("ArrayIndexOutOfBoundsException", "IndexOutOfBoundsException",
                            "StringIndexOutOfBoundsException")),
            rule("number-format", "Invalid number format", "NumberFormatException",
                    "A string that is not a valid number may have been parsed as one.",
                    List.of("Check the value quoted in the message, including whitespace and empty strings.",
                            "Validate or sanitise input before parsing."),
                    c -> c.hasType("NumberFormatException")),
            rule("class-cast", "Invalid type cast", "ClassCastException",
                    "An object may have been cast to a type it is not an instance of.",
                    List.of("Compare the two class names in the message.",
                            "Check generic collections and deserialisation code for unexpected types."),
                    c -> c.hasType("ClassCastException")),
            rule("stack-overflow", "Stack overflow", "StackOverflowError",
                    "Very deep or unbounded recursion may have exhausted the thread stack.",
                    List.of("Look for repeating frames in the stack trace to find the recursive call.",
                            "Check termination conditions and cyclic object references."),
                    c -> c.hasType("StackOverflowError")),
            rule("file-not-found", "File not found", "FileNotFoundException, NoSuchFileException",
                    "A file or directory may be missing, misnamed, or not accessible to the process.",
                    List.of("Check the path in the message, including relative-path assumptions.",
                            "Check file permissions and the working directory of the process."),
                    c -> c.hasType("FileNotFoundException", "NoSuchFileException")));

    public List<RuleDescription> describeRules() {
        return rules.stream().map(Rule::description).toList();
    }

    /**
     * @param trace      parsed stack trace, or null when the error has no exception
     * @param logMessage the log message text of the error
     */
    public List<Suggestion> suggest(StackTraceInfo trace, String logMessage) {
        Context full = fullContext(trace, logMessage);
        Context root = trace == null ? full : contextOf(List.of(trace.rootCauseCandidate()), "");

        List<Rule> matched = new ArrayList<>();
        for (Rule r : rules) {
            if (r.matcher().test(root)) {
                matched.add(r);
            }
        }
        for (Rule r : rules) {
            if (!matched.contains(r) && r.matcher().test(full)) {
                matched.add(r);
            }
        }

        List<Suggestion> result = new ArrayList<>();
        for (Rule r : matched) {
            if (result.size() == MAX_SUGGESTIONS) {
                break;
            }
            RuleDescription d = r.description();
            result.add(new Suggestion(d.id(), d.title(), d.possibleCause(), d.suggestedChecks(), false, DISCLAIMER));
        }
        if (result.isEmpty()) {
            result.add(genericSuggestion());
        }
        return withTopFrameHint(result, trace);
    }

    private static Suggestion genericSuggestion() {
        return new Suggestion("no-specific-rule", "No specific rule matched",
                "No local rule matched this error, so no likely cause is proposed.",
                List.of("Read the full message and the deepest 'Caused by' entry, if there is one.",
                        "Start from the first application frame in the stack trace, if there is one.",
                        "Check what changed recently (deployments, configuration, dependencies, data).",
                        "Try to reproduce the error with the surrounding log lines as context."),
                true, DISCLAIMER);
    }

    private static List<Suggestion> withTopFrameHint(List<Suggestion> suggestions, StackTraceInfo trace) {
        if (trace == null) {
            return suggestions;
        }
        StackFrame top = trace.topFrame().orElse(null);
        if (top == null) {
            return suggestions;
        }
        Suggestion first = suggestions.get(0);
        List<String> checks = new ArrayList<>(first.suggestedChecks());
        checks.add("Start at the top relevant frame: " + top.displayName());
        List<Suggestion> updated = new ArrayList<>(suggestions);
        updated.set(0, new Suggestion(first.ruleId(), first.title(), first.possibleCause(),
                checks, first.generic(), first.disclaimer()));
        return updated;
    }

    private static Context fullContext(StackTraceInfo trace, String logMessage) {
        if (trace == null) {
            return contextOf(List.of(), logMessage);
        }
        List<ExceptionInfo> all = new ArrayList<>(trace.causeChain());
        all.addAll(trace.suppressedExceptions());
        return contextOf(all, logMessage);
    }

    private static Context contextOf(List<ExceptionInfo> infos, String extraText) {
        Set<String> types = new HashSet<>();
        StringBuilder text = new StringBuilder();
        for (ExceptionInfo info : infos) {
            types.add(info.simpleName());
            text.append(info.message()).append(' ');
        }
        if (extraText != null) {
            text.append(extraText);
        }
        return new Context(types, text.toString().toLowerCase(Locale.ROOT));
    }

    private static Rule rule(String id, String title, String appliesTo, String cause,
                             List<String> checks, Predicate<Context> matcher) {
        return new Rule(new RuleDescription(id, title, appliesTo, cause, checks), matcher);
    }
}
