import axiosClient from './axiosClient';
import {BASE} from './base';
import type {ShareConfigDto, ShareInfoDto, ShareTokenDto} from '../types/api';

export async function createShare(resourceId: number): Promise<ShareTokenDto> {
  const {data} = await axiosClient.post<ShareTokenDto>(`/resources/${resourceId}/share`);
  return data;
}

// 200 = token exists, 204 = no token
export async function getShare(resourceId: number): Promise<ShareTokenDto | null> {
  const {status, data} = await axiosClient.get<ShareTokenDto>(
      `/resources/${resourceId}/share`,
      {validateStatus: s => s === 200 || s === 204},
  );
  return status === 200 ? data : null;
}

export async function revokeShare(resourceId: number): Promise<void> {
  await axiosClient.delete(`/resources/${resourceId}/share`);
}

export async function getShareConfig(): Promise<ShareConfigDto> {
  const {data} = await axiosClient.get<ShareConfigDto>('/share/config');
  return data;
}

// Intentionally uses native fetch — axiosClient injects JWT and has a refresh interceptor
// that would trigger an unwanted logout on the public share page.
export class ShareExpiredError extends Error {
  constructor() {
    super('EXPIRED');
  }
}

export async function getShareInfo(token: string): Promise<ShareInfoDto> {
  const res = await fetch(`${BASE}/api/share/${token}/info`);
  if (res.status === 410) throw new ShareExpiredError();
  if (!res.ok) throw new Error(String(res.status));
  return res.json() as Promise<ShareInfoDto>;
}

// Public subtitle URL for the player's <track> element (no auth needed).
export function getShareSubtitleUrl(token: string, trackId: number): string {
  return `${BASE}/api/share/${token}/subtitles/${trackId}`;
}

// Native fetch (no axios/JWT) for the transcript panel on the public share page.
export async function getShareSubtitleText(token: string, trackId: number): Promise<string> {
  const res = await fetch(`${BASE}/api/share/${token}/subtitles/${trackId}/text`);
  if (!res.ok) throw new Error(String(res.status));
  return res.text();
}
