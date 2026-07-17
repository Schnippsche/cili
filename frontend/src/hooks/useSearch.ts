import { useState, useDeferredValue } from 'react';
import { useQuery } from '@tanstack/react-query';
import { search } from '../api/search';

export function useSearch() {
  const [query, setQuery] = useState('');
  const [page, setPage]   = useState(0);
  const deferredQuery = useDeferredValue(query);

  const { data: results, isLoading, isFetching } = useQuery({
    queryKey: ['search', deferredQuery, page],
    queryFn:  () => search({ q: deferredQuery, page, size: 20 }),
    staleTime: 10_000,
    enabled: deferredQuery.trim().length >= 2,
  });

  return { query, setQuery, page, setPage, results, isLoading: isLoading || isFetching };
}
