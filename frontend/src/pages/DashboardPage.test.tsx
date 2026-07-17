import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { configureStore } from '@reduxjs/toolkit';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import authReducer, { setCredentials } from '../store/authSlice';
import themeReducer from '../store/themeSlice';
import DashboardPage from './DashboardPage';
import * as foldersApi from '../api/folders';
import * as resourcesApi from '../api/resources';

vi.mock('../api/folders');
vi.mock('../api/resources');
vi.mocked(foldersApi.getFolderChildren).mockResolvedValue([]);
vi.mocked(resourcesApi.getResourceFavorites).mockResolvedValue([]);

function renderPage() {
  const store = configureStore({ reducer: { auth: authReducer, theme: themeReducer } });
  store.dispatch(setCredentials({ user: { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' }, accessToken: 'at', refreshToken: 'rt' }));
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<Provider store={store}><QueryClientProvider client={qc}><MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><DashboardPage /></MemoryRouter></QueryClientProvider></Provider>);
}

describe('DashboardPage', () => {
  it('renders page heading', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Startseite')).toBeInTheDocument());
  });

  it('renders CILI in the app bar', () => {
    renderPage();
    expect(screen.getByText('CILI')).toBeInTheDocument();
  });
});
