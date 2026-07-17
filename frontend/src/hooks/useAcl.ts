import { useQuery } from '@tanstack/react-query';
import { getCollectionsEffectivePermissions, getTestimonialsEffectivePermissions } from '../api/acl';

export function useTestimonialsPermissions() {
  return useQuery({
    queryKey: ['acl', 'testimonials', 'effective-permissions'],
    queryFn: getTestimonialsEffectivePermissions,
  });
}

export function useCollectionsPermissions() {
  return useQuery({
    queryKey: ['acl', 'collections', 'effective-permissions'],
    queryFn: getCollectionsEffectivePermissions,
  });
}
