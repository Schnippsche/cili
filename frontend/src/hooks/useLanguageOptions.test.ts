import {renderHook, waitFor} from '@testing-library/react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {createElement, type ReactNode} from 'react';
import {useLanguageOptions} from './useLanguageOptions';
import * as languagesApi from '../api/languages';

vi.mock('../api/languages');
const mockedGetLanguages = vi.mocked(languagesApi.getLanguages);

const mockLanguages = [
  {code: 'de', label: 'Deutsch', translationSupported: true},
  {code: 'en', label: 'English', translationSupported: true},
  {code: 'ar', label: 'Arabic', translationSupported: false},
];

function wrapper({children}: { children: ReactNode }) {
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  return createElement(QueryClientProvider, {client: qc}, children);
}

describe('useLanguageOptions', () => {
  beforeEach(() => vi.clearAllMocks());

  it('returns language list on success', async () => {
    mockedGetLanguages.mockResolvedValueOnce(mockLanguages);
    const {result} = renderHook(() => useLanguageOptions(), {wrapper});
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(3);
    expect(result.current.data![0].code).toBe('de');
  });

  it('sets isError on fetch failure', async () => {
    mockedGetLanguages.mockRejectedValueOnce(new Error('network'));
    const {result} = renderHook(() => useLanguageOptions(), {wrapper});
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
