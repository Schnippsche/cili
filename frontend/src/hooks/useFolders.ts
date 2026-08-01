import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import * as api from '../api/folders';
import type {CreateFolderRequest, UpdateFolderRequest} from '../types/api';

export function useFolderChildren(folderId?: number) {
  return useQuery({
    queryKey: ['folders', 'children', folderId ?? 'root'],
    queryFn: () => api.getFolderChildren(folderId),
  });
}

export function useFolder(id: number) {
  return useQuery({
    queryKey: ['folders', id],
    queryFn: () => api.getFolder(id),
    enabled: id > 0,
  });
}

export function useFolderBreadcrumb(id: number) {
  return useQuery({
    queryKey: ['folders', id, 'breadcrumb'],
    queryFn: () => api.getFolderBreadcrumb(id),
    enabled: id > 0,
  });
}

export function useCreateFolder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateFolderRequest) => api.createFolder(req),
    onSuccess: (folder) => qc.invalidateQueries({queryKey: ['folders', 'children', folder.parentId ?? 'root']}),
  });
}

export function useUpdateFolder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({id, req}: { id: number; req: UpdateFolderRequest }) => api.updateFolder(id, req),
    onSuccess: (_, {id}) => {
      qc.invalidateQueries({queryKey: ['folders', id]});
      qc.invalidateQueries({queryKey: ['folders', 'children']});
    },
  });
}

export function useFolderPermissions(folderId: number | undefined) {
  return useQuery({
    queryKey: ['folders', folderId, 'permissions'],
    queryFn: () => api.getEffectivePermissions(folderId!),
    enabled: folderId != null,
  });
}

export function useTrashFolder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.trashFolder(id),
    onSuccess: () => qc.invalidateQueries({queryKey: ['folders', 'children']}),
  });
}

export function useTrash() {
  return useQuery({
    queryKey: ['folders', 'trash'],
    queryFn: api.getTrash,
  });
}

export function useRestoreFolder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.restoreFolder(id),
    onSuccess: () => {
      qc.invalidateQueries({queryKey: ['folders', 'trash']});
      qc.invalidateQueries({queryKey: ['folders', 'children']});
    },
  });
}

export function usePurgeFolder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.purgeFolder(id),
    onSuccess: () => qc.invalidateQueries({queryKey: ['folders', 'trash']}),
  });
}

export function useMoveFolder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({id, newParentId}: { id: number; newParentId: number }) =>
        api.moveFolder(id, newParentId),
    onSuccess: () => {
      qc.invalidateQueries({queryKey: ['folders', 'children']});
      qc.invalidateQueries({queryKey: ['folders']}); // invalidiert breadcrumbs + folder details
    },
  });
}
