import axiosClient from './axiosClient';
import type { LoginRequest, LoginResponse, UserDto } from '../types/api';

export async function login(req: LoginRequest): Promise<LoginResponse> {
  const { data } = await axiosClient.post<LoginResponse>('/auth/login', req);
  return data;
}

export async function logout(refreshToken: string): Promise<void> {
  await axiosClient.post('/auth/logout', { refreshToken });
}

export async function refreshAccessToken(refreshToken: string): Promise<LoginResponse> {
  const { data } = await axiosClient.post<LoginResponse>('/auth/refresh', { refreshToken });
  return data;
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await axiosClient.post('/auth/change-password', { currentPassword, newPassword });
}

export async function getMe(): Promise<UserDto> {
  const { data } = await axiosClient.get<UserDto>('/auth/me');
  return data;
}

export async function generateMyLabels(): Promise<Blob> {
  const { data } = await axiosClient.get('/auth/labels', { responseType: 'blob' });
  return data;
}
