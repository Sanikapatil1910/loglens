import { LEVEL_FILTER_OPTIONS, SORT_OPTIONS } from '../utils/filters.js';

export default function FilterBar({ filters, onChange, exceptionTypes, onReset, shown, total, canReset }) {
  const set = (patch) => onChange({ ...filters, ...patch });
  return (
    <section className="card" aria-labelledby="filter-title">
      <h2 id="filter-title">Search and filter</h2>
      <div className="filters">
        <div className="field">
          <label htmlFor="f-search">Search</label>
          <input
            id="f-search"
            type="search"
            placeholder="Keyword, class, message&hellip;"
            value={filters.search}
            onChange={(e) => set({ search: e.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="f-level">Level</label>
          <select id="f-level" value={filters.level} onChange={(e) => set({ level: e.target.value })}>
            {LEVEL_FILTER_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="f-type">Exception type</label>
          <select id="f-type" value={filters.exceptionType} onChange={(e) => set({ exceptionType: e.target.value })}>
            <option value="ALL">All exception types</option>
            {exceptionTypes.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <div className="field field-narrow">
          <label htmlFor="f-min">Min. occurrences</label>
          <input
            id="f-min"
            type="number"
            min="1"
            step="1"
            value={filters.minOccurrences}
            onChange={(e) => set({ minOccurrences: e.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="f-sort">Sort by</label>
          <select id="f-sort" value={filters.sort} onChange={(e) => set({ sort: e.target.value })}>
            {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </div>
      </div>
      <div className="filter-footer">
        <p aria-live="polite" className="muted">Showing {shown} of {total} error groups</p>
        <button type="button" className="button ghost" onClick={onReset} disabled={!canReset}>Reset filters</button>
      </div>
    </section>
  );
}
