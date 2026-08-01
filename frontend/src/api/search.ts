import axiosClient from './axiosClient';
import type {FacetsResponse, SearchResponse} from '../types/api';

interface SearchParams {
  q?: string;
  folder?: number;
  mimeType?: string;
  page?: number;
  size?: number;
  sort?: string;
  tPage?: number;
}

export async function search(params: SearchParams): Promise<SearchResponse> {
  const {data} = await axiosClient.get<SearchResponse>('/search', {params});
  return data;
}

export async function getFacets(q?: string): Promise<FacetsResponse> {
  const {data} = await axiosClient.get<FacetsResponse>('/search/facets', {params: {q}});
  return data;
}
