import axiosClient from './axiosClient';
import type { AclEntryDto, CreateGroupRequest, CreateUserRequest, GroupDto, JobStatus, LogResponse, PageResponse, ProcessingJobDto, TelegramSourceDto, UpdateGroupRequest, UpdateUserRequest, UserDto } from '../types/api';

export interface FolderItem { id: number; name: string; path: string; }

export async function listAllFolders(): Promise<FolderItem[]> {
  const { data } = await axiosClient.get<PageResponse<FolderItem>>('/acl/folders', { params: { size: 500 } });
  return data.content;
}

export async function listGroupAclEntries(groupId: number): Promise<AclEntryDto[]> {
  const { data } = await axiosClient.get<AclEntryDto[]>(`/acl/groups/${groupId}/entries`);
  return data;
}

export async function createFolderAclEntry(
  folderId: number,
  subjectId: number,
  permission: string,
  grantType: 'ALLOW' | 'DENY',
  inheritable: boolean,
): Promise<AclEntryDto> {
  const { data } = await axiosClient.post<AclEntryDto>(`/acl/folders/${folderId}/entries`, {
    subjectType: 'GROUP', subjectId, permission, grantType, inheritable,
  });
  return data;
}

export async function deleteAclEntry(entryId: number): Promise<void> {
  await axiosClient.delete(`/acl/entries/${entryId}`);
}

export async function createTestimonialsAclEntry(
  groupId: number,
  permission: 'READ' | 'WRITE' | 'DELETE',
): Promise<AclEntryDto> {
  const { data } = await axiosClient.post<AclEntryDto>('/acl/testimonials/entries', {
    subjectType: 'GROUP',
    subjectId: groupId,
    permission,
  });
  return data;
}

export async function createCollectionsAclEntry(
  groupId: number,
  permission: 'MANAGE_TEMPLATES',
): Promise<AclEntryDto> {
  const { data } = await axiosClient.post<AclEntryDto>('/acl/collections/entries', {
    subjectType: 'GROUP',
    subjectId: groupId,
    permission,
  });
  return data;
}

export async function listUsers(page = 0, size = 20): Promise<PageResponse<UserDto>> {
  const { data } = await axiosClient.get<PageResponse<UserDto>>('/admin/users', { params: { page, size } });
  return data;
}

export async function createUser(req: CreateUserRequest): Promise<UserDto> {
  const { data } = await axiosClient.post<UserDto>('/admin/users', req);
  return data;
}

export async function updateUser(id: number, req: UpdateUserRequest): Promise<UserDto> {
  const { data } = await axiosClient.put<UserDto>(`/admin/users/${id}`, req);
  return data;
}

export async function deleteUser(id: number): Promise<void> {
  await axiosClient.delete(`/admin/users/${id}`);
}

export async function listUserGroups(userId: number): Promise<GroupDto[]> {
  const { data } = await axiosClient.get<GroupDto[]>(`/admin/users/${userId}/groups`);
  return data;
}

export async function listGroups(page = 0, size = 20): Promise<PageResponse<GroupDto>> {
  const { data } = await axiosClient.get<PageResponse<GroupDto>>('/admin/groups', { params: { page, size } });
  return data;
}

export async function createGroup(req: CreateGroupRequest): Promise<GroupDto> {
  const { data } = await axiosClient.post<GroupDto>('/admin/groups', req);
  return data;
}

export async function updateGroup(id: number, req: UpdateGroupRequest): Promise<GroupDto> {
  const { data } = await axiosClient.put<GroupDto>(`/admin/groups/${id}`, req);
  return data;
}

export async function deleteGroup(id: number): Promise<void> {
  await axiosClient.delete(`/admin/groups/${id}`);
}

export async function listGroupMembers(groupId: number): Promise<UserDto[]> {
  const { data } = await axiosClient.get<UserDto[]>(`/admin/groups/${groupId}/members`);
  return data;
}

export async function addGroupMember(groupId: number, userId: number): Promise<void> {
  await axiosClient.post(`/admin/groups/${groupId}/members`, { userId });
}

export async function removeGroupMember(groupId: number, userId: number): Promise<void> {
  await axiosClient.delete(`/admin/groups/${groupId}/members/${userId}`);
}

export async function listJobs(page = 0, size = 50, status?: JobStatus): Promise<PageResponse<ProcessingJobDto>> {
  const params: Record<string, unknown> = { page, size };
  if (status) params.status = status;
  const { data } = await axiosClient.get<PageResponse<ProcessingJobDto>>('/admin/jobs', { params });
  return data;
}

export async function getJob(id: number): Promise<ProcessingJobDto> {
  const { data } = await axiosClient.get<ProcessingJobDto>(`/admin/jobs/${id}`);
  return data;
}

export async function deleteJob(id: number): Promise<void> {
  await axiosClient.delete(`/admin/jobs/${id}`);
}

export async function deleteCompletedJobs(): Promise<void> {
  await axiosClient.delete('/admin/jobs/completed');
}

export async function listTelegramSources(): Promise<TelegramSourceDto[]> {
  const { data } = await axiosClient.get<TelegramSourceDto[]>('/admin/jobs/telegram-import/sources');
  return data;
}

export async function triggerTelegramImport(source: string): Promise<ProcessingJobDto> {
  const { data } = await axiosClient.post<ProcessingJobDto>(`/admin/jobs/telegram-import/trigger/${source}`);
  return data;
}

export async function fetchLogs(lines = 500): Promise<LogResponse> {
  const { data } = await axiosClient.get<LogResponse>('/admin/logs', { params: { lines } });
  return data;
}

export async function generateUserLabels(userId: number): Promise<Blob> {
  const { data } = await axiosClient.get(`/admin/users/${userId}/labels`, { responseType: 'blob' });
  return data;
}
