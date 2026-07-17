import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement, type ReactNode } from 'react';
import { useResourcesByFolder } from './useResources';
import * as resourcesApi from '../api/resources';

vi.mock('../api/resources');
const mockedList = vi.mocked(resourcesApi.getResourcesByFolder);

const mockResource = { id: 1, folderId: 10, originalName: 'doc.pdf', storedName: 'uuid',
  mimeType: 'application/pdf', size: 1024, checksum: null, uploaderId: 1,
  storageType: 'LOCAL', fileDate: null, sortOrder: null, createdAt: '', updatedAt: '', metadata: null, thumbnailStatus: null, hasAnalyzableSubtitles: false };

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return createElement(QueryClientProvider, { client: qc }, children);
}

describe('useResources', () => {
  beforeEach(() => vi.clearAllMocks());

  it('fetches resources by folder id', async () => {
    mockedList.mockResolvedValueOnce([mockResource]);
    const { result } = renderHook(() => useResourcesByFolder(10), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data![0].originalName).toBe('doc.pdf');
  });

  it('does not fetch when folderId is undefined', () => {
    const { result } = renderHook(() => useResourcesByFolder(undefined), { wrapper });
    expect(result.current.status).toBe('pending');
    expect(mockedList).not.toHaveBeenCalled();
  });
});
