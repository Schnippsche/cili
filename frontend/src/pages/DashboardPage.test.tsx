import {render, screen, waitFor} from '@testing-library/react';
import {Provider} from 'react-redux';
import {MemoryRouter} from 'react-router-dom';
import {configureStore} from '@reduxjs/toolkit';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import authReducer, {setCredentials} from '../store/authSlice';
import themeReducer from '../store/themeSlice';
import DashboardPage from './DashboardPage';
import * as foldersApi from '../api/folders';
import * as resourcesApi from '../api/resources';
import type {RecentResourceDto} from '../types/api';

vi.mock('../api/folders');
vi.mock('../api/resources');
vi.mocked(foldersApi.getFolderChildren).mockResolvedValue([]);
vi.mocked(resourcesApi.getResourceFavorites).mockResolvedValue([]);
vi.mocked(resourcesApi.getRecentResources).mockResolvedValue([]);

function recentItem(id: number, name: string, folderName: string): RecentResourceDto {
  return {
    folderName,
    resource: {
      id, folderId: 3, originalName: name, storedName: 's', mimeType: 'application/pdf',
      size: 1, checksum: null, uploaderId: 1, storageType: 'LOCAL', fileDate: null,
      sortOrder: null, createdAt: '2026-08-27T10:00:00Z', updatedAt: '2026-08-27T10:00:00Z',
      metadata: null, thumbnailStatus: null, hasAnalyzableSubtitles: false,
    },
  };
}

function renderPage() {
  const store = configureStore({reducer: {auth: authReducer, theme: themeReducer}});
  store.dispatch(setCredentials({
    user: {
      id: 1,
      username: 'admin',
      displayName: 'Admin',
      role: 'ADMIN'
    }, accessToken: 'at', refreshToken: 'rt'
  }));
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  return render(<Provider store={store}><QueryClientProvider client={qc}><MemoryRouter future={{
    v7_startTransition: true,
    v7_relativeSplatPath: true
  }}><DashboardPage/></MemoryRouter></QueryClientProvider></Provider>);
}

describe('DashboardPage', () => {
  it('renders page heading', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Startseite')).toBeInTheDocument());
  });

  it('renders Cili Academy in the app bar', () => {
    renderPage();
    expect(screen.getByText('Cili Academy')).toBeInTheDocument();
  });

  it('shows the most recent resources under a "Aktuellste Dateien" heading', async () => {
    vi.mocked(resourcesApi.getRecentResources).mockResolvedValue([
      recentItem(1, 'bericht.pdf', 'Projekte'),
      recentItem(2, 'notiz.pdf', 'Notizen'),
    ]);
    renderPage();

    await waitFor(() => expect(screen.getByText('Aktuellste Dateien')).toBeInTheDocument());
    expect(screen.getByText('bericht.pdf')).toBeInTheDocument();
    expect(screen.getByText('notiz.pdf')).toBeInTheDocument();
  });

  it('hides the recent section when there are no recent resources', async () => {
    vi.mocked(resourcesApi.getRecentResources).mockResolvedValue([]);
    renderPage();

    await waitFor(() => expect(screen.getByText('Startseite')).toBeInTheDocument());
    expect(screen.queryByText('Aktuellste Dateien')).not.toBeInTheDocument();
  });
});
