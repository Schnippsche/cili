import axiosClient from './axiosClient';
import type { LanguageOptionDto } from '../types/api';

export async function getLanguages(): Promise<LanguageOptionDto[]> {
  const { data } = await axiosClient.get<LanguageOptionDto[]>('/languages');
  return data;
}
