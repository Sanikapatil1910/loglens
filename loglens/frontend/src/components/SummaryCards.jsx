import { formatNumber, truncate } from '../utils/format.js';

function Stat({ label, value, tone }) {
  return (
    <article className={`stat${tone ? ` stat-${tone}` : ''}`} aria-label={label}>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{formatNumber(value)}</span>
    </article>
  );
}

function WideStat({ label, item }) {
  return (
    <article className="stat stat-wide" aria-label={label}>
      <span className="stat-label">{label}</span>
      {item ? (
        <>
          <span className="stat-text" title={item.name}>{truncate(item.name, 90)}</span>
          <span className="stat-sub">{formatNumber(item.count)} occurrences</span>
        </>
      ) : (
        <span className="stat-sub">None found</span>
      )}
    </article>
  );
}

export default function SummaryCards({ summary, uniqueErrorGroups, unknownLineCount }) {
  return (
    <section aria-labelledby="summary-title">
      <h2 id="summary-title">Summary</h2>
      <div className="stats-grid">
        <Stat label="Total lines" value={summary.totalLines} />
        <Stat label="Parsed lines" value={summary.parsedLines} />
        <Stat label="Errors" value={summary.errorCount} tone="error" />
        <Stat label="Warnings" value={summary.warningCount} tone="warn" />
        <Stat label="Info" value={summary.infoCount} tone="info" />
        <Stat label="Debug / Trace" value={summary.debugTraceCount} />
        <Stat label="Unknown lines" value={unknownLineCount} />
        <Stat label="Unique error groups" value={uniqueErrorGroups} tone="error" />
        <WideStat label="Most frequent exception" item={summary.mostFrequentException} />
        <WideStat label="Most frequent error message" item={summary.mostFrequentErrorMessage} />
      </div>
    </section>
  );
}
