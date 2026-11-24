import { useCallback, useRef, useState } from 'react';
import { analyzeFile as apiAnalyzeFile, fetchSample } from '../services/api.js';

const INITIAL = { status: 'idle', result: null, error: null, uploadPercent: null };

/**
 * Owns the analysis lifecycle: idle -> uploading -> analyzing -> success | error.
 * The previous result is kept while a new analysis runs, and after a failed one.
 */
export function useAnalysis() {
  const [state, setState] = useState(INITIAL);
  const latest = useRef(0);

  const analyzeFile = useCallback(async (file, redact = true) => {
    const id = ++latest.current;
    setState((s) => ({ ...s, status: 'uploading', error: null, uploadPercent: 0 }));
    try {
      const result = await apiAnalyzeFile(file, {
        redact,
        onUploadProgress: (percent) => {
          if (id !== latest.current) return;
          setState((s) =>
            s.status === 'uploading' && percent >= 100
              ? { ...s, status: 'analyzing', uploadPercent: 100 }
              : { ...s, uploadPercent: percent },
          );
        },
      });
      if (id === latest.current) setState({ status: 'success', result, error: null, uploadPercent: null });
    } catch (err) {
      if (id === latest.current) {
        setState((s) => ({ ...s, status: 'error', error: err, uploadPercent: null }));
      }
    }
  }, []);

  const loadSample = useCallback(async () => {
    const id = ++latest.current;
    setState((s) => ({ ...s, status: 'analyzing', error: null, uploadPercent: null }));
    try {
      const data = await fetchSample();
      if (id === latest.current) {
        setState({ status: 'success', result: data.analysis, error: null, uploadPercent: null });
      }
    } catch (err) {
      if (id === latest.current) {
        setState((s) => ({ ...s, status: 'error', error: err, uploadPercent: null }));
      }
    }
  }, []);

  return { ...state, analyzeFile, loadSample };
}
