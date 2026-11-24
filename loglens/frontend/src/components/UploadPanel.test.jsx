import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import UploadPanel from './UploadPanel.jsx';

function setup(props = {}) {
  const handlers = { onAnalyzeFile: vi.fn(), onLoadSample: vi.fn(), onRedactChange: vi.fn() };
  // applyAccept:false lets us test our own validation with files the browser filter would hide.
  const user = userEvent.setup({ applyAccept: false });
  render(<UploadPanel busy={false} redact={true} {...handlers} {...props} />);
  return { user, ...handlers };
}

const logFile = (name = 'app.log', size = 2048) => new File(['x'.repeat(size)], name, { type: 'text/plain' });

describe('UploadPanel', () => {
  it('shows the chosen file name and size and enables analysis', async () => {
    const { user, onAnalyzeFile } = setup();
    expect(screen.getByRole('button', { name: /analyze file/i })).toBeDisabled();

    const file = logFile();
    await user.upload(screen.getByLabelText(/choose file/i), file);

    expect(screen.getByTestId('selected-file')).toHaveTextContent('app.log');
    expect(screen.getByTestId('selected-file')).toHaveTextContent('2.0 KB');
    await user.click(screen.getByRole('button', { name: /analyze file/i }));
    expect(onAnalyzeFile).toHaveBeenCalledWith(file, true);
  });

  it('rejects unsupported file types before uploading', async () => {
    const { user, onAnalyzeFile } = setup();
    await user.upload(screen.getByLabelText(/choose file/i), logFile('malware.exe'));
    expect(screen.getByRole('alert')).toHaveTextContent(/unsupported file type/i);
    expect(screen.getByRole('button', { name: /analyze file/i })).toBeDisabled();
    expect(onAnalyzeFile).not.toHaveBeenCalled();
  });

  it('rejects empty files and oversized files', async () => {
    const { user } = setup();
    await user.upload(screen.getByLabelText(/choose file/i), new File([''], 'empty.log'));
    expect(screen.getByRole('alert')).toHaveTextContent(/empty/i);

    const huge = logFile('huge.log', 10);
    Object.defineProperty(huge, 'size', { value: 11 * 1024 * 1024 });
    await user.upload(screen.getByLabelText(/choose file/i), huge);
    expect(screen.getByRole('alert')).toHaveTextContent(/maximum allowed size/i);
  });

  it('accepts a dropped file', () => {
    setup();
    fireEvent.drop(screen.getByTestId('dropzone'), { dataTransfer: { files: [logFile('dropped.log')] } });
    expect(screen.getByTestId('selected-file')).toHaveTextContent('dropped.log');
  });

  it('rejects dropping more than one file', () => {
    setup();
    fireEvent.drop(screen.getByTestId('dropzone'), { dataTransfer: { files: [logFile('a.log'), logFile('b.log')] } });
    expect(screen.getByRole('alert')).toHaveTextContent(/single file/i);
  });

  it('passes the redaction choice and offers the sample', async () => {
    const { user, onLoadSample, onRedactChange } = setup();
    await user.click(screen.getByRole('checkbox'));
    expect(onRedactChange).toHaveBeenCalledWith(false);
    await user.click(screen.getByRole('button', { name: /try the sample log/i }));
    expect(onLoadSample).toHaveBeenCalledTimes(1);
  });

  it('disables actions while busy', () => {
    setup({ busy: true });
    expect(screen.getByRole('button', { name: /try the sample log/i })).toBeDisabled();
    expect(screen.getByLabelText(/choose file/i)).toBeDisabled();
  });
});
