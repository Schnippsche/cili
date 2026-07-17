import { useCallback, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { completeUpload, initUpload, uploadChunk } from '../api/upload';
import { uploadSubtitle } from '../api/resources';
import type { ResourceDto } from '../types/api';

const CHUNK_SIZE = 5 * 1024 * 1024;
const SUBTITLE_EXTS = new Set(['srt', 'vtt']);
const VIDEO_EXTS = new Set(['mp4', 'mkv', 'avi', 'mov', 'webm', 'flv', 'wmv', 'ts', 'ogv', 'm4v']);

export interface UploadEntry {
  id: string;
  fileName: string;
  progress: number;
  status: 'uploading' | 'done' | 'error';
  resourceId?: number;
  error?: string;
  codecWarning?: string;
  subtitleFile?: string;
  subtitleStatus?: 'pending' | 'uploading' | 'done' | 'error';
  subtitleError?: string;
}

function fileExt(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : '';
}

function isVideoFile(name: string): boolean {
  return VIDEO_EXTS.has(fileExt(name));
}

function isSubtitleFile(name: string): boolean {
  return SUBTITLE_EXTS.has(fileExt(name));
}

// "video1.de.vtt" → "video1", "video1.vtt" → "video1", "video1.mp4" → "video1"
function baseName(fileName: string): string {
  const ext = fileExt(fileName);
  const withoutExt = fileName.slice(0, -(ext.length + 1));
  if (SUBTITLE_EXTS.has(ext)) {
    const lastDot = withoutExt.lastIndexOf('.');
    if (lastDot >= 0 && /^[a-z]{2}(-[A-Z]{2})?$/.test(withoutExt.slice(lastDot + 1))) {
      return withoutExt.slice(0, lastDot);
    }
  }
  return withoutExt;
}

// "video1.de.vtt" → "de", "video1.vtt" → "de" (default)
function extractLang(fileName: string): string {
  const ext = fileExt(fileName);
  const withoutExt = fileName.slice(0, -(ext.length + 1));
  const lastDot = withoutExt.lastIndexOf('.');
  if (lastDot >= 0) {
    const candidate = withoutExt.slice(lastDot + 1);
    if (/^[a-z]{2}(-[A-Z]{2})?$/.test(candidate)) return candidate;
  }
  return 'de';
}

function subtitleFormat(fileName: string): 'SRT' | 'VTT' {
  return fileExt(fileName) === 'srt' ? 'SRT' : 'VTT';
}

export function useUpload(folderId: number) {
  const [uploads, setUploads] = useState<UploadEntry[]>([]);
  const qc = useQueryClient();

  const update = (id: string, patch: Partial<UploadEntry>) =>
    setUploads(prev => prev.map(u => u.id === id ? { ...u, ...patch } : u));

  const doSubtitleUpload = useCallback(async (eid: string, resourceId: number, sub: File) => {
    setUploads(prev => prev.map(u => u.id === eid ? { ...u, subtitleStatus: 'uploading' as const } : u));
    try {
      await uploadSubtitle(resourceId, extractLang(sub.name), null, subtitleFormat(sub.name), sub);
      setUploads(prev => prev.map(u => u.id === eid ? { ...u, subtitleStatus: 'done' as const } : u));
    } catch {
      setUploads(prev => prev.map(u => u.id === eid ? { ...u, subtitleStatus: 'error' as const, subtitleError: 'Untertitel-Upload fehlgeschlagen' } : u));
    }
  }, []);

  const uploadFile = useCallback(async (file: File, pairedSubtitle?: File) => {
    const eid = `${file.name}-${Date.now()}`;
    setUploads(prev => [...prev, {
      id: eid,
      fileName: file.name,
      progress: 0,
      status: 'uploading',
      subtitleFile: pairedSubtitle?.name,
      subtitleStatus: pairedSubtitle ? 'pending' : undefined,
    }]);
    try {
      const job = await initUpload({
        fileName: file.name,
        mimeType: file.type || 'application/octet-stream',
        totalSize: file.size,
        chunkSize: CHUNK_SIZE,
        folderId,
        fileLastModified: file.lastModified,
      });
      for (let i = 0; i < job.chunksTotal; i++) {
        await uploadChunk(job.jobId, i, file.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE));
        update(eid, { progress: Math.round(((i + 1) / job.chunksTotal) * 100) });
      }
      const done = await completeUpload(job.jobId);
      update(eid, { status: 'done', progress: 100, resourceId: done.resourceId, codecWarning: done.codecWarning });
      qc.invalidateQueries({ queryKey: ['resources', 'folder', folderId] });
      if (pairedSubtitle && done.resourceId) {
        await doSubtitleUpload(eid, done.resourceId, pairedSubtitle);
      }
    } catch (err) {
      update(eid, { status: 'error', error: (err as { message?: string })?.message ?? 'Upload fehlgeschlagen' });
    }
  }, [folderId, qc, doSubtitleUpload]);

  const uploadSubtitleOnly = useCallback(async (sub: File) => {
    const eid = `${sub.name}-${Date.now()}`;
    setUploads(prev => [...prev, { id: eid, fileName: sub.name, progress: 100, status: 'uploading', subtitleStatus: 'uploading' }]);
    try {
      const resources = qc.getQueryData<ResourceDto[]>(['resources', 'folder', folderId]);
      const base = baseName(sub.name);
      const match = resources?.find(r => isVideoFile(r.originalName) && baseName(r.originalName) === base);
      if (!match) {
        setUploads(prev => prev.map(u => u.id === eid ? { ...u, status: 'error' as const, error: `Kein passendes Video für "${sub.name}" gefunden` } : u));
        return;
      }
      await uploadSubtitle(match.id, extractLang(sub.name), null, subtitleFormat(sub.name), sub);
      setUploads(prev => prev.map(u => u.id === eid ? { ...u, status: 'done' as const, subtitleStatus: 'done' as const, resourceId: match.id } : u));
    } catch {
      setUploads(prev => prev.map(u => u.id === eid ? { ...u, status: 'error' as const, error: 'Untertitel-Upload fehlgeschlagen' } : u));
    }
  }, [folderId, qc]);

  const uploadFiles = useCallback((files: File[]) => {
    const videos = files.filter(f => isVideoFile(f.name));
    const subtitles = files.filter(f => isSubtitleFile(f.name));
    const others = files.filter(f => !isVideoFile(f.name) && !isSubtitleFile(f.name));

    const subtitleMap = new Map<string, File>();
    subtitles.forEach(f => subtitleMap.set(baseName(f.name), f));

    const queue: Array<() => Promise<void>> = [];

    others.forEach(f => queue.push(() => uploadFile(f)));

    for (const video of videos) {
      const paired = subtitleMap.get(baseName(video.name));
      if (paired) subtitleMap.delete(baseName(video.name));
      queue.push(() => uploadFile(video, paired));
    }

    for (const sub of subtitleMap.values()) {
      queue.push(() => uploadSubtitleOnly(sub));
    }

    // Max. 3 gleichzeitige Uploads — verhindert Überlastung von Tomcat und HikariCP
    const CONCURRENCY = 3;
    let idx = 0;
    const runNext = () => {
      if (idx >= queue.length) return;
      const task = queue[idx++];
      task().finally(runNext);
    };
    for (let i = 0; i < Math.min(CONCURRENCY, queue.length); i++) runNext();
  }, [uploadFile, uploadSubtitleOnly]);

  const clearCompleted = useCallback(() => setUploads(prev => prev.filter(u => u.status === 'uploading')), []);

  return { uploads, uploadFile, uploadFiles, clearCompleted };
}
