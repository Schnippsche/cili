import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {Provider} from 'react-redux';
import {MemoryRouter} from 'react-router-dom';
import {configureStore} from '@reduxjs/toolkit';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import authReducer from '../store/authSlice';
import LoginPage from './LoginPage';
import * as authApi from '../api/auth';

vi.mock('../api/auth');
const mockedLogin = vi.mocked(authApi.login);

function renderLogin() {
  const store = configureStore({reducer: {auth: authReducer}});
  const qc = new QueryClient();
  return render(
      <Provider store={store}><QueryClientProvider client={qc}><MemoryRouter
          future={{v7_startTransition: true, v7_relativeSplatPath: true}}>
        <LoginPage/>
      </MemoryRouter></QueryClientProvider></Provider>
  );
}

describe('LoginPage', () => {
  it('renders username, password fields and sign-in button', () => {
    renderLogin();
    expect(screen.getByLabelText(/benutzername/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/passwort/i)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /anmelden/i})).toBeInTheDocument();
  });

  it('calls login API with entered credentials on submit', async () => {
    mockedLogin.mockResolvedValueOnce({
      accessToken: 'at', refreshToken: 'rt', expiresIn: 900,
      user: {id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN'},
    });
    renderLogin();
    await userEvent.type(screen.getByLabelText(/benutzername/i), 'admin');
    await userEvent.type(screen.getByLabelText(/passwort/i), 'secret');
    await userEvent.click(screen.getByRole('button', {name: /anmelden/i}));
    await waitFor(() => expect(mockedLogin).toHaveBeenCalledWith({
      username: 'admin',
      password: 'secret'
    }));
  });

  it('shows an error alert on failed login', async () => {
    mockedLogin.mockRejectedValueOnce({response: {data: {message: 'Invalid credentials'}}});
    renderLogin();
    await userEvent.type(screen.getByLabelText(/benutzername/i), 'admin');
    await userEvent.type(screen.getByLabelText(/passwort/i), 'wrong');
    await userEvent.click(screen.getByRole('button', {name: /anmelden/i}));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
  });
});
