import { useState } from 'react';
import { MAX_UPLOAD_BYTES } from '../utils/config.js';
import { formatBytes } from '../utils/format.js';
import { validateFile } from '../utils/validation.js';

export default function UploadPanel({ busy, redact, onRedactChange, onAnalyzeFile, onLoadSample }) {
  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);
  const [dragging, setDragging] = useState(false);

  function accept(candidate) {
    setFile(candidate);
    setError(validateFile(candidate, MAX_UPLOAD_BYTES));
  }

  function handleChange(event) {
    const chosen = event.target.files?.[0];
    if (chosen) accept(chosen);
  }

  function handleDrop(event) {
    event.preventDefault();
    setDragging(false);
    const dropped = event.dataTransfer?.files;
    if (!dropped || dropped.length === 0) return;
    if (dropped.length > 1) {
      setFile(null);
      setError('Please drop a single file.');
      return;
    }
    accept(dropped[0]);
  }

  const canAnalyze = Boolean(file) && !error && !busy;

  return (
    <section className="card" aria-labelledby="upload-title">
      <h2 id="upload-title">1. Choose a log file</h2>
      <div
        className={`dropzone${dragging ? ' dragging' : ''}`}
        data-testid="dropzone"
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
      >
        <p>Drag and drop a <code>.log</code> or <code>.txt</code> file here, or</p>
        <input
          id="log-file-input"
          className="visually-hidden"
          type="file"
          accept=".log,.txt,text/plain"
          onChange={handleChange}
          disabled={busy}
        />
        <label htmlFor="log-file-input" className="button secondary">Choose file</label>
        <p className="hint">Maximum size: {formatBytes(MAX_UPLOAD_BYTES)}</p>
      </div>

      {file && (
        <p className="file-meta" data-testid="selected-file">
          <strong>{file.name}</strong> <span>{formatBytes(file.size)}</span>
        </p>
      )}
      {error && <p role="alert" className="field-error">{error}</p>}

      <label className="checkbox">
        <input type="checkbox" checked={redact} onChange={(e) => onRedactChange(e.target.checked)} />
        Mask obvious passwords and tokens in the results (best effort)
      </label>

      <div className="actions">
        <button type="button" className="button primary" disabled={!canAnalyze} onClick={() => onAnalyzeFile(file, redact)}>
          Analyze file
        </button>
        <button type="button" className="button secondary" disabled={busy} onClick={onLoadSample}>
          Try the sample log
        </button>
      </div>
    </section>
  );
}
