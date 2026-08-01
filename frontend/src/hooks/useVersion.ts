import {useQuery} from '@tanstack/react-query';
import {getVersion} from '../api/version';

export function useVersion() {
  return useQuery({
    queryKey: ['version'],
    queryFn: getVersion,
    staleTime: Infinity,
    retry: false,
  });
}
