import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as api from '../api/collections';
import type {
  CollectionNameRequest, CreateCollectionRequest, CreateFromTemplateRequest,
  AddToCollectionRequest, AddTestimonialToCollectionRequest,
} from '../types/api';

export function useCollections() {
  return useQuery({
    queryKey: ['collections'],
    queryFn: api.getCollections,
  });
}

export function useCollectionTemplates() {
  return useQuery({
    queryKey: ['collections', 'templates'],
    queryFn: api.getCollectionTemplates,
  });
}

export function useCollection(id: number) {
  return useQuery({
    queryKey: ['collections', id],
    queryFn: () => api.getCollection(id),
    enabled: id > 0,
  });
}

export function useCollectionItems(id: number) {
  return useQuery({
    queryKey: ['collections', id, 'items'],
    queryFn: () => api.getCollectionItems(id),
    enabled: id > 0,
  });
}

export function useCreateCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateCollectionRequest) => api.createCollection(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['collections'] }),
  });
}

export function useCreateCollectionFromTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateFromTemplateRequest) => api.createCollectionFromTemplate(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['collections'] }),
  });
}

export function useRenameCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: CollectionNameRequest }) =>
      api.renameCollection(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['collections'] }),
  });
}

export function useDeleteCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteCollection(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['collections'] }),
  });
}

export function useAddToCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ collectionId, req }: { collectionId: number; req: AddToCollectionRequest }) =>
      api.addToCollection(collectionId, req),
    onSuccess: (_, { collectionId }) => {
      qc.invalidateQueries({ queryKey: ['collections', collectionId, 'items'] });
      qc.invalidateQueries({ queryKey: ['collections'] });
    },
  });
}

export function useRemoveFromCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ collectionId, resourceId }: { collectionId: number; resourceId: number }) =>
      api.removeFromCollection(collectionId, resourceId),
    onSuccess: (_, { collectionId }) => {
      qc.invalidateQueries({ queryKey: ['collections', collectionId, 'items'] });
      qc.invalidateQueries({ queryKey: ['collections'] });
    },
  });
}

export function useCollectionTestimonials(id: number) {
  return useQuery({
    queryKey: ['collections', id, 'testimonials'],
    queryFn: () => api.getCollectionTestimonials(id),
    enabled: id > 0,
  });
}

export function useAddTestimonialToCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ collectionId, req }: { collectionId: number; req: AddTestimonialToCollectionRequest }) =>
      api.addTestimonialToCollection(collectionId, req),
    onSuccess: (_, { collectionId }) => {
      qc.invalidateQueries({ queryKey: ['collections', collectionId, 'testimonials'] });
      qc.invalidateQueries({ queryKey: ['collections'] });
    },
  });
}

export function useRemoveTestimonialFromCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ collectionId, testimonialId }: { collectionId: number; testimonialId: number }) =>
      api.removeTestimonialFromCollection(collectionId, testimonialId),
    onSuccess: (_, { collectionId }) => {
      qc.invalidateQueries({ queryKey: ['collections', collectionId, 'testimonials'] });
      qc.invalidateQueries({ queryKey: ['collections'] });
    },
  });
}

export function useCopyCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) => api.copyCollection(id, name),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['collections'] }),
  });
}
