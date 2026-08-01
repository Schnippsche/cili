import {act, renderHook, waitFor} from '@testing-library/react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {createElement, type ReactNode} from 'react';
import {
  useFolder,
  useFolderChildren,
  usePurgeFolder,
  useRestoreFolder,
  useTrash
} from './useFolders';
import * as foldersApi from '../api/folders';

vi.mock('../api/folders');
const mockedGetChildren = vi.mocked(foldersApi.getFolderChildren);
const mockedGetFolder = vi.mocked(foldersApi.getFolder);
const mockedGetTrash = vi.mocked(foldersApi.getTrash);
const mockedRestoreFolder = vi.mocked(foldersApi.restoreFolder);
const mockedPurgeFolder = vi.mocked(foldersApi.purgeFolder);

const mockFolder = {
  id: 1,
  name: 'Docs',
  parentId: null,
  path: '/1/',
  description: null,
  trashed: false,
  trashedAt: null,
  createdBy: 1,
  createdAt: '',
  updatedAt: ''
};

function wrapper({children}: { children: ReactNode }) {
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  return createElement(QueryClientProvider, {client: qc}, children);
}

describe('useFolders', () => {
  beforeEach(() => vi.clearAllMocks());

  it('useFolderChildren fetches root folders when folderId is undefined', async () => {
    mockedGetChildren.mockResolvedValueOnce([mockFolder]);
    const {result} = renderHook(() => useFolderChildren(undefined), {wrapper});
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([mockFolder]);
    expect(mockedGetChildren).toHaveBeenCalledWith(undefined);
  });

  it('useFolder fetches a single folder by id', async () => {
    mockedGetFolder.mockResolvedValueOnce(mockFolder);
    const {result} = renderHook(() => useFolder(1), {wrapper});
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.name).toBe('Docs');
  });

  it('useTrash fetches the trash list', async () => {
    const trashed = {...mockFolder, trashed: true, trashedAt: '2026-05-20T10:00:00'};
    mockedGetTrash.mockResolvedValueOnce([trashed]);
    const {result} = renderHook(() => useTrash(), {wrapper});
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].trashed).toBe(true);
    expect(mockedGetTrash).toHaveBeenCalledOnce();
  });

  it('useRestoreFolder calls restoreFolder and invalidates caches', async () => {
    mockedRestoreFolder.mockResolvedValueOnce({...mockFolder, trashed: false, trashedAt: null});
    const {result} = renderHook(() => useRestoreFolder(), {wrapper});
    await act(async () => {
      result.current.mutate(1);
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedRestoreFolder).toHaveBeenCalledWith(1);
  });

  it('usePurgeFolder calls purgeFolder and invalidates trash cache', async () => {
    mockedPurgeFolder.mockResolvedValueOnce(undefined);
    const {result} = renderHook(() => usePurgeFolder(), {wrapper});
    await act(async () => {
      result.current.mutate(1);
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedPurgeFolder).toHaveBeenCalledWith(1);
  });
});
