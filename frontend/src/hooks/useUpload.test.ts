import {act, renderHook} from '@testing-library/react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {createElement, type ReactNode} from 'react';
import {useUpload} from './useUpload';
import * as uploadApi from '../api/upload';
import * as resourcesApi from '../api/resources';
import type {ResourceDto} from '../types/api';

vi.mock('../api/upload');
vi.mock('../api/resources');
const mockedInit = vi.mocked(uploadApi.initUpload);
const mockedChunk = vi.mocked(uploadApi.uploadChunk);
const mockedComplete = vi.mocked(uploadApi.completeUpload);
const mockedGetResource = vi.mocked(resourcesApi.getResource);

function wrapper({children}: { children: ReactNode }) {
  const qc = new QueryClient();
  return createElement(QueryClientProvider, {client: qc}, children);
}

describe('useUpload', () => {
  beforeEach(() => vi.clearAllMocks());

  it('starts with empty uploads list', () => {
    const {result} = renderHook(() => useUpload(1), {wrapper});
    expect(result.current.uploads).toEqual([]);
  });

  it('tracks upload through init → chunk → complete', async () => {
    mockedInit.mockResolvedValueOnce({
      jobId: 'j1',
      chunksTotal: 1,
      chunksReceived: 0,
      status: 'INITIATED'
    });
    mockedChunk.mockResolvedValueOnce({
      jobId: 'j1',
      chunksTotal: 1,
      chunksReceived: 1,
      status: 'IN_PROGRESS'
    });
    mockedComplete.mockResolvedValueOnce({resourceId: 42});
    const {result} = renderHook(() => useUpload(1), {wrapper});
    const file = new File(['hello'], 'hello.txt', {type: 'text/plain'});
    await act(async () => {
      await result.current.uploadFile(file);
    });
    expect(result.current.uploads[0].status).toBe('done');
    expect(result.current.uploads[0].resourceId).toBe(42);
  });

  it('marks upload as error when init fails', async () => {
    mockedInit.mockRejectedValueOnce(new Error('Server error'));
    const {result} = renderHook(() => useUpload(1), {wrapper});
    await act(async () => {
      await result.current.uploadFile(new File(['x'], 'x.txt'));
    });
    expect(result.current.uploads[0].status).toBe('error');
  });

  describe('audio normalization polling', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('polls the uploaded resource until it is normalized to audio/mpeg, then stops', async () => {
      mockedInit.mockResolvedValueOnce({
        jobId: 'j2',
        chunksTotal: 1,
        chunksReceived: 0,
        status: 'INITIATED'
      });
      mockedChunk.mockResolvedValueOnce({
        jobId: 'j2',
        chunksTotal: 1,
        chunksReceived: 1,
        status: 'IN_PROGRESS'
      });
      mockedComplete.mockResolvedValueOnce({resourceId: 99});
      mockedGetResource
      .mockResolvedValueOnce({mimeType: 'audio/ogg'} as ResourceDto)
      .mockResolvedValueOnce({mimeType: 'audio/mpeg'} as ResourceDto);

      const {result} = renderHook(() => useUpload(1), {wrapper});
      const file = new File(['x'], 'song.ogg', {type: 'audio/ogg'});
      await act(async () => {
        await result.current.uploadFile(file);
      });

      await act(async () => {
        await vi.advanceTimersByTimeAsync(1500);
      });
      expect(mockedGetResource).toHaveBeenCalledTimes(1);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(1500);
      });
      expect(mockedGetResource).toHaveBeenCalledTimes(2);

      // mimeType ist jetzt audio/mpeg -> Polling muss stoppen, keine weiteren Aufrufe
      await act(async () => {
        await vi.advanceTimersByTimeAsync(10_000);
      });
      expect(mockedGetResource).toHaveBeenCalledTimes(2);
    });

    it('does not poll for non-audio uploads', async () => {
      mockedInit.mockResolvedValueOnce({
        jobId: 'j3',
        chunksTotal: 1,
        chunksReceived: 0,
        status: 'INITIATED'
      });
      mockedChunk.mockResolvedValueOnce({
        jobId: 'j3',
        chunksTotal: 1,
        chunksReceived: 1,
        status: 'IN_PROGRESS'
      });
      mockedComplete.mockResolvedValueOnce({resourceId: 100});

      const {result} = renderHook(() => useUpload(1), {wrapper});
      const file = new File(['x'], 'doc.pdf', {type: 'application/pdf'});
      await act(async () => {
        await result.current.uploadFile(file);
      });

      await act(async () => {
        await vi.advanceTimersByTimeAsync(20_000);
      });
      expect(mockedGetResource).not.toHaveBeenCalled();
    });
  });
});
