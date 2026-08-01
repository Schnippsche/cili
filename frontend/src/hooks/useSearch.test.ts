import {act, renderHook} from '@testing-library/react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {createElement, type ReactNode} from 'react';
import {useSearch} from './useSearch';
import * as searchApi from '../api/search';

vi.mock('../api/search');
vi.mocked(searchApi.search).mockResolvedValue({
  hits: [], totalHits: 0, page: 0, size: 20,
  testimonialHits: [], testimonialTotalHits: 0, testimonialPage: 0, testimonialSize: 10,
});
vi.mocked(searchApi.getFacets).mockResolvedValue({mimeTypes: [], languages: []});

function wrapper({children}: { children: ReactNode }) {
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  return createElement(QueryClientProvider, {client: qc}, children);
}

describe('useSearch', () => {
  beforeEach(() => vi.clearAllMocks());

  it('starts with empty query', () => {
    const {result} = renderHook(() => useSearch(), {wrapper});
    expect(result.current.query).toBe('');
  });

  it('setQuery updates query state', () => {
    const {result} = renderHook(() => useSearch(), {wrapper});
    act(() => result.current.setQuery('hello'));
    expect(result.current.query).toBe('hello');
  });
});
