import axiosClient from './axiosClient';
import type {
  TestimonialDto,
  SpringPage,
} from '../types/api';

export interface TestimonialFormData {
  authorName: string;
  tags: string | null;
  text: string;
  human: boolean;
  animal: boolean;
  images?: File[];
  deleteAttachmentIds?: number[];
}

export async function listTestimonials(params?: {
  q?: string;
  source?: 'Mensch' | 'Tier';
  page?: number;
  size?: number;
}): Promise<SpringPage<TestimonialDto>> {
  const { data } = await axiosClient.get<SpringPage<TestimonialDto>>(
    '/testimonials',
    { params },
  );
  return data;
}

function buildFormData(form: TestimonialFormData): FormData {
  const fd = new FormData();
  fd.append('authorName', form.authorName);
  if (form.tags) fd.append('tags', form.tags);
  fd.append('text', form.text);
  fd.append('human', String(form.human));
  fd.append('animal', String(form.animal));
  form.images?.forEach(f => fd.append('images', f));
  form.deleteAttachmentIds?.forEach(id => fd.append('deleteAttachmentIds', String(id)));
  return fd;
}

export async function createTestimonial(form: TestimonialFormData): Promise<TestimonialDto> {
  const { data } = await axiosClient.post<TestimonialDto>('/testimonials', buildFormData(form));
  return data;
}

export async function updateTestimonial(id: number, form: TestimonialFormData): Promise<TestimonialDto> {
  const { data } = await axiosClient.put<TestimonialDto>(`/testimonials/${id}`, buildFormData(form));
  return data;
}

export async function getTestimonial(id: number): Promise<TestimonialDto> {
  const { data } = await axiosClient.get<TestimonialDto>(`/testimonials/${id}`);
  return data;
}

export async function deleteTestimonial(id: number): Promise<void> {
  await axiosClient.delete(`/testimonials/${id}`);
}
