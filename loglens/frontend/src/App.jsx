import { useEffect, useState } from 'react';
import Header from './components/Header.jsx';
import Dashboard from './pages/Dashboard.jsx';

const STORAGE_KEY = 'loglens-theme';

function readStoredTheme() {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return stored === 'light' ? 'light' : 'dark';
  } catch {
    return 'dark';
  }
}

export default function App() {
  const [theme, setTheme] = useState(readStoredTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      window.localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      /* storage may be unavailable; the theme still applies for this session */
    }
  }, [theme]);

  return (
    <div className="app">
      <Header theme={theme} onToggleTheme={() => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))} />
      <main>
        <Dashboard />
      </main>
      <footer className="site-footer">
        LogLens runs locally. Suggestions come from a deterministic rule set and are possible explanations,
        not confirmed diagnoses.
      </footer>
    </div>
  );
}
