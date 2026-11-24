import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatPercent, simpleName, truncate } from '../utils/format.js';
import { LEVEL_COLORS } from '../utils/levels.js';
import LevelBadge from './LevelBadge.jsx';

const AXIS = { fontSize: 12, fill: 'currentColor' };

function ChartCard({ title, children, empty }) {
  return (
    <section className="card chart-card" aria-label={title}>
      <h3>{title}</h3>
      {empty ? <p className="muted">{empty}</p> : children}
    </section>
  );
}

export function LevelChart({ levelCounts }) {
  const hasData = levelCounts.some((l) => l.count > 0);
  return (
    <ChartCard title="Log level distribution" empty={hasData ? null : 'No log lines to chart.'}>
      <div className="chart-box">
        <ResponsiveContainer width="100%" height={230}>
          <BarChart data={levelCounts} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
            <XAxis dataKey="level" tick={AXIS} />
            <YAxis allowDecimals={false} tick={AXIS} />
            <Tooltip formatter={(value, _name, item) => [`${value} (${formatPercent(item.payload.percentage)})`, 'Records']} />
            <Bar dataKey="count" name="Records">
              {levelCounts.map((l) => <Cell key={l.level} fill={LEVEL_COLORS[l.level]} />)}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <ul className="legend">
        {levelCounts.map((l) => (
          <li key={l.level}>
            <LevelBadge level={l.level} /> {l.count} <span className="muted">({formatPercent(l.percentage)})</span>
          </li>
        ))}
      </ul>
    </ChartCard>
  );
}

export function TopExceptionsChart({ topExceptions }) {
  const data = topExceptions.map((e) => ({ ...e, label: simpleName(e.name) }));
  return (
    <ChartCard title="Top recurring exceptions" empty={data.length ? null : 'No exceptions were found.'}>
      <div className="chart-box">
        <ResponsiveContainer width="100%" height={Math.max(180, data.length * 36 + 40)}>
          <BarChart data={data} layout="vertical" margin={{ top: 4, right: 12, left: 8, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
            <XAxis type="number" allowDecimals={false} tick={AXIS} />
            <YAxis type="category" dataKey="label" width={150} tick={AXIS} />
            <Tooltip formatter={(value) => [value, 'Occurrences']} labelFormatter={(_l, p) => p?.[0]?.payload?.name ?? ''} />
            <Bar dataKey="count" name="Occurrences" fill="#ef4444" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  );
}

export function TopGroupsChart({ groups }) {
  const data = [...groups]
    .sort((a, b) => b.occurrenceCount - a.occurrenceCount)
    .slice(0, 5)
    .map((g) => ({
      label: truncate(`${simpleName(g.exceptionType)}: ${g.normalizedMessage}`, 34),
      full: `${g.exceptionType}: ${g.normalizedMessage}`,
      count: g.occurrenceCount,
    }));
  return (
    <ChartCard title="Most frequent error groups" empty={data.length ? null : 'No error groups were found.'}>
      <div className="chart-box">
        <ResponsiveContainer width="100%" height={Math.max(180, data.length * 40 + 40)}>
          <BarChart data={data} layout="vertical" margin={{ top: 4, right: 12, left: 8, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
            <XAxis type="number" allowDecimals={false} tick={AXIS} />
            <YAxis type="category" dataKey="label" width={190} tick={AXIS} />
            <Tooltip formatter={(value) => [value, 'Occurrences']} labelFormatter={(_l, p) => p?.[0]?.payload?.full ?? ''} />
            <Bar dataKey="count" name="Occurrences" fill="#f59e0b" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  );
}
