import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {vi} from 'vitest';
import {Provider} from 'react-redux';
import {MemoryRouter} from 'react-router-dom';
import {configureStore} from '@reduxjs/toolkit';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import SearchPage from './SearchPage';
import * as searchApi from '../api/search';
import * as shareApi from '../api/share';
import authReducer, {setCredentials} from '../store/authSlice';
import themeReducer from '../store/themeSlice';
import type {SearchHitDto, SearchResponse, TestimonialSearchHitDto} from '../types/api';

vi.mock('../api/search');
vi.mock('../api/share');

const shareableHit: SearchHitDto = {
  resourceId: 101,
  name: 'freigebbar.pdf',
  title: null,
  mimeType: 'application/pdf',
  size: 2048,
  folderId: 5,
  folderPath: '/Dokumente/',
  uploadedAt: '2026-08-01T10:00:00',
  score: 1,
  snippets: [],
  thumbnailStatus: null,
  storedName: 'uuid-101',
  canShare: true,
};

const lockedHit: SearchHitDto = {
  ...shareableHit,
  resourceId: 102,
  name: 'gesperrt.pdf',
  storedName: 'uuid-102',
  canShare: false,
};

const testimonialHit: TestimonialSearchHitDto = {
  id: 7,
  authorName: 'Erika Musterfrau',
  tags: null,
  text: 'Ein toller Bericht über meine Erfahrung.',
  human: true,
  animal: false,
  createdAt: '2026-08-01T09:00:00',
};

const searchResponse: SearchResponse = {
  hits: [shareableHit, lockedHit],
  totalHits: 2,
  page: 0,
  size: 20,
  testimonialHits: [testimonialHit],
  testimonialTotalHits: 1,
  testimonialPage: 0,
  testimonialSize: 10,
};

function renderPage() {
  vi.mocked(searchApi.search).mockResolvedValue(searchResponse);
  vi.mocked(shareApi.getShare).mockResolvedValue(null);
  vi.mocked(shareApi.getShareConfig).mockResolvedValue({validityDays: 90});
  const store = configureStore({reducer: {auth: authReducer, theme: themeReducer}});
  store.dispatch(setCredentials({
    user: {id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN'},
    accessToken: 'at', refreshToken: 'rt',
  }));
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  render(
      <Provider store={store}>
        <QueryClientProvider client={qc}>
          <MemoryRouter future={{v7_startTransition: true, v7_relativeSplatPath: true}}>
            <SearchPage/>
          </MemoryRouter>
        </QueryClientProvider>
      </Provider>
  );
}

async function typeQuery(q: string) {
  const input = screen.getByPlaceholderText('Suchbegriff(e) eingeben…');
  fireEvent.change(input, {target: {value: q}});
  await waitFor(() => expect(searchApi.search).toHaveBeenCalled());
}

describe('SearchPage — Freigabelink-Button', () => {
  test('zeigt Freigabe-Button nur bei Datei-Treffern mit canShare', async () => {
    renderPage();
    await typeQuery('doc');
    await waitFor(() => expect(screen.getByText('freigebbar.pdf')).toBeInTheDocument());

    const shareableItem = screen.getByText('freigebbar.pdf').closest('li')!;
    expect(within(shareableItem).getByRole('button', {name: /freigabelink erzeugen/i})).toBeInTheDocument();

    const lockedItem = screen.getByText('gesperrt.pdf').closest('li')!;
    expect(within(lockedItem).queryByRole('button', {name: /freigabelink erzeugen/i})).not.toBeInTheDocument();
  });

  test('Freigabe-Button bei Datei-Treffer öffnet ShareLinkDialog mit korrekter resourceId', async () => {
    renderPage();
    await typeQuery('doc');
    await waitFor(() => expect(screen.getByText('freigebbar.pdf')).toBeInTheDocument());

    const shareableItem = screen.getByText('freigebbar.pdf').closest('li')!;
    fireEvent.click(within(shareableItem).getByRole('button', {name: /freigabelink erzeugen/i}));

    expect(screen.getByText('Freigabelink')).toBeInTheDocument();
    await waitFor(() => expect(shareApi.getShare).toHaveBeenCalledWith(101));
  });

  test('zeigt Freigabe-Button immer bei Erfahrungsbericht-Treffern und öffnet TestimonialShareDialog', async () => {
    renderPage();
    await typeQuery('doc');
    await waitFor(() => expect(screen.getByText('Erika Musterfrau')).toBeInTheDocument());

    const testimonialItem = screen.getByText('Erika Musterfrau').closest('li')!;
    fireEvent.click(within(testimonialItem).getByRole('button', {name: /freigabelink erzeugen/i}));

    expect(screen.getByText(/erfahrungsbericht teilen/i)).toBeInTheDocument();
  });
});
