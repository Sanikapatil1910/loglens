import { downloadReport } from '../utils/report.js';

export default function ExportPanel({ analysis }) {
  return (
    <section className="card" aria-labelledby="export-title">
      <h2 id="export-title">Export report</h2>
      <p>
        The JSON report contains the summary, level counts, error groups with occurrence counts, root-cause
        candidates, suggestions and up to three representative log lines per group. The full log and raw
        stack-trace lines are <strong>not</strong> included.
        {analysis.summary.redactionApplied
          ? ' Obvious secrets were masked (best effort).'
          : ' Masking was turned off, so representative lines may contain sensitive values.'}
      </p>
      <button type="button" className="button primary" onClick={() => downloadReport(analysis)}>
        Export JSON report
      </button>
    </section>
  );
}
