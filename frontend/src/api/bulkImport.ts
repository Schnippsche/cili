import axiosClient from './axiosClient';
import type { CreateBulkImportRequest, CreateBulkImportResponse, BulkImportJobDto } from '../types/api';

export async function createBulkImport(req: CreateBulkImportRequest): Promise<CreateBulkImportResponse> {
  const { data } = await axiosClient.post<CreateBulkImportResponse>('/admin/bulk-imports', req);
  return data;
}

export async function getBulkImportJob(jobId: string): Promise<BulkImportJobDto> {
  const { data } = await axiosClient.get<BulkImportJobDto>(`/admin/bulk-imports/${jobId}`);
  return data;
}

export async function failBulkImportItem(jobId: string, itemId: number, errorMessage: string): Promise<void> {
  await axiosClient.post(`/admin/bulk-imports/${jobId}/items/${itemId}/fail`, { errorMessage });
}
