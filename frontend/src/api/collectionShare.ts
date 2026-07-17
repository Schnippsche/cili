import axiosClient from './axiosClient';
import { BASE } from './base';
import type { CollectionShareTokenDto, CollectionShareInfoDto } from '../types/api';

export class CollectionShareExpiredError extends Error {
  constructor() { super('EXPIRED'); }
}

// ── Authenticated (collection owner) ────────────────────────────────────────

export async function createCollectionShare(collectionId: number): Promise<CollectionShareTokenDto> {
  const { data } = await axiosClient.post<CollectionShareTokenDto>(`/collections/${collectionId}/share`);
  return data;
}

export async function getCollectionShare(collectionId: number): Promise<CollectionShareTokenDto | null> {
  const { status, data } = await axiosClient.get<CollectionShareTokenDto>(
    `/collections/${collectionId}/share`,
    { validateStatus: s => s === 200 || s === 204 },
  );
  return status === 200 ? data : null;
}

export async function revokeCollectionShare(collectionId: number): Promise<void> {
  await axiosClient.delete(`/collections/${collectionId}/share`);
}

// ── Public (no auth, native fetch) ──────────────────────────────────────────

export async function getCollectionShareInfo(token: string): Promise<CollectionShareInfoDto> {
  const res = await fetch(`${BASE}/api/share/collection/${token}/info`);
  if (res.status === 410) throw new CollectionShareExpiredError();
  if (!res.ok) throw new Error(String(res.status));
  return res.json() as Promise<CollectionShareInfoDto>;
}

export function collectionShareStreamUrl(token: string, resourceId: number): string {
  return `${BASE}/api/share/collection/${token}/stream/${resourceId}`;
}

export function collectionShareThumbnailUrl(token: string, resourceId: number, size: 'small' | 'large' = 'small'): string {
  return `${BASE}/api/share/collection/${token}/thumbnail/${resourceId}?size=${size}`;
}

export function collectionShareSubtitleUrl(token: string, resourceId: number, trackId: number): string {
  return `${BASE}/api/share/collection/${token}/subtitles/${resourceId}/${trackId}`;
}
