import { API_BASE } from '../utils/config.js';

const NETWORK_MESSAGE =
  'Cannot reach the LogLens backend. Make sure it is running (mvn spring-boot:run in the backend folder).';

export class ApiError extends Error {
  constructor(message, status = 0, details = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

function parseJson(text) {
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return null;
  }
}

function toApiError(status, body) {
  const message = body?.message || `The server responded with status ${status}.`;
  return new ApiError(message, status, body?.details ?? []);
}

async function request(path, options) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, options);
  } catch {
    throw new ApiError(NETWORK_MESSAGE, 0);
  }
  const body = parseJson(await response.text());
  if (!response.ok || body === null) throw toApiError(response.status, body);
  return body;
}

export function fetchHealth() {
  return request('/api/health');
}

export function fetchRules() {
  return request('/api/logs/error-rules');
}

/** Returns { filename, notice, content, analysis }. */
export function fetchSample() {
  return request('/api/logs/sample');
}

export function analyzeText(text, redact = true) {
  return request('/api/logs/analyze-text', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text, redact }),
  });
}

/**
 * Uploads a file with XMLHttpRequest so the browser can report real upload progress.
 * onUploadProgress receives an integer 0-100 that reflects bytes actually sent.
 */
export function analyzeFile(file, { redact = true, onUploadProgress, signal } = {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE}/api/logs/analyze`);
    xhr.responseType = 'text';

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onUploadProgress) {
        onUploadProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () => {
      const body = parseJson(xhr.responseText);
      if (xhr.status >= 200 && xhr.status < 300 && body) resolve(body);
      else reject(toApiError(xhr.status, body));
    };
    xhr.onerror = () => reject(new ApiError(NETWORK_MESSAGE, 0));
    xhr.onabort = () => reject(new ApiError('The upload was cancelled.', 0));
    if (signal) signal.addEventListener('abort', () => xhr.abort());

    const form = new FormData();
    form.append('file', file);
    form.append('redact', String(redact));
    xhr.send(form);
  });
}
