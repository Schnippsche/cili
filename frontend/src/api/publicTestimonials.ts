import axiosClient from './axiosClient';
import type { PublicTestimonialDto, SpringPage, SubtitleTrackDto } from '../types/api';

export function listPublicTestimonials(params?: {
  q?: string;
  source?: 'Mensch' | 'Tier';
  page?: number;
  size?: number;
}): Promise<SpringPage<PublicTestimonialDto>> {
  return axiosClient
    .get<SpringPage<PublicTestimonialDto>>('/public/testimonials', { params })
    .then(r => r.data);
}

export function publicImageUrl(resourceId: number, size: 'small' | 'large'): string {
  return `/api/public/testimonials/images/${resourceId}?size=${size}`;
}

export function getPublicStreamUrl(testimonialId: number, resourceId: number): string {
  return `/api/public/testimonials/${testimonialId}/stream/${resourceId}`;
}

export function getPublicSubtitleTracks(testimonialId: number, resourceId: number): Promise<SubtitleTrackDto[]> {
  return axiosClient
    .get<SubtitleTrackDto[]>(`/public/testimonials/${testimonialId}/subtitles/${resourceId}`)
    .then(r => r.data);
}

export function getPublicSubtitleUrl(testimonialId: number, resourceId: number, trackId: number): string {
  return `/api/public/testimonials/${testimonialId}/subtitles/${resourceId}/${trackId}`;
}
