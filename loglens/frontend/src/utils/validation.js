import { formatBytes } from './format.js';

export const DEFAULT_MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
export const ALLOWED_EXTENSIONS = ['log', 'txt'];

/**
 * Client-side check that mirrors the backend rules (the backend always re-validates).
 * Returns an error message, or null when the file looks acceptable.
 */
export function validateFile(file, maxBytes = DEFAULT_MAX_UPLOAD_BYTES) {
  if (!file) return 'Please choose a log file first.';
  const name = String(file.name ?? '');
  const dot = name.lastIndexOf('.');
  const extension = dot >= 0 && dot < name.length - 1 ? name.slice(dot + 1).toLowerCase() : '';
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return `Unsupported file type. Allowed types: ${ALLOWED_EXTENSIONS.map((e) => `.${e}`).join(', ')}.`;
  }
  if (file.size === 0) return 'The selected file is empty.';
  if (file.size > maxBytes) {
    return `The file is ${formatBytes(file.size)}; the maximum allowed size is ${formatBytes(maxBytes)}.`;
  }
  return null;
}
