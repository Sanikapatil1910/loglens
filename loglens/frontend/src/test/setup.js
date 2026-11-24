import '@testing-library/jest-dom/vitest';

// jsdom has no ResizeObserver; Recharts expects one.
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};
