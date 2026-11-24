import { describe, expect, it } from 'vitest';
import { sampleAnalysis } from '../test/fixtures.js';
import { DEFAULT_FILTERS, filterGroups, getExceptionTypes, hasActiveFilters } from './filters.js';

const groups = sampleAnalysis.errorGroups;
const ids = (list) => list.map((g) => g.id);

describe('filterGroups', () => {
  it('returns every group, most frequent first, with default filters', () => {
    expect(ids(filterGroups(groups, DEFAULT_FILTERS))).toEqual([
      'aaaa000000000001',
      'bbbb000000000002',
      'cccc000000000003',
    ]);
  });

  it('searches by keyword across type, message, frames and log lines', () => {
    expect(ids(filterGroups(groups, { search: 'refused' }))).toEqual(['bbbb000000000002']);
    expect(ids(filterGroups(groups, { search: 'userservice' }))).toEqual(['aaaa000000000001']);
    expect(ids(filterGroups(groups, { search: 'RETRYING' }))).toEqual(['cccc000000000003']);
    expect(filterGroups(groups, { search: 'no such text' })).toHaveLength(0);
  });

  it('filters by level, including "errors only" and "warnings only"', () => {
    expect(ids(filterGroups(groups, { level: 'ERRORS' }))).toEqual(['aaaa000000000001', 'bbbb000000000002']);
    expect(ids(filterGroups(groups, { level: 'WARN' }))).toEqual(['cccc000000000003']);
    expect(filterGroups(groups, { level: 'FATAL' })).toHaveLength(0);
  });

  it('filters by exception type and minimum occurrences', () => {
    expect(ids(filterGroups(groups, { exceptionType: 'java.net.ConnectException' }))).toEqual(['bbbb000000000002']);
    expect(ids(filterGroups(groups, { minOccurrences: 2 }))).toEqual(['aaaa000000000001', 'bbbb000000000002']);
    expect(ids(filterGroups(groups, { minOccurrences: '3' }))).toEqual(['aaaa000000000001']);
    expect(ids(filterGroups(groups, { minOccurrences: '' }))).toHaveLength(3);
  });

  it('sorts by frequency and by occurrence position', () => {
    expect(ids(filterGroups(groups, { sort: 'frequency-asc' }))).toEqual([
      'cccc000000000003',
      'bbbb000000000002',
      'aaaa000000000001',
    ]);
    expect(ids(filterGroups(groups, { sort: 'first-occurrence' }))[0]).toBe('aaaa000000000001');
    expect(ids(filterGroups(groups, { sort: 'last-occurrence' }))[0]).toBe('bbbb000000000002');
  });

  it('combines filters and never mutates its input', () => {
    const before = ids(groups);
    expect(ids(filterGroups(groups, { level: 'ERRORS', search: 'refused', minOccurrences: 2 }))).toEqual([
      'bbbb000000000002',
    ]);
    filterGroups(groups, { sort: 'frequency-asc' });
    expect(ids(groups)).toEqual(before);
  });
});

describe('helpers', () => {
  it('lists unique exception types alphabetically', () => {
    expect(getExceptionTypes([...groups, ...groups])).toEqual([
      'java.io.IOException',
      'java.lang.NullPointerException',
      'java.net.ConnectException',
    ]);
  });

  it('detects active filters', () => {
    expect(hasActiveFilters(DEFAULT_FILTERS)).toBe(false);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, search: ' x ' })).toBe(true);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, sort: 'frequency-asc' })).toBe(true);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, minOccurrences: 2 })).toBe(true);
  });
});
