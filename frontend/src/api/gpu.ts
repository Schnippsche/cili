import axiosClient from './axiosClient';
import type {GpuMetricSampleDto} from '../types/api';

export async function fetchCurrentGpuMetric(): Promise<GpuMetricSampleDto | null> {
  const response = await axiosClient.get<GpuMetricSampleDto>('/admin/gpu/current', {
    validateStatus: status => status === 200 || status === 204,
  });
  return response.status === 204 ? null : response.data;
}

export async function fetchGpuMetricHistory(hours: 1 | 6 | 24 | 168): Promise<GpuMetricSampleDto[]> {
  const {data} = await axiosClient.get<GpuMetricSampleDto[]>('/admin/gpu/history', {params: {hours}});
  return data;
}
