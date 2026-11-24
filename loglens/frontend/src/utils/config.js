export const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
export const MAX_UPLOAD_BYTES = (Number(import.meta.env.VITE_MAX_UPLOAD_MB) || 10) * 1024 * 1024;
