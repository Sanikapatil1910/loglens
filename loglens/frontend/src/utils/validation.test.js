import { describe, expect, it } from 'vitest';
import { formatBytes, simpleName, truncate } from './format.js';
import { validateFile } from './validation.js';

const file = (name, size) => ({ name, size });

describe('validateFile', () => {
  it('accepts .log and .txt files in any letter case', () => {
    expect(validateFile(file('app.log', 100))).toBeNull();
    expect(validateFile(file('notes.TXT', 100))).toBeNull();
  });

  it('rejects missing, unsupported and extension-less files', () => {
    expect(validateFile(null)).toMatch(/choose a log file/i);
    expect(validateFile(file('malware.exe', 100))).toMatch(/unsupported file type/i);
    expect(validateFile(file('noextension', 100))).toMatch(/unsupported file type/i);
    expect(validateFile(file('trailingdot.', 100))).toMatch(/unsupported file type/i);
  });

  it('rejects empty and oversized files', () => {
    expect(validateFile(file('app.log', 0))).toMatch(/empty/i);
    expect(validateFile(file('app.log', 2049), 2048)).toMatch(/maximum allowed size/i);
    expect(validateFile(file('app.log', 2048), 2048)).toBeNull();
  });
});

describe('format helpers', () => {
  it('formats byte sizes', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
    expect(formatBytes(-1)).toBe('0 B');
  });

  it('shortens text and class names', () => {
    expect(truncate('abcdef', 4)).toBe('abc\u2026');
    expect(truncate('abc', 4)).toBe('abc');
    expect(simpleName('java.lang.NullPointerException')).toBe('NullPointerException');
    expect(simpleName('Plain')).toBe('Plain');
  });
});
