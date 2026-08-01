import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {vi} from 'vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import PublicTestimonialsPage from './PublicTestimonialsPage';
import * as publicTestimonialsApi from '../api/publicTestimonials';
import type {SpringPage, PublicTestimonialDto} from '../types/api';

vi.mock('../api/publicTestimonials');

const emptyPage: SpringPage<PublicTestimonialDto> = {
  content: [],
  page: {totalElements: 0, totalPages: 0, number: 0, size: 25},
};

function renderPage() {
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  return render(
      <QueryClientProvider client={qc}>
        <PublicTestimonialsPage/>
      </QueryClientProvider>
  );
}

test('Suchfeld bleibt beim Nachladen der Ergebnisse sichtbar und fokussiert', async () => {
  vi.mocked(publicTestimonialsApi.listPublicTestimonials)
      .mockResolvedValueOnce(emptyPage) // initialer Seitenaufbau
      .mockImplementation(() => new Promise(() => {
      })); // jede Suche danach bleibt "am Laden" hängen

  renderPage();

  const input = await screen.findByPlaceholderText(/suchen nach/i);
  await userEvent.type(input, 'A');

  // Während die durch die Eingabe ausgelöste neue Suche noch lädt, darf das
  // Suchfeld nicht verschwinden (Remount würde den Fokus kosten).
  await waitFor(() => {
    expect(screen.getByPlaceholderText(/suchen nach/i)).toBe(input);
  });
  expect(document.activeElement).toBe(input);
});
