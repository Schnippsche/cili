import axiosClient from './axiosClient';
import type { VersionResponse } from '../types/api';

export async function getVersion(): Promise<VersionResponse> {
  const { data } = await axiosClient.get<VersionResponse>('/public/version');
  return data;
}
