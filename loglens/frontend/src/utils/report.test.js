import { describe, expect, it } from 'vitest';
import { sampleAnalysis } from '../test/fixtures.js';
import { buildReport, reportFilename, serializeReport } from './report.js';

const NOW = new Date('2026-10-22T10:30:45Z');

describe('JSON report', () => {
  const report = buildReport(sampleAnalysis, NOW);

  it('serialises to valid JSON that round-trips', () => {
    const parsed = JSON.parse(serializeReport(report));
    expect(parsed.generatedBy).toBe('LogLens');
    expect(parsed.generatedAt).toBe('2026-10-22T10:30:45.000Z');
    expect(parsed.errorGroups).toHaveLength(3);
  });

  it('contains summary, level counts, groups, root causes, suggestions and representative lines', () => {
    expect(report.summary.totalLines).toBe(20);
    expect(report.logLevelCounts).toHaveLength(7);
    const first = report.errorGroups[0];
    expect(first.occurrenceCount).toBe(3);
    expect(first.rootCauseCandidate.type).toBe('java.lang.NullPointerException');
    expect(first.suggestions[0].ruleId).toBe('null-pointer');
    expect(first.representativeLines[0].lineNumber).toBe(2);
    expect(first.relevantFrames[0].lineNumber).toBe(42);
  });

  it('does not export raw stack-trace lines or the full log', () => {
    const text = serializeReport(report);
    expect(text).not.toContain('rawLines');
    expect(text).not.toContain('at com.example.UserService.getUser(UserService.java:42)');
    expect(report.about.dataExcluded.join(' ')).toMatch(/raw stack-trace lines/i);
  });

  it('records whether redaction was applied and whether the data is a sample', () => {
    expect(report.about.redactionApplied).toBe(true);
    expect(report.about.sampleData).toBe(false);
    const sample = buildReport({ ...sampleAnalysis, summary: { ...sampleAnalysis.summary, sampleData: true, redactionApplied: false } }, NOW);
    expect(sample.about.sampleData).toBe(true);
    expect(sample.about.redactionApplied).toBe(false);
  });

  it('builds a safe file name', () => {
    expect(reportFilename(sampleAnalysis, NOW)).toBe('loglens-report-app-log-20261022-103045.json');
    const odd = { summary: { sourceName: '../../Weird Name!!.log' } };
    expect(reportFilename(odd, NOW)).toBe('loglens-report-weird-name-log-20261022-103045.json');
  });
});
