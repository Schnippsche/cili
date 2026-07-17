import axiosClient from './axiosClient';
import type { EffectivePermissionsResponse } from '../types/api';

export async function getTestimonialsEffectivePermissions(): Promise<EffectivePermissionsResponse> {
  const { data } = await axiosClient.get<EffectivePermissionsResponse>(
    '/acl/testimonials/effective-permissions',
  );
  return data;
}

export async function getCollectionsEffectivePermissions(): Promise<EffectivePermissionsResponse> {
  const { data } = await axiosClient.get<EffectivePermissionsResponse>(
    '/acl/collections/effective-permissions',
  );
  return data;
}
