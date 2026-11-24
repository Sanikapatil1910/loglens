package com.loglens.dto;

import com.loglens.model.ErrorGroup;
import java.util.List;

/**
 * Full analysis result.
 *
 * @param warnings analysis notices for the user (not the same as WARN log lines)
 */
public record AnalysisResponse(
        AnalysisSummary summary,
        List<LevelCount> logLevelCounts,
        int uniqueErrorGroups,
        List<ErrorGroup> errorGroups,
        List<FrequentItem> topExceptions,
        List<String> warnings,
        int unknownLineCount,
        String privacyNote) {
}
