package com.loglens.parser;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Replaces values that change between otherwise identical errors (timestamps, UUIDs,
 * memory addresses, request/trace ids, very long numbers) with placeholders.
 *
 * Deliberately NOT normalised: hosts, IP addresses, ports, short numbers, and file or class
 * names, because they usually distinguish genuinely different errors
 * (e.g. "Connection refused: localhost:5432" vs "localhost:6379").
 */
@Component
public class MessageNormalizer {

    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?");
    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern HEX_ADDRESS = Pattern.compile("\\b0[xX][0-9a-fA-F]{4,}\\b");
    private static final Pattern IDENTITY_HASH = Pattern.compile("(?<=[\\w$])@[0-9a-fA-F]{5,}\\b");
    private static final Pattern ID_PAIR = Pattern.compile(
            "(?i)\\b(request[_-]?id|req[_-]?id|trace[_-]?id|span[_-]?id|correlation[_-]?id|session[_-]?id"
                    + "|transaction[_-]?id|txn?[_-]?id|thread[_-]?id|user[_-]?id|order[_-]?id|id)"
                    + "\\s*[=:]\\s*[\\w\\-.]+");
    private static final Pattern NUMBERED_WORKER = Pattern.compile("(?i)\\b(thread|exec|worker|task)-\\d+\\b");
    private static final Pattern POOL_THREAD = Pattern.compile("\\bpool-\\d+-thread-\\d+\\b");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{6,}\\b");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String normalize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String s = TIMESTAMP.matcher(message).replaceAll("<TS>");
        s = UUID.matcher(s).replaceAll("<UUID>");
        s = HEX_ADDRESS.matcher(s).replaceAll("<ADDR>");
        s = IDENTITY_HASH.matcher(s).replaceAll("@<HASH>");
        s = ID_PAIR.matcher(s).replaceAll("$1=<ID>");
        s = POOL_THREAD.matcher(s).replaceAll("pool-<N>-thread-<N>");
        s = NUMBERED_WORKER.matcher(s).replaceAll("$1-<N>");
        s = LONG_NUMBER.matcher(s).replaceAll("<NUM>");
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }
}
