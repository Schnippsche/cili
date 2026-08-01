import {render, screen, waitFor} from '@testing-library/react';
import {vi} from 'vitest';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import PublicTestimonialDetailPage from './PublicTestimonialDetailPage';
import * as publicTestimonialsApi from '../api/publicTestimonials';
import type {PublicTestimonialDto} from '../types/api';

vi.mock('../api/publicTestimonials');

const mockTestimonial: PublicTestimonialDto = {
  id: 1,
  authorName: 'Anna',
  tags: null,
  text: 'Super Erfahrung!',
  human: true,
  animal: false,
  createdAt: '2026-05-31T10:00:00',
  updatedAt: '2026-05-31T10:00:00',
  attachments: [],
};

function renderPage(path = '/erfahrungsberichte/1') {
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  return render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/erfahrungsberichte/:id" element={<PublicTestimonialDetailPage/>}/>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
  );
}

test('lädt und zeigt den Erfahrungsbericht anhand der ID aus der Route', async () => {
  vi.mocked(publicTestimonialsApi.getPublicTestimonial).mockResolvedValue(mockTestimonial);

  renderPage();

  await waitFor(() => expect(screen.getByText('Anna')).toBeInTheDocument());
  expect(screen.getByText('Super Erfahrung!')).toBeInTheDocument();
  expect(publicTestimonialsApi.getPublicTestimonial).toHaveBeenCalledWith(1);
});

test('zeigt Fehlermeldung wenn der Bericht nicht gefunden wird', async () => {
  vi.mocked(publicTestimonialsApi.getPublicTestimonial).mockRejectedValue(new Error('404'));

  renderPage();

  await waitFor(() => expect(screen.getByText(/nicht gefunden/i)).toBeInTheDocument());
});

test('zeigt Fehlermeldung wenn die ID in der Route ungültig ist', async () => {
  renderPage('/erfahrungsberichte/abc');

  await waitFor(() => expect(screen.getByText(/nicht gefunden/i)).toBeInTheDocument());
});

test('zeigt einen Link zurück zur Übersicht', async () => {
  vi.mocked(publicTestimonialsApi.getPublicTestimonial).mockResolvedValue(mockTestimonial);

  renderPage();

  await waitFor(() => expect(screen.getByText('Anna')).toBeInTheDocument());
  expect(screen.getByRole('link', {name: /zurück zur übersicht/i})).toHaveAttribute('href', '/erfahrungsberichte');
});
