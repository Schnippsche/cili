import axiosClient from './axiosClient';
import type { CompleteUploadResponse, InitUploadRequest, UploadJobDto } from '../types/api';

/**
 * Initialize a file upload.
 * Exactly one of folderId or testimonialId must be provided in the request.
 */
export async function initUpload(req: InitUploadRequest): Promise<UploadJobDto> {
  if ((!req.folderId && !req.testimonialId) || (req.folderId && req.testimonialId)) {
    throw new Error('Exactly one of folderId or testimonialId must be provided');
  }
  const { data } = await axiosClient.post<UploadJobDto>('/uploads/init', req);
  return data;
}

export async function uploadChunk(jobId: string, chunkIndex: number, chunk: Blob): Promise<UploadJobDto> {
  const { data } = await axiosClient.put<UploadJobDto>(
    `/uploads/${jobId}/chunk/${chunkIndex}`, chunk,
    { headers: { 'Content-Type': 'application/octet-stream' } }
  );
  return data;
}

export async function completeUpload(jobId: string): Promise<CompleteUploadResponse> {
  const { data } = await axiosClient.post<CompleteUploadResponse>(`/uploads/${jobId}/complete`);
  return data;
}

export async function cancelUpload(jobId: string): Promise<void> {
  await axiosClient.delete(`/uploads/${jobId}`);
}
