import { useEffect, useMemo, useState } from 'react';
import { LevelChart, TopExceptionsChart, TopGroupsChart } from '../components/Charts.jsx';
import ErrorGroupsTable from '../components/ErrorGroupsTable.jsx';
import ExportPanel from '../components/ExportPanel.jsx';
import FilterBar from '../components/FilterBar.jsx';
import PrivacyNotice from '../components/PrivacyNotice.jsx';
import StatusBanner from '../components/StatusBanner.jsx';
import SummaryCards from '../components/SummaryCards.jsx';
import UploadPanel from '../components/UploadPanel.jsx';
import { useAnalysis } from '../hooks/useAnalysis.js';
import { DEFAULT_FILTERS, filterGroups, getExceptionTypes, hasActiveFilters } from '../utils/filters.js';

export default function Dashboard() {
  const { status, result, error, uploadPercent, analyzeFile, loadSample } = useAnalysis();
  const [redact, setRedact] = useState(true);
  const [filters, setFilters] = useState({ ...DEFAULT_FILTERS });

  // A new analysis starts with clean filters; filtering itself never re-uploads anything.
  useEffect(() => {
    setFilters({ ...DEFAULT_FILTERS });
  }, [result]);

  const busy = status === 'uploading' || status === 'analyzing';
  const groups = result?.errorGroups ?? [];
  const visibleGroups = useMemo(() => filterGroups(groups, filters), [groups, filters]);
  const exceptionTypes = useMemo(() => getExceptionTypes(groups), [groups]);

  return (
    <div className="dashboard">
      <PrivacyNotice />
      <UploadPanel
        busy={busy}
        redact={redact}
        onRedactChange={setRedact}
        onAnalyzeFile={analyzeFile}
        onLoadSample={loadSample}
      />
      <StatusBanner status={status} uploadPercent={uploadPercent} error={error} />

      {!result && status === 'idle' && (
        <section className="card empty" aria-label="No analysis yet">
          <h2>No analysis yet</h2>
          <p>
            Upload a <code>.log</code> or <code>.txt</code> file, or click &ldquo;Try the sample log&rdquo; to see
            LogLens group repeated errors, trace root-cause candidates and suggest what to check.
          </p>
        </section>
      )}

      {result && (
        <div className="results" aria-busy={busy}>
          <div className="result-heading">
            <h2>Results for <span className="source-name">{result.summary.sourceName}</span></h2>
            {result.summary.sampleData && <span className="badge badge-sample">SAMPLE DATA &ndash; not real results</span>}
          </div>

          {result.warnings.length > 0 && (
            <div className="notice notice-warn" role="note" aria-label="Analysis notices">
              <ul>{result.warnings.map((w) => <li key={w}>{w}</li>)}</ul>
            </div>
          )}

          <SummaryCards
            summary={result.summary}
            uniqueErrorGroups={result.uniqueErrorGroups}
            unknownLineCount={result.unknownLineCount}
          />

          <div className="charts-grid">
            <LevelChart levelCounts={result.logLevelCounts} />
            <TopExceptionsChart topExceptions={result.topExceptions} />
            <TopGroupsChart groups={groups} />
          </div>

          <section aria-labelledby="groups-title" className="groups-section">
            <h2 id="groups-title">Error groups</h2>
            {groups.length === 0 ? (
              <p className="card empty">No errors or exceptions were detected in this log.</p>
            ) : (
              <>
                <FilterBar
                  filters={filters}
                  onChange={setFilters}
                  exceptionTypes={exceptionTypes}
                  onReset={() => setFilters({ ...DEFAULT_FILTERS })}
                  shown={visibleGroups.length}
                  total={groups.length}
                  canReset={hasActiveFilters(filters)}
                />
                {visibleGroups.length === 0 ? (
                  <p className="card empty">No error groups match the current filters.</p>
                ) : (
                  <ErrorGroupsTable groups={visibleGroups} />
                )}
              </>
            )}
          </section>

          <ExportPanel analysis={result} />
        </div>
      )}
    </div>
  );
}
