import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { sampleAnalysis } from '../test/fixtures.js';
import { analyzeFile, fetchSample } from '../services/api.js';
import { downloadReport } from '../utils/report.js';
import Dashboard from './Dashboard.jsx';

vi.mock('../services/api.js', () => ({
  analyzeFile: vi.fn(),
  fetchSample: vi.fn(),
}));

vi.mock('../utils/report.js', async (importOriginal) => ({
  ...(await importOriginal()),
  downloadReport: vi.fn(),
}));

// Recharts needs real layout sizes, which jsdom does not have; the charts are fed real data
// from the same props, so replacing them with plain containers keeps the page tests meaningful.
vi.mock('recharts', async () => {
  const React = await import('react');
  const Pass = ({ children }) => React.createElement('div', null, children);
  const Nothing = () => null;
  return {
    ResponsiveContainer: Pass, BarChart: Pass, Bar: Pass,
    Cell: Nothing, XAxis: Nothing, YAxis: Nothing, Tooltip: Nothing, CartesianGrid: Nothing,
  };
});

const logFile = () => new File(['2026-10-22 10:00:00 ERROR boom'], 'app.log', { type: 'text/plain' });

async function uploadAndAnalyze(user) {
  await user.upload(screen.getByLabelText(/choose file/i), logFile());
  await user.click(screen.getByRole('button', { name: /analyze file/i }));
}

describe('Dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('starts with an empty state and no results', () => {
    render(<Dashboard />);
    expect(screen.getByRole('heading', { name: /no analysis yet/i })).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('uploads a file and renders summary numbers and error groups from the response', async () => {
    analyzeFile.mockResolvedValue(sampleAnalysis);
    const user = userEvent.setup();
    render(<Dashboard />);
    await uploadAndAnalyze(user);

    expect(await screen.findByRole('heading', { name: /results for/i })).toHaveTextContent('app.log');
    expect(analyzeFile).toHaveBeenCalledWith(expect.any(File), expect.objectContaining({ redact: true }));

    expect(screen.getByLabelText('Total lines')).toHaveTextContent('20');
    expect(screen.getByLabelText('Errors')).toHaveTextContent('5');
    expect(screen.getByLabelText('Unique error groups')).toHaveTextContent('3');
    expect(screen.getByLabelText('Most frequent exception')).toHaveTextContent('java.lang.NullPointerException');

    const table = screen.getByRole('table');
    expect(within(table).getByText('java.lang.NullPointerException')).toBeInTheDocument();
    expect(within(table).getByText('java.net.ConnectException')).toBeInTheDocument();
    expect(within(table).getAllByRole('row')).toHaveLength(4); // header + 3 groups
  });

  it('shows a labelled sample-data badge for the demo log', async () => {
    fetchSample.mockResolvedValue({
      analysis: { ...sampleAnalysis, summary: { ...sampleAnalysis.summary, sampleData: true, sourceName: 'sample-application.log (sample data)' } },
    });
    const user = userEvent.setup();
    render(<Dashboard />);
    await user.click(screen.getByRole('button', { name: /try the sample log/i }));
    expect(await screen.findByText(/sample data . not real results/i)).toBeInTheDocument();
  });

  it('shows a clear error and keeps the page usable when the analysis fails', async () => {
    analyzeFile.mockRejectedValue(new Error('The uploaded file is empty.'));
    const user = userEvent.setup();
    render(<Dashboard />);
    await uploadAndAnalyze(user);

    expect(await screen.findByText(/analysis failed/i)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('The uploaded file is empty.');
    expect(screen.getByRole('button', { name: /analyze file/i })).toBeEnabled();
  });

  it('filters the analysed groups without uploading again', async () => {
    analyzeFile.mockResolvedValue(sampleAnalysis);
    const user = userEvent.setup();
    render(<Dashboard />);
    await uploadAndAnalyze(user);
    await screen.findByRole('table');

    await user.type(screen.getByLabelText('Search'), 'refused');
    let table = screen.getByRole('table');
    expect(within(table).getAllByRole('row')).toHaveLength(2);
    expect(within(table).getByText('java.net.ConnectException')).toBeInTheDocument();
    expect(screen.getByText(/showing 1 of 3 error groups/i)).toBeInTheDocument();

    await user.clear(screen.getByLabelText('Search'));
    await user.selectOptions(screen.getByLabelText('Level'), 'WARN');
    table = screen.getByRole('table');
    expect(within(table).getByText('java.io.IOException')).toBeInTheDocument();
    expect(within(table).getAllByRole('row')).toHaveLength(2);

    await user.type(screen.getByLabelText('Search'), 'zzz-nothing');
    expect(screen.getByText(/no error groups match the current filters/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /reset filters/i }));
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(4);
    expect(analyzeFile).toHaveBeenCalledTimes(1);
  });

  it('sorts groups by frequency', async () => {
    analyzeFile.mockResolvedValue(sampleAnalysis);
    const user = userEvent.setup();
    render(<Dashboard />);
    await uploadAndAnalyze(user);
    await screen.findByRole('table');

    await user.selectOptions(screen.getByLabelText('Sort by'), 'frequency-asc');
    const rows = within(screen.getByRole('table')).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('java.io.IOException');
  });

  it('expands a group to show its stack trace details and suggestions', async () => {
    analyzeFile.mockResolvedValue(sampleAnalysis);
    const user = userEvent.setup();
    render(<Dashboard />);
    await uploadAndAnalyze(user);
    await screen.findByRole('table');

    const toggle = screen.getByRole('button', { name: /show details for java\.lang\.NullPointerException/i });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText(/root-cause candidate \(heuristic\)/i)).toBeInTheDocument();
    expect(screen.getByText('UserService.java')).toBeInTheDocument();
    expect(screen.getByText(/code may be accessing a method or field on a reference that is null/i)).toBeInTheDocument();
    expect(screen.getByText(/not a confirmed diagnosis/i)).toBeInTheDocument();

    await user.click(toggle);
    expect(screen.queryByText(/root-cause candidate \(heuristic\)/i)).not.toBeInTheDocument();
  });

  it('exports the current analysis as a JSON report', async () => {
    analyzeFile.mockResolvedValue(sampleAnalysis);
    const user = userEvent.setup();
    render(<Dashboard />);
    await uploadAndAnalyze(user);
    await user.click(await screen.findByRole('button', { name: /export json report/i }));
    expect(downloadReport).toHaveBeenCalledWith(sampleAnalysis);
  });

  it('says so when no errors were found', async () => {
    analyzeFile.mockResolvedValue({
      ...sampleAnalysis,
      errorGroups: [],
      uniqueErrorGroups: 0,
      topExceptions: [],
      summary: { ...sampleAnalysis.summary, uniqueErrorGroups: 0, mostFrequentException: undefined, mostFrequentErrorMessage: undefined },
    });
    const user = userEvent.setup();
    render(<Dashboard />);
    await uploadAndAnalyze(user);
    expect(await screen.findByText(/no errors or exceptions were detected/i)).toBeInTheDocument();
  });
});
