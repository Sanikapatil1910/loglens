package com.loglens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loglens.model.LogEntry;
import com.loglens.model.RuleDescription;
import com.loglens.model.StackTraceInfo;
import com.loglens.model.Suggestion;
import com.loglens.parser.LogLineParser;
import com.loglens.parser.StackTraceParser;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class TroubleshootingEngineTest {

    private final LogLineParser lineParser = new LogLineParser();
    private final StackTraceParser traceParser = new StackTraceParser();
    private final TroubleshootingEngine engine = new TroubleshootingEngine();

    private List<Suggestion> suggestionsFor(String... lines) {
        LogEntry entry = lineParser.parseText(String.join("\n", lines)).entries().get(0);
        StackTraceInfo trace = traceParser.parse(entry).orElse(null);
        return engine.suggest(trace, entry.message());
    }

    private static List<String> ids(List<Suggestion> suggestions) {
        return suggestions.stream().map(Suggestion::ruleId).toList();
    }

    @Test
    void nullPointerRuleMatchesAndPointsAtTheSourceLine() {
        List<Suggestion> s = suggestionsFor(
                "2026-10-22 10:30:12 ERROR java.lang.NullPointerException at com.example.UserService.getUser(UserService.java:42)");
        assertEquals(List.of("null-pointer"), ids(s));
        assertTrue(s.get(0).suggestedChecks().stream().anyMatch(c -> c.contains("UserService.java:42")));
    }

    @Test
    void rootCauseRuleComesFirstAndWrapperRuleStillMatches() {
        List<Suggestion> s = suggestionsFor(
                "2026-10-22 10:30:12 ERROR Database failure",
                "java.sql.SQLTransientConnectionException: pool - Connection is not available",
                "\tat com.example.Db.open(Db.java:1)",
                "Caused by: java.net.ConnectException: Connection refused",
                "\tat java.base/sun.nio.ch.Net.connect(Net.java:1)");
        assertEquals(List.of("connection-refused", "sql-exception"), ids(s));
    }

    @Test
    void matchesOtherRequiredRules() {
        assertEquals(List.of("out-of-memory"),
                ids(suggestionsFor("ERROR java.lang.OutOfMemoryError: Java heap space")));
        assertEquals(List.of("class-not-found"),
                ids(suggestionsFor("ERROR java.lang.ClassNotFoundException: com.acme.Missing")));
        assertEquals(List.of("socket-timeout"),
                ids(suggestionsFor("ERROR java.net.SocketTimeoutException: Read timed out")));
        assertEquals(List.of("sql-exception"),
                ids(suggestionsFor("ERROR java.sql.SQLException: Table 'x' doesn't exist")));
    }

    @Test
    void unknownErrorsGetAGenericSuggestionWithoutInventedCauses() {
        List<Suggestion> s = suggestionsFor("ERROR com.acme.WeirdException: something strange");
        assertEquals(1, s.size());
        assertTrue(s.get(0).generic());
        assertEquals("no-specific-rule", s.get(0).ruleId());
        assertTrue(s.get(0).possibleCause().contains("no likely cause"));
    }

    @Test
    void errorsWithoutAnyExceptionAreHandledToo() {
        List<Suggestion> s = engine.suggest(null, "Something failed");
        assertEquals(1, s.size());
        assertTrue(s.get(0).generic());
    }

    @Test
    void everySuggestionIsLabelledAsPossibleNotConfirmed() {
        List<Suggestion> s = suggestionsFor("ERROR java.lang.NullPointerException: x");
        for (Suggestion suggestion : s) {
            assertTrue(suggestion.disclaimer().contains("not a confirmed diagnosis"));
        }
    }

    @Test
    void ruleCatalogueHasUniqueIdsAndCoversRequiredCategories() {
        List<RuleDescription> rules = engine.describeRules();
        assertFalse(rules.isEmpty());
        assertEquals(rules.size(), new HashSet<>(rules.stream().map(RuleDescription::id).toList()).size());
        List<String> ids = rules.stream().map(RuleDescription::id).toList();
        for (String required : List.of("null-pointer", "connection-refused", "socket-timeout",
                "sql-exception", "out-of-memory", "class-not-found")) {
            assertTrue(ids.contains(required), "missing rule " + required);
        }
    }
}
