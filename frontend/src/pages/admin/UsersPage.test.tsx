import {render, screen, waitFor} from '@testing-library/react';
import {Provider} from 'react-redux';
import {MemoryRouter} from 'react-router-dom';
import {configureStore} from '@reduxjs/toolkit';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import authReducer, {setCredentials} from '../../store/authSlice';
import themeReducer from '../../store/themeSlice';
import UsersPage from './UsersPage';
import * as adminApi from '../../api/admin';

vi.mock('../../api/admin');
vi.mocked(adminApi.listUsers).mockResolvedValue({
  content: [
    {
      id: 2,
      username: 'bob',
      email: 'bob@test.com',
      displayName: 'Bob',
      memberId: null,
      url: null,
      phone: null,
      active: true,
      role: 'USER',
      createdAt: ''
    }
  ], page: 0, size: 20, totalElements: 1, totalPages: 1
});

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
  }}><UsersPage/></MemoryRouter></QueryClientProvider></Provider>);
}

describe('UsersPage', () => {
  it('renders page title', () => {
    renderPage();
    expect(screen.getByText(/benutzerverwaltung/i)).toBeInTheDocument();
  });

  it('shows loaded users', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('bob')).toBeInTheDocument());
  });
});
