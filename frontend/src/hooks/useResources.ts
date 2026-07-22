import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as api from '../api/resources';
import type { UpdateMetadataRequest } from '../types/api';

export function useResourcesByFolder(folderId: number | undefined) {
  return useQuery({
    queryKey: ['resources', 'folder', folderId],
    queryFn: () => api.getResourcesByFolder(folderId!),
    enabled: folderId != null,
    refetchInterval: (query) => {
      const resources = query.state.data;
      if (!Array.isArray(resources)) return false;
      const pending = resources.some(
        r => r.thumbnailStatus === 'PENDING' || r.thumbnailStatus === 'PROCESSING'
      );
      return pending ? 3000 : false;
    },
  });
}

export function useResource(id: number) {
  return useQuery({
    queryKey: ['resources', id],
    queryFn: () => api.getResource(id),
    enabled: id > 0,
  });
}

export function useDeleteResource() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteResource(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['resources'] }),
  });
}

export function useUpdateMetadata() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpdateMetadataRequest }) => api.updateMetadata(id, req),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: ['resources', id] });
      qc.invalidateQueries({ queryKey: ['resources', 'folder'] });
    },
  });
}

export function useResourceContent(id: number) {
  return useQuery({
    queryKey: ['resources', id, 'content'],
    queryFn: () => api.getResourceContent(id),
    enabled: id > 0,
  });
}

export function useSubtitleTracks(resourceId: number | null) {
  return useQuery({
    queryKey: ['subtitles', resourceId],
    queryFn: () => api.getSubtitleTracks(resourceId!),
    enabled: resourceId !== null,
  });
}

export function useSaveVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, content }: { id: number; content: string }) =>
      api.saveResourceContent(id, content),
    onSuccess: (_, { id }) => qc.invalidateQueries({ queryKey: ['resources', id, 'content'] }),
  });
}

export function useMoveResource() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, newFolderId }: { id: number; newFolderId: number }) =>
      api.moveResource(id, newFolderId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['resources', 'folder'] });
    },
  });
}

export function useAiSummaries(resourceId: number) {
  return useQuery({
    queryKey: ['aiSummaries', resourceId],
    queryFn: () => api.getAiSummaries(resourceId),
    enabled: resourceId > 0,
  });
}

export function useAnalyzeResource() {
  return useMutation({
    mutationFn: ({ id, languageCode, force }: { id: number; languageCode: string; force?: boolean }) =>
      api.analyzeResource(id, languageCode, force ?? false),
  });
}

export function useActiveTranscriptionJob(resourceId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['transcriptionJobActive', resourceId],
    queryFn: () => api.hasActiveTranscriptionJob(resourceId),
    enabled: enabled && resourceId > 0,
    refetchInterval: (query) => query.state.data === true ? 3000 : false,
  });
}

export function useRetranscribeResource() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (resourceId: number) => api.retranscribeResource(resourceId),
    onSuccess: (_, resourceId) => {
      void qc.invalidateQueries({ queryKey: ['transcriptionJobActive', resourceId] });
      void qc.invalidateQueries({ queryKey: ['subtitles', resourceId] });
    },
  });
}

export function useActiveAnalysisJobs(resourceId: number) {
  return useQuery({
    queryKey: ['analysisJobs', resourceId],
    queryFn: () => api.getActiveAnalysisJobs(resourceId),
    enabled: resourceId > 0,
    staleTime: 0,
    refetchInterval: (query) => {
      const jobs = query.state.data;
      if (!Array.isArray(jobs)) return false;
      return jobs.some(j => j.status === 'PENDING' || j.status === 'RUNNING') ? 3000 : false;
    },
  });
}

export function useCreateMediaClip() {
  return useMutation({
    mutationFn: ({ id, startMs, endMs, title }: { id: number; startMs: number; endMs: number; title: string }) =>
      api.createMediaClip(id, startMs, endMs, title),
  });
}

export function useActiveClipJobs(resourceId: number) {
  return useQuery({
    queryKey: ['clipJobs', resourceId],
    queryFn: () => api.getActiveClipJobs(resourceId),
    enabled: resourceId > 0,
    staleTime: 0,
    refetchInterval: (query) => {
      const jobs = query.state.data;
      if (!Array.isArray(jobs)) return false;
      return jobs.some(j => j.status === 'PENDING' || j.status === 'RUNNING') ? 3000 : false;
    },
  });
}
