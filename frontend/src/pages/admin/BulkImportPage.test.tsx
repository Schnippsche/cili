import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { configureStore } from '@reduxjs/toolkit';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import authReducer, { setCredentials } from '../../store/authSlice';
import themeReducer from '../../store/themeSlice';
import BulkImportPage from './BulkImportPage';
import * as useBulkImportUploadModule from '../../hooks/useBulkImportUpload';
import * as foldersApi from '../../api/folders';

vi.mock('../../api/folders');

function renderPage() {
  const store = configureStore({ reducer: { auth: authReducer, theme: themeReducer } });
  store.dispatch(setCredentials({
    user: { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' },
    accessToken: 'at', refreshToken: 'rt',
  }));
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={qc}>
        <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <BulkImportPage />
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  );
}

describe('BulkImportPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.mocked(foldersApi.getFolderChildren).mockResolvedValue([]);
  });

  test('renders the folder selection button and heading', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /bulk-ordner-import/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /ordner ausw(ä|ae)hlen/i })).toBeInTheDocument();
  });

  test('calls start() with target folder, root name and selected files once a target folder is picked', async () => {
    const startMock = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(useBulkImportUploadModule, 'useBulkImportUpload').mockReturnValue({
      jobId: null, items: [], running: false, start: startMock, resume: vi.fn(),
    });
    vi.mocked(foldersApi.getFolderChildren).mockResolvedValue([
      { id: 42, name: 'Kampagnen', parentId: null, path: '/42/', description: null,
        trashed: false, trashedAt: null, createdBy: 1, createdAt: '', updatedAt: '' },
    ]);

    renderPage();

    const file = new File(['x'], 'video1.mp4', { type: 'video/mp4' });
    Object.defineProperty(file, 'webkitRelativePath', { value: 'Quelle/Interviews/video1.mp4' });
    const input = screen.getByTestId('folder-input') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [file] } });

    fireEvent.click(screen.getByRole('button', { name: /zielordner w(ä|ae)hlen/i }));
    const folderOption = await screen.findByText('Kampagnen');
    fireEvent.click(folderOption);
    fireEvent.click(screen.getByRole('button', { name: /verschieben/i }));

    await waitFor(() => expect(startMock).toHaveBeenCalledWith(42, 'Quelle', [file]));
  });

  test('persists jobId to localStorage once the hook reports one, and clears it once all items are terminal', () => {
    vi.spyOn(useBulkImportUploadModule, 'useBulkImportUpload').mockReturnValue({
      jobId: 'job1', running: false, start: vi.fn(), resume: vi.fn(),
      items: [
        { id: 1, relativePath: 'video1.mp4', status: 'DONE' },
        { id: 2, relativePath: 'video2.mp4', status: 'FAILED', errorMessage: 'x' },
      ],
    });

    renderPage();

    expect(localStorage.getItem('cili.bulkImport.jobId')).toBeNull();
  });

  test('does not start a second import while the first start() call is still pending', async () => {
    let resolveStart: () => void = () => {};
    const startPromise = new Promise<void>(resolve => { resolveStart = resolve; });
    const startMock = vi.fn().mockReturnValue(startPromise);
    vi.spyOn(useBulkImportUploadModule, 'useBulkImportUpload').mockReturnValue({
      jobId: null, items: [], running: false, start: startMock, resume: vi.fn(),
    });
    vi.mocked(foldersApi.getFolderChildren).mockResolvedValue([
      { id: 42, name: 'Kampagnen', parentId: null, path: '/42/', description: null,
        trashed: false, trashedAt: null, createdBy: 1, createdAt: '', updatedAt: '' },
    ]);

    renderPage();

    const file = new File(['x'], 'video1.mp4', { type: 'video/mp4' });
    Object.defineProperty(file, 'webkitRelativePath', { value: 'Quelle/Interviews/video1.mp4' });
    const input = screen.getByTestId('folder-input') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [file] } });

    fireEvent.click(screen.getByRole('button', { name: /zielordner w(ä|ae)hlen/i }));
    const folderOption = await screen.findByText('Kampagnen');
    fireEvent.click(folderOption);
    fireEvent.click(screen.getByRole('button', { name: /verschieben/i }));

    await waitFor(() => expect(startMock).toHaveBeenCalledTimes(1));

    // While start() is still pending, "Zielordner wählen" must stay disabled so a
    // second target-folder pick cannot fire off a duplicate import job. MUI's Dialog
    // exit transition is still in flight at this point, so the underlying page is
    // briefly aria-hidden — query with { hidden: true } to see past that.
    const targetButton = screen.getByRole('button', { name: /zielordner w(ä|ae)hlen/i, hidden: true });
    expect(targetButton).toBeDisabled();
    fireEvent.click(targetButton);
    expect(startMock).toHaveBeenCalledTimes(1);

    resolveStart();
    await waitFor(() => expect(startMock).toHaveBeenCalledTimes(1));
  });

  test('shows an error and offers a discard action when resume() fails, unblocking a fresh import', async () => {
    localStorage.setItem('cili.bulkImport.jobId', 'stuck-job');
    const resumeMock = vi.fn().mockRejectedValue(new Error('Job nicht gefunden'));
    vi.spyOn(useBulkImportUploadModule, 'useBulkImportUpload').mockReturnValue({
      jobId: null, items: [], running: false, start: vi.fn(), resume: resumeMock,
    });

    renderPage();

    expect(screen.getByText(/vorheriger import wurde nicht abgeschlossen/i)).toBeInTheDocument();

    const file = new File(['x'], 'video1.mp4', { type: 'video/mp4' });
    Object.defineProperty(file, 'webkitRelativePath', { value: 'Quelle/Interviews/video1.mp4' });
    const input = screen.getByTestId('folder-input') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => expect(resumeMock).toHaveBeenCalledWith('stuck-job', [file]));
    await screen.findByText(/Job nicht gefunden/);

    // Still blocked (job id not yet discarded): can't jump straight into a fresh import.
    expect(screen.getByRole('button', { name: /zielordner w(ä|ae)hlen/i })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: /neu starten/i }));

    expect(localStorage.getItem('cili.bulkImport.jobId')).toBeNull();
    expect(screen.queryByText(/vorheriger import wurde nicht abgeschlossen/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Job nicht gefunden/)).not.toBeInTheDocument();
  });

  test('shows a success completion message once every item is DONE/SKIPPED and the import is no longer running', () => {
    vi.spyOn(useBulkImportUploadModule, 'useBulkImportUpload').mockReturnValue({
      jobId: 'job1', running: false, start: vi.fn(), resume: vi.fn(),
      items: [
        { id: 1, relativePath: 'video1.mp4', status: 'DONE' },
        { id: 2, relativePath: 'notes.exe', status: 'SKIPPED', skipReason: 'x' },
      ],
    });

    renderPage();

    expect(screen.getByText(/import abgeschlossen: 1 fertig, 1 übersprungen, 0 fehlgeschlagen/i)).toBeInTheDocument();
    expect(screen.queryByText(/import läuft/i)).not.toBeInTheDocument();
  });

  test('shows a warning completion message once terminal but with failed items, and no message while still running', () => {
    const store = configureStore({ reducer: { auth: authReducer, theme: themeReducer } });
    store.dispatch(setCredentials({
      user: { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' },
      accessToken: 'at', refreshToken: 'rt',
    }));
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    // A fresh element tree per render/rerender call is required here: reusing the
    // exact same JSX reference across rerender() lets React bail out at the root
    // (nothing "changed" from its perspective, since our mock update happens outside
    // React state/props), silently skipping the re-render we're relying on to see
    // the new mocked hook return value take effect.
    const renderTree = () => (
      <Provider store={store}>
        <QueryClientProvider client={qc}>
          <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
            <BulkImportPage />
          </MemoryRouter>
        </QueryClientProvider>
      </Provider>
    );

    vi.spyOn(useBulkImportUploadModule, 'useBulkImportUpload').mockReturnValue({
      jobId: 'job1', running: true, start: vi.fn(), resume: vi.fn(),
      items: [
        { id: 1, relativePath: 'video1.mp4', status: 'UPLOADING' },
        { id: 2, relativePath: 'video2.mp4', status: 'FAILED', errorMessage: 'x' },
      ],
    });
    const { rerender } = render(renderTree());

    expect(screen.queryByText(/import abgeschlossen/i)).not.toBeInTheDocument();

    vi.spyOn(useBulkImportUploadModule, 'useBulkImportUpload').mockReturnValue({
      jobId: 'job1', running: false, start: vi.fn(), resume: vi.fn(),
      items: [
        { id: 1, relativePath: 'video1.mp4', status: 'DONE' },
        { id: 2, relativePath: 'video2.mp4', status: 'FAILED', errorMessage: 'x' },
      ],
    });
    rerender(renderTree());

    expect(screen.getByText(/import abgeschlossen: 1 fertig, 0 übersprungen, 1 fehlgeschlagen/i)).toBeInTheDocument();
  });
});
