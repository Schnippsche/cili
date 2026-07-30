import { useCallback, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { completeUpload, initUpload, uploadChunk } from '../api/upload';

const CHUNK_SIZE = 5 * 1024 * 1024;

export interface TestimonialUploadState {
  progress: number;
  status: 'uploading' | 'done' | 'error';
  error?: string;
}

export function useTestimonialMediaUpload(testimonialId: number) {
  const [uploads, setUploads] = useState<Map<string, TestimonialUploadState>>(new Map());
  const qc = useQueryClient();

  const update = (fileName: string, patch: Partial<TestimonialUploadState>) =>
    setUploads(prev => {
      const copy = new Map(prev);
      const state = copy.get(fileName) ?? { progress: 0, status: 'uploading' as const };
      copy.set(fileName, { ...state, ...patch });
      return copy;
    });

  const uploadFile = useCallback(async (file: File) => {
    update(file.name, { progress: 0, status: 'uploading' });
    try {
      const job = await initUpload({
        fileName: file.name,
        mimeType: file.type || 'application/octet-stream',
        totalSize: file.size,
        chunkSize: CHUNK_SIZE,
        testimonialId,
        fileLastModified: file.lastModified,
      });
      for (let i = 0; i < job.chunksTotal; i++) {
        await uploadChunk(job.jobId, i, file.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE));
        update(file.name, { progress: Math.round(((i + 1) / job.chunksTotal) * 100) });
      }
      await completeUpload(job.jobId);
      update(file.name, { status: 'done', progress: 100 });
      qc.invalidateQueries({ queryKey: ['testimonial', testimonialId] });
      qc.invalidateQueries({ queryKey: ['testimonials'] });
    } catch (err) {
      update(file.name, {
        status: 'error',
        error: (err as { message?: string })?.message ?? 'Upload fehlgeschlagen',
      });
    }
  }, [testimonialId, qc]);

  const retry = useCallback(async (fileName: string) => {
    const state = uploads.get(fileName);
    if (!state || state.status !== 'error') return;

    // Find the original file - we need a way to track it
    // For now, this is a placeholder that requires the file to be passed
    // In practice, the component should handle retry differently
  }, [uploads]);

  return { uploads, uploadFile, retry };
}
