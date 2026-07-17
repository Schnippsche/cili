import axiosClient from './axiosClient';
import type { PublicTestimonialDto } from '../types/api';

export function listPublicTestimonials(): Promise<PublicTestimonialDto[]> {
  return axiosClient
    .get<PublicTestimonialDto[]>('/public/testimonials')
    .then(r => r.data);
}

export function publicImageUrl(resourceId: number, size: 'small' | 'large'): string {
  return `/api/public/testimonials/images/${resourceId}?size=${size}`;
}
