import {beforeEach, describe, expect, test, vi} from 'vitest';
import {act, renderHook, waitFor} from '@testing-library/react';
import {useBulkImportUpload} from './useBulkImportUpload';
import * as uploadApi from '../api/upload';
import * as bulkImportApi from '../api/bulkImport';

vi.mock('../api/upload');
vi.mock('../api/bulkImport');

function makeFile(relativePath: string, content = 'x'): File {
  const file = new File([content], relativePath.split('/').pop()!, {type: 'video/mp4'});
  Object.defineProperty(file, 'webkitRelativePath', {value: `Quelle/${relativePath}`});
  return file;
}

describe('useBulkImportUpload', () => {
  beforeEach(() => vi.clearAllMocks());

  test('start: uploads all PENDING items and marks them DONE', async () => {
    vi.mocked(bulkImportApi.createBulkImport).mockResolvedValue({
      jobId: 'job1',
      items: [
        {
          id: 1,
          relativePath: 'video1.mp4',
          resolvedFolderId: 10,
          status: 'PENDING',
          skipReason: null,
          errorMessage: null,
          resourceId: null
        },
        {
          id: 2,
          relativePath: 'notes.exe',
          resolvedFolderId: null,
          status: 'SKIPPED',
          skipReason: 'Nicht unterstützt',
          errorMessage: null,
          resourceId: null
        },
      ],
    });
    vi.mocked(uploadApi.initUpload).mockResolvedValue({
      jobId: 'up1',
      chunksTotal: 1,
      chunksReceived: 0,
      status: 'INITIATED'
    });
    vi.mocked(uploadApi.uploadChunk).mockResolvedValue({
      jobId: 'up1',
      chunksTotal: 1,
      chunksReceived: 1,
      status: 'IN_PROGRESS'
    });
    vi.mocked(uploadApi.completeUpload).mockResolvedValue({resourceId: 100});

    const {result} = renderHook(() => useBulkImportUpload());

    await act(async () => {
      await result.current.start(10, 'Quelle', [makeFile('video1.mp4'), makeFile('notes.exe')]);
    });

    await waitFor(() => {
      expect(result.current.items.find(i => i.id === 1)?.status).toBe('DONE');
    });
    expect(result.current.items.find(i => i.id === 2)?.status).toBe('SKIPPED');
    expect(uploadApi.initUpload).toHaveBeenCalledWith(expect.objectContaining({
      bulkImportItemId: 1,
      folderId: 10
    }));
  });

  test('start: marks item FAILED and reports it to the backend after retries are exhausted', async () => {
    vi.mocked(bulkImportApi.createBulkImport).mockResolvedValue({
      jobId: 'job2',
      items: [
        {
          id: 3,
          relativePath: 'video1.mp4',
          resolvedFolderId: 10,
          status: 'PENDING',
          skipReason: null,
          errorMessage: null,
          resourceId: null
        },
      ],
    });
    vi.mocked(uploadApi.initUpload).mockRejectedValue(new Error('Netzwerkfehler'));
    vi.mocked(bulkImportApi.failBulkImportItem).mockResolvedValue(undefined);

    const {result} = renderHook(() => useBulkImportUpload());

    await act(async () => {
      await result.current.start(10, 'Quelle', [makeFile('video1.mp4')]);
    });

    expect(result.current.items.find(i => i.id === 3)?.status).toBe('FAILED');
    expect(bulkImportApi.failBulkImportItem).toHaveBeenCalledWith('job2', 3, expect.any(String));
    expect(uploadApi.initUpload).toHaveBeenCalledTimes(3); // MAX_RETRIES ausgeschöpft, bevor aufgegeben wird
  });
});
