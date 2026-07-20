import { useState, useDeferredValue, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { search } from '../api/search';

export function useSearch() {
  const [query, setQuery] = useState('');
  const [page, setPage]   = useState(0);
  const [tPage, setTPage] = useState(0);
  const deferredQuery = useDeferredValue(query);

  // Neue Suche → zurück auf Seite 1 für beide Trefferlisten, sonst könnte man auf einer
  // leeren Folgeseite landen, wenn die vorherige Suche mehr Treffer hatte.
  useEffect(() => {
    setPage(0);
    setTPage(0);
  }, [deferredQuery]);

  const { data: results, isLoading, isFetching } = useQuery({
    queryKey: ['search', deferredQuery, page, tPage],
    queryFn:  () => search({ q: deferredQuery, page, size: 20, tPage }),
    staleTime: 10_000,
    enabled: deferredQuery.trim().length >= 2,
  });

  return { query, setQuery, page, setPage, tPage, setTPage, results, isLoading: isLoading || isFetching };
}
