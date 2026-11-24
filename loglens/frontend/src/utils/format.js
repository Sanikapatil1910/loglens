export function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
}

export function formatPercent(value) {
  const n = Number(value);
  return `${(Number.isFinite(n) ? n : 0).toFixed(1)}%`;
}

export function formatNumber(value) {
  const n = Number(value);
  return (Number.isFinite(n) ? n : 0).toLocaleString('en-US');
}

export function truncate(text, max) {
  const s = String(text ?? '');
  return s.length <= max ? s : `${s.slice(0, Math.max(0, max - 1))}\u2026`;
}

/** "java.lang.NullPointerException" -> "NullPointerException" */
export function simpleName(type) {
  const s = String(type ?? '');
  const i = Math.max(s.lastIndexOf('.'), s.lastIndexOf('$'));
  return i >= 0 ? s.slice(i + 1) : s;
}
