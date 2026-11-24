import { Fragment, useState } from 'react';
import { formatNumber, truncate } from '../utils/format.js';
import GroupDetails from './GroupDetails.jsx';
import LevelBadge from './LevelBadge.jsx';

function when(occurrence) {
  return occurrence.timestamp ? `line ${occurrence.lineNumber} \u00b7 ${occurrence.timestamp}` : `line ${occurrence.lineNumber}`;
}

export default function ErrorGroupsTable({ groups }) {
  const [expanded, setExpanded] = useState(() => new Set());

  function toggle(id) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  return (
    <div className="table-scroll">
      <table className="groups-table">
        <thead>
          <tr>
            <th scope="col"><span className="visually-hidden">Details</span></th>
            <th scope="col">Level</th>
            <th scope="col">Exception</th>
            <th scope="col">Message</th>
            <th scope="col" className="num">Count</th>
            <th scope="col">First seen</th>
            <th scope="col">Last seen</th>
          </tr>
        </thead>
        <tbody>
          {groups.map((g) => {
            const open = expanded.has(g.id);
            const panelId = `group-panel-${g.id}`;
            return (
              <Fragment key={g.id}>
              <tr className={open ? 'row-open' : undefined}>
                <td>
                  <button
                    type="button"
                    className="button ghost small"
                    aria-expanded={open}
                    aria-controls={panelId}
                    aria-label={`${open ? 'Hide' : 'Show'} details for ${g.exceptionType}`}
                    onClick={() => toggle(g.id)}
                  >
                    {open ? '\u25be Hide' : '\u25b8 Details'}
                  </button>
                </td>
                <td><LevelBadge level={g.level} /></td>
                <td><code>{g.exceptionType}</code></td>
                <td title={g.normalizedMessage}>{truncate(g.normalizedMessage, 110)}</td>
                <td className="num">{formatNumber(g.occurrenceCount)}</td>
                <td>{when(g.firstOccurrence)}</td>
                <td>{when(g.lastOccurrence)}</td>
              </tr>
              {open && (
                <tr className="details-row">
                  <td colSpan={7} id={panelId}>
                    <GroupDetails group={g} />
                  </td>
                </tr>
              )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
