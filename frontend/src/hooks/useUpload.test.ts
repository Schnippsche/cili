import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement, type ReactNode } from 'react';
import { useUpload } from './useUpload';
import * as uploadApi from '../api/upload';

vi.mock('../api/upload');
const mockedInit     = vi.mocked(uploadApi.initUpload);
const mockedChunk    = vi.mocked(uploadApi.uploadChunk);
const mockedComplete = vi.mocked(uploadApi.completeUpload);

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient();
  return createElement(QueryClientProvider, { client: qc }, children);
}

describe('useUpload', () => {
  beforeEach(() => vi.clearAllMocks());

  it('starts with empty uploads list', () => {
    const { result } = renderHook(() => useUpload(1), { wrapper });
    expect(result.current.uploads).toEqual([]);
  });

  it('tracks upload through init → chunk → complete', async () => {
    mockedInit.mockResolvedValueOnce({ jobId: 'j1', chunksTotal: 1, chunksReceived: 0, status: 'INITIATED' });
    mockedChunk.mockResolvedValueOnce({ jobId: 'j1', chunksTotal: 1, chunksReceived: 1, status: 'IN_PROGRESS' });
    mockedComplete.mockResolvedValueOnce({ resourceId: 42 });
    const { result } = renderHook(() => useUpload(1), { wrapper });
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' });
    await act(async () => { await result.current.uploadFile(file); });
    expect(result.current.uploads[0].status).toBe('done');
    expect(result.current.uploads[0].resourceId).toBe(42);
  });

  it('marks upload as error when init fails', async () => {
    mockedInit.mockRejectedValueOnce(new Error('Server error'));
    const { result } = renderHook(() => useUpload(1), { wrapper });
    await act(async () => { await result.current.uploadFile(new File(['x'], 'x.txt')); });
    expect(result.current.uploads[0].status).toBe('error');
  });
});
