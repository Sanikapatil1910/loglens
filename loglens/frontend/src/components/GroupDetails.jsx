export default function GroupDetails({ group }) {
  const trace = group.stackTrace;
  return (
    <div className="details">
      {group.rootCauseCandidate && (
        <section>
          <h4>Root-cause candidate (heuristic)</h4>
          <p>
            <code>{group.rootCauseCandidate.type}</code>
            {group.rootCauseCandidate.message ? <>: {group.rootCauseCandidate.message}</> : null}
          </p>
          {trace?.rootCauseExplanation && <p className="muted">{trace.rootCauseExplanation}</p>}
        </section>
      )}

      {trace?.causeChain?.length > 1 && (
        <section>
          <h4>Cause chain</h4>
          <ol>
            {trace.causeChain.map((c, i) => (
              <li key={`${c.type}-${i}`}>
                <code>{c.type}</code>{c.message ? <>: {c.message}</> : null}
              </li>
            ))}
          </ol>
        </section>
      )}

      {trace?.suppressedExceptions?.length > 0 && (
        <section>
          <h4>Suppressed exceptions</h4>
          <ul>
            {trace.suppressedExceptions.map((c, i) => (
              <li key={`${c.type}-${i}`}><code>{c.type}</code>{c.message ? <>: {c.message}</> : null}</li>
            ))}
          </ul>
        </section>
      )}

      {trace?.relevantFrames?.length > 0 && (
        <section>
          <h4>Relevant stack frames</h4>
          <div className="table-scroll">
            <table className="mini-table">
              <thead>
                <tr><th>Class</th><th>Method</th><th>File</th><th>Line</th><th>Origin</th></tr>
              </thead>
              <tbody>
                {trace.relevantFrames.map((f, i) => (
                  <tr key={`${f.className}.${f.methodName}.${i}`}>
                    <td><code>{f.className}</code></td>
                    <td><code>{f.methodName}</code></td>
                    <td>{f.fileName ?? '\u2014'}</td>
                    <td>{f.lineNumber ?? '\u2014'}</td>
                    <td>{f.applicationFrame ? 'application' : 'library / JDK'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {trace?.rawLines?.length > 0 && (
        <details>
          <summary>Stack trace lines{trace.truncated ? ' (truncated)' : ''}</summary>
          <pre className="log-block">{trace.rawLines.join('\n')}</pre>
        </details>
      )}

      <section>
        <h4>Representative log lines</h4>
        {group.representativeLines.map((l) => (
          <pre key={l.lineNumber} className="log-block"><span className="line-no">line {l.lineNumber}</span>{'  '}{l.text}</pre>
        ))}
      </section>

      <section>
        <h4>Troubleshooting suggestions</h4>
        {group.suggestions.map((s) => (
          <article key={s.ruleId} className="suggestion">
            <h5>{s.title}</h5>
            <p className="disclaimer">Possible explanation &mdash; not a confirmed diagnosis.</p>
            <p>{s.possibleCause}</p>
            {s.generic && <p className="muted">No specific rule was found for this error; these are general debugging steps.</p>}
            <ul>{s.suggestedChecks.map((c) => <li key={c}>{c}</li>)}</ul>
          </article>
        ))}
      </section>
    </div>
  );
}
