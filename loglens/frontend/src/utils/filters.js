export const DEFAULT_FILTERS = Object.freeze({
  search: '',
  level: 'ALL',
  exceptionType: 'ALL',
  minOccurrences: 1,
  sort: 'frequency-desc',
});

export const LEVEL_FILTER_OPTIONS = [
  { value: 'ALL', label: 'All levels' },
  { value: 'ERRORS', label: 'Errors only (ERROR + FATAL)' },
  { value: 'WARN', label: 'Warnings only' },
  { value: 'FATAL', label: 'FATAL' },
  { value: 'ERROR', label: 'ERROR' },
];

export const SORT_OPTIONS = [
  { value: 'frequency-desc', label: 'Most frequent first' },
  { value: 'frequency-asc', label: 'Least frequent first' },
  { value: 'first-occurrence', label: 'First seen (earliest first)' },
  { value: 'last-occurrence', label: 'Last seen (latest first)' },
];

function matchesLevel(group, level) {
  switch (level) {
    case 'ALL':
      return true;
    case 'ERRORS':
      return group.level === 'ERROR' || group.level === 'FATAL';
    default:
      return group.level === level;
  }
}

/** Lower-cased text that keyword search runs against. */
export function groupSearchText(group) {
  const parts = [group.exceptionType, group.normalizedMessage];
  if (group.rootCauseCandidate) parts.push(group.rootCauseCandidate.type, group.rootCauseCandidate.message);
  (group.representativeLines ?? []).forEach((l) => parts.push(l.text));
  (group.stackTrace?.relevantFrames ?? []).forEach((f) => parts.push(`${f.className}.${f.methodName}`, f.fileName));
  (group.suggestions ?? []).forEach((s) => parts.push(s.title, s.possibleCause));
  return parts.filter(Boolean).join('\n').toLowerCase();
}

export function getExceptionTypes(groups) {
  return [...new Set(groups.map((g) => g.exceptionType))].sort((a, b) => a.localeCompare(b));
}

export function sortGroups(groups, sort) {
  const byFirst = (a, b) => a.firstOccurrence.lineNumber - b.firstOccurrence.lineNumber;
  const copy = [...groups];
  switch (sort) {
    case 'frequency-asc':
      return copy.sort((a, b) => a.occurrenceCount - b.occurrenceCount || byFirst(a, b));
    case 'first-occurrence':
      return copy.sort(byFirst);
    case 'last-occurrence':
      return copy.sort((a, b) => b.lastOccurrence.lineNumber - a.lastOccurrence.lineNumber);
    case 'frequency-desc':
    default:
      return copy.sort((a, b) => b.occurrenceCount - a.occurrenceCount || byFirst(a, b));
  }
}

/** Pure function: filters and sorts the already-analysed groups without touching the server. */
export function filterGroups(groups, filters = {}) {
  const f = { ...DEFAULT_FILTERS, ...filters };
  const needle = String(f.search ?? '').trim().toLowerCase();
  const min = Number(f.minOccurrences) || 1;
  const matched = groups.filter(
    (g) =>
      matchesLevel(g, f.level) &&
      (f.exceptionType === 'ALL' || g.exceptionType === f.exceptionType) &&
      g.occurrenceCount >= min &&
      (!needle || groupSearchText(g).includes(needle)),
  );
  return sortGroups(matched, f.sort);
}

export function hasActiveFilters(filters) {
  const f = { ...DEFAULT_FILTERS, ...filters };
  return (
    String(f.search).trim() !== '' ||
    f.level !== 'ALL' ||
    f.exceptionType !== 'ALL' ||
    (Number(f.minOccurrences) || 1) > 1 ||
    f.sort !== DEFAULT_FILTERS.sort
  );
}
