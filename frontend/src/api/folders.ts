import axiosClient from './axiosClient';
import type { BreadcrumbItemDto, CreateFolderRequest, EffectivePermissionsResponse, FolderDto, UpdateFolderRequest } from '../types/api';

export async function getFolderChildren(folderId?: number): Promise<FolderDto[]> {
  const url = folderId != null ? `/folders/${folderId}/children` : '/folders/root';
  const { data } = await axiosClient.get<FolderDto[]>(url);
  return data;
}

export async function getFolder(id: number): Promise<FolderDto> {
  const { data } = await axiosClient.get<FolderDto>(`/folders/${id}`);
  return data;
}

export async function getFolderBreadcrumb(id: number): Promise<BreadcrumbItemDto[]> {
  const { data } = await axiosClient.get<BreadcrumbItemDto[]>(`/folders/${id}/breadcrumb`);
  return data;
}

export async function createFolder(req: CreateFolderRequest): Promise<FolderDto> {
  const { data } = await axiosClient.post<FolderDto>('/folders', req);
  return data;
}

export async function updateFolder(id: number, req: UpdateFolderRequest): Promise<FolderDto> {
  const { data } = await axiosClient.patch<FolderDto>(`/folders/${id}`, req);
  return data;
}

export async function trashFolder(id: number): Promise<void> {
  await axiosClient.delete(`/folders/${id}`);
}

export async function getEffectivePermissions(folderId: number): Promise<EffectivePermissionsResponse> {
  const { data } = await axiosClient.get<EffectivePermissionsResponse>(`/acl/folders/${folderId}/effective-permissions`);
  return data;
}

export async function addFolderFavorite(id: number): Promise<void> {
  await axiosClient.post(`/folders/${id}/favorite`);
}

export async function removeFolderFavorite(id: number): Promise<void> {
  await axiosClient.delete(`/folders/${id}/favorite`);
}

export async function getTrash(): Promise<FolderDto[]> {
  const { data } = await axiosClient.get<FolderDto[]>('/folders/trash');
  return data;
}

export async function restoreFolder(id: number): Promise<FolderDto> {
  const { data } = await axiosClient.post<FolderDto>(`/folders/${id}/restore`);
  return data;
}

export async function purgeFolder(id: number): Promise<void> {
  await axiosClient.delete(`/folders/${id}/purge`);
}

export async function moveFolder(id: number, newParentId: number): Promise<FolderDto> {
  const { data } = await axiosClient.put<FolderDto>(`/folders/${id}/move`, null, {
    params: { newParentId },
  });
  return data;
}
