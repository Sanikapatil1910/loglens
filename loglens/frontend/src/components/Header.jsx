export default function Header({ theme, onToggleTheme }) {
  const next = theme === 'dark' ? 'light' : 'dark';
  return (
    <header className="site-header">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">&#128269;</span>
        <div>
          <h1>LogLens</h1>
          <p className="tagline">Smart log analyzer &amp; debugging assistant</p>
        </div>
      </div>
      <button type="button" className="button ghost" onClick={onToggleTheme} aria-label={`Switch to ${next} theme`}>
        {theme === 'dark' ? 'Light theme' : 'Dark theme'}
      </button>
    </header>
  );
}
