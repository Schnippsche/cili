import axiosClient from './axiosClient';

export const getReportPreview = (q: string): Promise<string> =>
  axiosClient
    .get<string>('/testimonials/report/preview', {
      params: { q: q || undefined },
      responseType: 'text',
    })
    .then(r => r.data);

export const getCollectionReportPreview = (collectionId: number): Promise<string> =>
  axiosClient
    .get<string>(`/collections/${collectionId}/report/preview`, { responseType: 'text' })
    .then(r => r.data);
