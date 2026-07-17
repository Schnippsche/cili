import { useQuery } from '@tanstack/react-query';
import { getLanguages } from '../api/languages';
import type { LanguageOptionDto } from '../types/api';

function sortLanguages(langs: LanguageOptionDto[]): LanguageOptionDto[] {
  return [...langs].sort((a, b) => {
    if (a.code === 'de') return -1;
    if (b.code === 'de') return  1;
    return a.label.localeCompare(b.label, 'de');
  });
}

export function useLanguageOptions() {
  return useQuery<LanguageOptionDto[]>({
    queryKey: ['languageOptions'],
    queryFn: async () => sortLanguages(await getLanguages()),
    staleTime: 5 * 60 * 1000,
  });
}
