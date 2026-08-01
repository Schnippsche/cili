import axiosClient from './axiosClient';
import {BASE} from './base';
import type {
  AiSummaryDto,
  MetadataDto,
  ProcessingJobDto,
  ResourceDto,
  SubtitleTrackDto,
  UpdateMetadataRequest
} from '../types/api';

export async function getResourcesByFolder(folderId: number): Promise<ResourceDto[]> {
  const {data} = await axiosClient.get<ResourceDto[]>(`/folders/${folderId}/resources`);
  return data;
}

export async function reorderResources(folderId: number, resourceIds: number[]): Promise<void> {
  await axiosClient.patch(`/folders/${folderId}/resources/reorder`, {resourceIds});
}

export async function getResource(id: number): Promise<ResourceDto> {
  const {data} = await axiosClient.get<ResourceDto>(`/resources/${id}`);
  return data;
}

function tokenParam(): string {
  const t = localStorage.getItem('accessToken');
  return t ? `?token=${t}` : '';
}

export function getDownloadUrl(id: number): string {
  return `${BASE}/api/resources/${id}/download${tokenParam()}`;
}

export function getStreamUrl(id: number): string {
  return `${BASE}/api/resources/${id}/stream${tokenParam()}`;
}

export function getPreviewUrl(id: number): string {
  return `${BASE}/api/resources/${id}/preview${tokenParam()}`;
}

// Wird via axiosClient (useAuthenticatedUrl) geladen → Pfad relativ zu axiosClient.baseURL ('/cili/api')
// axiosClient hängt baseURL selbst voran, daher kein '${BASE}/api'-Prefix hier
export function getThumbnailUrl(id: number, size: 'small' | 'large' = 'small', v?: string): string {
  return `/resources/${id}/thumbnail?size=${size}${v ? `&v=${v}` : ''}`;
}

export async function renameResource(id: number, originalName: string): Promise<ResourceDto> {
  const {data} = await axiosClient.put<ResourceDto>(`/resources/${id}`, {originalName});
  return data;
}

export async function deleteResource(id: number): Promise<void> {
  await axiosClient.delete(`/resources/${id}`);
}

export async function updateMetadata(id: number, req: UpdateMetadataRequest): Promise<MetadataDto> {
  const {data} = await axiosClient.put<MetadataDto>(`/resources/${id}/metadata`, req);
  return data;
}

export async function addResourceFavorite(id: number): Promise<void> {
  await axiosClient.post(`/resources/${id}/favorite`);
}

export async function removeResourceFavorite(id: number): Promise<void> {
  await axiosClient.delete(`/resources/${id}/favorite`);
}

export async function getResourceFavorites(): Promise<ResourceDto[]> {
  const {data} = await axiosClient.get<ResourceDto[]>('/resources/favorites');
  return data;
}

export async function getResourceContent(id: number): Promise<string> {
  const {data} = await axiosClient.get<string>(`/resources/${id}/content`);
  return data;
}

export async function saveResourceContent(id: number, content: string): Promise<void> {
  await axiosClient.put(
      `/resources/${id}/content`, content,
      {headers: {'Content-Type': 'text/plain'}}
  );
}

export async function getSubtitleTracks(resourceId: number): Promise<SubtitleTrackDto[]> {
  const {data} = await axiosClient.get<SubtitleTrackDto[]>(`/resources/${resourceId}/subtitles`);
  return data;
}

export function getSubtitleUrl(resourceId: number, trackId: number): string {
  return `${BASE}/api/resources/${resourceId}/subtitles/${trackId}${tokenParam()}`;
}

export function getSubtitleDownloadUrl(resourceId: number, trackId: number, format: 'vtt' | 'srt' | 'txt'): string {
  const t = localStorage.getItem('accessToken');
  const params = new URLSearchParams({format});
  if (t) params.set('token', t);
  return `${BASE}/api/resources/${resourceId}/subtitles/${trackId}/download?${params.toString()}`;
}

