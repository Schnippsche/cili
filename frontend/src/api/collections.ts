import axiosClient from './axiosClient';
import type {
  AddTestimonialToCollectionRequest,
  AddToCollectionRequest,
  CollectionDto,
  CollectionNameRequest,
  CreateCollectionRequest,
  CreateFromTemplateRequest,
  ResourceDto,
  TestimonialDto,
} from '../types/api';

export async function getCollections(): Promise<CollectionDto[]> {
  const {data} = await axiosClient.get<CollectionDto[]>('/collections');
  return data;
}

export async function getCollectionTemplates(): Promise<CollectionDto[]> {
  const {data} = await axiosClient.get<CollectionDto[]>('/collections/templates');
  return data;
}

export async function getCollection(id: number): Promise<CollectionDto> {
  const {data} = await axiosClient.get<CollectionDto>(`/collections/${id}`);
  return data;
}

export async function createCollection(req: CreateCollectionRequest): Promise<CollectionDto> {
  const {data} = await axiosClient.post<CollectionDto>('/collections', req);
  return data;
}

export async function createCollectionFromTemplate(req: CreateFromTemplateRequest): Promise<CollectionDto> {
  const {data} = await axiosClient.post<CollectionDto>('/collections/from-template', req);
  return data;
}

export async function renameCollection(id: number, req: CollectionNameRequest): Promise<CollectionDto> {
  const {data} = await axiosClient.patch<CollectionDto>(`/collections/${id}`, req);
  return data;
}

export async function deleteCollection(id: number): Promise<void> {
  await axiosClient.delete(`/collections/${id}`);
}

export async function getCollectionItems(id: number): Promise<ResourceDto[]> {
  const {data} = await axiosClient.get<ResourceDto[]>(`/collections/${id}/items`);
  return data;
}

export async function addToCollection(id: number, req: AddToCollectionRequest): Promise<void> {
  await axiosClient.post(`/collections/${id}/items`, req);
}

export async function removeFromCollection(id: number, resourceId: number): Promise<void> {
  await axiosClient.delete(`/collections/${id}/items/${resourceId}`);
}

export async function getCollectionTestimonials(id: number): Promise<TestimonialDto[]> {
  const {data} = await axiosClient.get<TestimonialDto[]>(`/collections/${id}/testimonials`);
  return data;
}

export async function addTestimonialToCollection(id: number, req: AddTestimonialToCollectionRequest): Promise<void> {
  await axiosClient.post(`/collections/${id}/testimonials`, req);
}

export async function removeTestimonialFromCollection(id: number, testimonialId: number): Promise<void> {
  await axiosClient.delete(`/collections/${id}/testimonials/${testimonialId}`);
}

export async function copyCollection(id: number, name: string): Promise<CollectionDto> {
  const {data} = await axiosClient.post<CollectionDto>(`/collections/${id}/copy`, {name});
  return data;
}
