export default function StatusBanner({ status, uploadPercent, error }) {
  if (status === 'uploading') {
    const known = Number.isFinite(uploadPercent);
    return (
      <div className="status" role="status">
        <progress max="100" value={known ? uploadPercent : undefined} aria-label="Upload progress" />
        <span>{known ? `Uploading file\u2026 ${uploadPercent}%` : 'Uploading file\u2026'}</span>
      </div>
    );
  }
  if (status === 'analyzing') {
    return (
      <div className="status" role="status">
        <span className="spinner" aria-hidden="true" />
        <span>Analyzing log&hellip;</span>
      </div>
    );
  }
  if (status === 'error' && error) {
    return (
      <div className="status status-error" role="alert">
        <strong>Analysis failed.</strong> {error.message}
        {error.details?.length > 0 && (
          <ul>{error.details.map((d) => <li key={d}>{d}</li>)}</ul>
        )}
      </div>
    );
  }
  return null;
}
