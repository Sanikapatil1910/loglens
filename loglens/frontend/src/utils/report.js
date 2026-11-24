const DATA_INCLUDED = [
  'Analysis summary and log-level counts',
  'Error groups with occurrence counts, first and last occurrence',
  'Root-cause candidates (heuristic) and cause chains',
  'Relevant stack frames (class, method, file, line)',
  'Troubleshooting suggestions (possible explanations only)',
  'Up to three representative log lines per group',
];

const DATA_EXCLUDED = [
  'The full original log',
  'Raw stack-trace lines',
  'Any uploaded file contents beyond the representative lines above',
];

/** Builds the exportable report object. Pure function, no side effects. */
export function buildReport(analysis, now = new Date()) {
  return {
    reportVersion: 1,
    generatedBy: 'LogLens',
    generatedAt: now.toISOString(),
    about: {
      sampleData: Boolean(analysis.summary?.sampleData),
      redactionApplied: Boolean(analysis.summary?.redactionApplied),
      dataIncluded: DATA_INCLUDED,
      dataExcluded: DATA_EXCLUDED,
      note:
        'Suggestions are possible explanations based on local rules, not confirmed diagnoses. ' +
        'Redaction is best-effort: review this file before sharing it.',
    },
    summary: analysis.summary,
    logLevelCounts: analysis.logLevelCounts,
    uniqueErrorGroups: analysis.uniqueErrorGroups,
    unknownLineCount: analysis.unknownLineCount,
    topExceptions: analysis.topExceptions ?? [],
    warnings: analysis.warnings ?? [],
    errorGroups: analysis.errorGroups.map((g) => ({
      id: g.id,
      level: g.level,
      exceptionType: g.exceptionType,
      normalizedMessage: g.normalizedMessage,
      occurrenceCount: g.occurrenceCount,
      firstOccurrence: g.firstOccurrence,
      lastOccurrence: g.lastOccurrence,
      rootCauseCandidate: g.rootCauseCandidate ?? null,
      rootCauseExplanation: g.stackTrace?.rootCauseExplanation ?? null,
      causeChain: g.stackTrace?.causeChain ?? [],
      relevantFrames: g.stackTrace?.relevantFrames ?? [],
      representativeLines: g.representativeLines ?? [],
      suggestions: g.suggestions ?? [],
    })),
  };
}

export function serializeReport(report) {
  return JSON.stringify(report, null, 2);
}

export function reportFilename(analysis, now = new Date()) {
  const slug =
    String(analysis.summary?.sourceName ?? 'log')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 40) || 'log';
  const stamp = now.toISOString().replace(/[-:]/g, '').replace('T', '-').slice(0, 15);
  return `loglens-report-${slug}-${stamp}.json`;
}

/** Triggers a browser download of the JSON report. */
export function downloadReport(analysis) {
  const json = serializeReport(buildReport(analysis));
  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = reportFilename(analysis);
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
