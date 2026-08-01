import {useCallback, useState} from 'react';
import {completeUpload, initUpload, uploadChunk} from '../api/upload';
import {createBulkImport, failBulkImportItem, getBulkImportJob} from '../api/bulkImport';
import type {BulkImportEntry, BulkImportItemDto, BulkImportItemStatus} from '../types/api';

const CHUNK_SIZE = 5 * 1024 * 1024;
const CONCURRENCY = 3;
const MAX_RETRIES = 3;

export interface BulkImportProgressItem {
  id: number;
  relativePath: string;
  status: BulkImportItemStatus;
  skipReason?: string | null;
  errorMessage?: string | null;
}

// "Quelle/sub/video1.mp4" → "Quelle" (the synthetic root folder name that the
// browser prepends via webkitRelativePath for <input webkitdirectory>)
export function rootSegmentOf(file: File): string {
  return file.webkitRelativePath.split('/')[0] ?? '';
}

// "Quelle/sub/video1.mp4" → "sub/video1.mp4" (strips the synthetic root folder name
// that the browser prepends via webkitRelativePath for <input webkitdirectory>)
function relativePathWithoutRoot(file: File): string {
  const parts = file.webkitRelativePath.split('/');
  return parts.slice(1).join('/');
}

export function useBulkImportUpload() {
  const [jobId, setJobId] = useState<string | null>(null);
  const [items, setItems] = useState<BulkImportProgressItem[]>([]);
  const [running, setRunning] = useState(false);

  const updateItem = (id: number, patch: Partial<BulkImportProgressItem>) =>
      setItems(prev => prev.map(i => (i.id === id ? {...i, ...patch} : i)));

  const uploadOne = useCallback(async (currentJobId: string, item: BulkImportItemDto, file: File) => {
    updateItem(item.id, {status: 'UPLOADING'});
    let lastError: unknown;
    for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
      try {
        const uploadJob = await initUpload({
          fileName: file.name,
          mimeType: file.type || 'application/octet-stream',
          totalSize: file.size,
          chunkSize: CHUNK_SIZE,
          folderId: item.resolvedFolderId!,
          fileLastModified: file.lastModified,
          bulkImportItemId: item.id,
        });
        for (let i = 0; i < uploadJob.chunksTotal; i++) {
          await uploadChunk(uploadJob.jobId, i, file.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE));
        }
        await completeUpload(uploadJob.jobId);
        updateItem(item.id, {status: 'DONE'});
        return;
      } catch (err) {
        lastError = err;
      }
    }
    const message = (lastError as { message?: string })?.message ?? 'Upload fehlgeschlagen';
    await failBulkImportItem(currentJobId, item.id, message).catch(() => undefined);
    updateItem(item.id, {status: 'FAILED', errorMessage: message});
  }, []);

  const runQueue = useCallback((currentJobId: string, pending: Array<{
    item: BulkImportItemDto;
    file: File
  }>) => {
    setRunning(true);
    return new Promise<void>(resolve => {
      if (pending.length === 0) {
        setRunning(false);
        resolve();
        return;
      }
      let idx = 0;
      let inFlight = 0;
      const runNext = () => {
        if (idx >= pending.length) {
          if (inFlight === 0) {
            setRunning(false);
            resolve();
          }
          return;
        }
        const {item, file} = pending[idx++];
        inFlight++;
        uploadOne(currentJobId, item, file).finally(() => {
          inFlight--;
          runNext();
        });
      };
      for (let i = 0; i < Math.min(CONCURRENCY, pending.length); i++) runNext();
    });
  }, [uploadOne]);

  const start = useCallback(async (targetFolderId: number, rootName: string, files: File[]) => {
    const entries: BulkImportEntry[] = files.map(f => ({
      relativePath: relativePathWithoutRoot(f),
      fileSize: f.size,
      mimeType: f.type || 'application/octet-stream',
      fileLastModified: f.lastModified,
    }));
    const response = await createBulkImport({targetFolderId, rootName, entries});
    setJobId(response.jobId);
    setItems(response.items.map(i => ({
      id: i.id,
      relativePath: i.relativePath,
      status: i.status,
      skipReason: i.skipReason,
      errorMessage: i.errorMessage,
    })));

    const fileByPath = new Map(files.map(f => [relativePathWithoutRoot(f), f]));
    const pending = response.items
    .filter(i => i.status === 'PENDING')
    .map(item => ({item, file: fileByPath.get(item.relativePath)}))
    .filter((p): p is { item: BulkImportItemDto; file: File } => p.file !== undefined);

    await runQueue(response.jobId, pending);
  }, [runQueue]);

  const resume = useCallback(async (existingJobId: string, files: File[]) => {
    const jobDto = await getBulkImportJob(existingJobId);
    setJobId(existingJobId);
    setItems(jobDto.items.map(i => ({
      id: i.id,
      relativePath: i.relativePath,
      status: i.status,
      skipReason: i.skipReason,
      errorMessage: i.errorMessage,
    })));

    const fileByPath = new Map(files.map(f => [relativePathWithoutRoot(f), f]));
    const pending = jobDto.items
    .filter(i => i.status === 'PENDING' || i.status === 'UPLOADING' || i.status === 'FAILED')
    .map(item => ({item, file: fileByPath.get(item.relativePath)}))
    .filter((p): p is { item: BulkImportItemDto; file: File } => p.file !== undefined);

    await runQueue(existingJobId, pending);
  }, [runQueue]);

  return {jobId, items, running, start, resume};
}