export async function uploadSubtitle(
    resourceId: number,
    languageCode: string,
    label: string | null,
    format: 'SRT' | 'VTT',
    file: File
): Promise<SubtitleTrackDto> {
  const form = new FormData();
  form.append('languageCode', languageCode);
  if (label) form.append('label', label);
  form.append('format', format);
  form.append('file', file);
  const {data} = await axiosClient.post<SubtitleTrackDto>(
      `/resources/${resourceId}/subtitles`,
      form,
      {headers: {'Content-Type': undefined}}
  );
  return data;
}

export async function deleteSubtitle(resourceId: number, trackId: number): Promise<void> {
  await axiosClient.delete(`/resources/${resourceId}/subtitles/${trackId}`);
}

export async function retranscribeResource(resourceId: number): Promise<void> {
  await axiosClient.post(`/resources/${resourceId}/retranscribe`);
}

export async function hasActiveTranscriptionJob(resourceId: number): Promise<boolean> {
  const {data} = await axiosClient.get<boolean>(`/resources/${resourceId}/transcription-jobs/active`);
  return data;
}

export async function getSubtitleText(resourceId: number, trackId: number): Promise<string> {
  const {data} = await axiosClient.get<string>(
      `/resources/${resourceId}/subtitles/${trackId}/text`,
      {responseType: 'text'}
  );
  return data;
}

export async function requestTranslation(
    resourceId: number,
    sourceTrackId: number,
    targetLang: string,
    overwrite = false,
): Promise<{ jobId: number }> {
  const {data} = await axiosClient.post<{ jobId: number }>(
      `/resources/${resourceId}/subtitle-translations`,
      {sourceTrackId, targetLang},
      {params: overwrite ? {overwrite: 'true'} : undefined},
  );
  return data;
}

export async function getActiveTranslationJobs(
    resourceId: number,
): Promise<ProcessingJobDto[]> {
  const {data} = await axiosClient.get<ProcessingJobDto[]>(
      `/resources/${resourceId}/subtitle-translations/active`,
  );
  return data;
}

export async function requestDocumentTranslation(
    resourceId: number,
    sourceLang: string,
    targetLang: string,
): Promise<{ jobId: number }> {
  const {data} = await axiosClient.post<{ jobId: number }>(
      `/resources/${resourceId}/document-translations`,
      {sourceLang, targetLang},
  );
  return data;
}

export async function getActiveDocumentTranslationJobs(
    resourceId: number,
): Promise<ProcessingJobDto[]> {
  const {data} = await axiosClient.get<ProcessingJobDto[]>(
      `/resources/${resourceId}/document-translations/active`,
  );
  return data;
}

export async function moveResource(id: number, newFolderId: number): Promise<ResourceDto> {
  const {data} = await axiosClient.patch<ResourceDto>(`/resources/${id}/move`, null, {
    params: {newFolderId},
  });
  return data;
}

export async function getAiSummaries(id: number): Promise<AiSummaryDto[]> {
  const {data} = await axiosClient.get<AiSummaryDto[]>(`/resources/${id}/summaries`);
  return data;
}

export async function analyzeResource(id: number, languageCode: string, force = false): Promise<{
  jobId: number
}> {
  const {data} = await axiosClient.post<{ jobId: number }>(
      `/resources/${id}/analyze`,
      null,
      {params: {languageCode, ...(force ? {force: 'true'} : {})}}
  );
  return data;
}

export async function getActiveAnalysisJobs(resourceId: number): Promise<ProcessingJobDto[]> {
  const {data} = await axiosClient.get<ProcessingJobDto[]>(
      `/resources/${resourceId}/analysis-jobs/active`,
  );
  return data;
}

export async function createMediaClip(resourceId: number, startMs: number, endMs: number, title: string): Promise<{
  jobId: number
}> {
  const {data} = await axiosClient.post<{
    jobId: number
  }>(`/resources/${resourceId}/clip`, {startMs, endMs, title});
  return data;
}

export async function getActiveClipJobs(resourceId: number): Promise<ProcessingJobDto[]> {
  const {data} = await axiosClient.get<ProcessingJobDto[]>(`/resources/${resourceId}/clip-jobs/active`);
  return data;
}
