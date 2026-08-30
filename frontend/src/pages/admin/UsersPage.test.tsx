import {render, screen, waitFor, within} from '@testing-library/react';
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
      memberId: 42,
      url: 'https://bob.example',
      phone: '0123',
      active: true,
      role: 'USER',
      lastLoginAt: null,
      createdAt: ''
    },
    {
      id: 3,
      username: 'carol',
      email: 'carol@test.com',
      displayName: 'Carol',
      memberId: null,
      url: null,
      phone: null,
      active: true,
      role: 'USER',
      lastLoginAt: '2026-08-29T14:30:00',
      createdAt: ''
    }
  ], page: 0, size: 20, totalElements: 2, totalPages: 1
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

  it('shows last login date, or a dash when the user never logged in', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('carol')).toBeInTheDocument());
    const bobRow = screen.getByText('bob').closest('tr')!;
    const carolRow = screen.getByText('carol').closest('tr')!;
    expect(within(bobRow).getByText('–')).toBeInTheDocument();
    expect(within(carolRow).getByText(/29\.08\.26,?\s+14:30/)).toBeInTheDocument();
  });
});
