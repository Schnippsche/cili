import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {Provider} from 'react-redux';
import {MemoryRouter} from 'react-router-dom';
import {configureStore} from '@reduxjs/toolkit';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import authReducer, {setCredentials} from '../store/authSlice';
import themeReducer from '../store/themeSlice';
import TrashPage from './TrashPage';
import * as foldersApi from '../api/folders';

vi.mock('../api/folders');

const trashedFolder = {
  id: 5, name: 'Altprojekte', parentId: null, path: '/Altprojekte/',
  description: null, trashed: true, trashedAt: '2026-05-19T14:30:00',
  createdBy: 1, createdAt: '', updatedAt: '',
};

function renderPage() {
  vi.mocked(foldersApi.getTrash).mockResolvedValue([trashedFolder]);
  const store = configureStore({reducer: {auth: authReducer, theme: themeReducer}});
  store.dispatch(setCredentials({
    user: {id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN'},
    accessToken: 'at', refreshToken: 'rt',
  }));
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  return render(
      <Provider store={store}>
        <QueryClientProvider client={qc}>
          <MemoryRouter future={{v7_startTransition: true, v7_relativeSplatPath: true}}>
            <TrashPage/>
          </MemoryRouter>
        </QueryClientProvider>
      </Provider>
  );
}

describe('TrashPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders page heading', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByRole('heading', {name: 'Papierkorb'})).toBeInTheDocument());
  });

  it('lists trashed folder name and path', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Altprojekte')).toBeInTheDocument());
    expect(screen.getByText(/\/Altprojekte\//)).toBeInTheDocument();
  });

  it('shows empty state when trash is empty', async () => {
    vi.mocked(foldersApi.getTrash).mockResolvedValue([]);
    const store = configureStore({reducer: {auth: authReducer, theme: themeReducer}});
    store.dispatch(setCredentials({
      user: {id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN'},
      accessToken: 'at', refreshToken: 'rt',
    }));
    const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
    render(
        <Provider store={store}>
          <QueryClientProvider client={qc}>
            <MemoryRouter future={{
              v7_startTransition: true,
              v7_relativeSplatPath: true
            }}><TrashPage/></MemoryRouter>
          </QueryClientProvider>
        </Provider>
    );
    await waitFor(() => expect(screen.getByText('Papierkorb ist leer.')).toBeInTheDocument());
  });

  it('opens confirm dialog when delete button is clicked', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Altprojekte')).toBeInTheDocument());
    const deleteBtn = screen.getByLabelText('endgültig löschen');
    fireEvent.click(deleteBtn);
    expect(screen.getByText('Ordner endgültig löschen?')).toBeInTheDocument();
    expect(screen.getByText(/unwiderruflich gelöscht/)).toBeInTheDocument();
  });
});
