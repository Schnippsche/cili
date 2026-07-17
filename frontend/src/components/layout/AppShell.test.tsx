import { type ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { configureStore } from '@reduxjs/toolkit';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import authReducer, { setCredentials } from '../../store/authSlice';
import themeReducer from '../../store/themeSlice';
import AppShell from './AppShell';

const mockUser = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' as const };

function renderShell(children: ReactNode = <div>Content</div>) {
  const store = configureStore({ reducer: { auth: authReducer, theme: themeReducer } });
  store.dispatch(setCredentials({ user: mockUser, accessToken: 'at', refreshToken: 'rt' }));
  const qc = new QueryClient();
  return render(
    <Provider store={store}><QueryClientProvider client={qc}><MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AppShell>{children}</AppShell>
    </MemoryRouter></QueryClientProvider></Provider>
  );
}

describe('AppShell', () => {
  it('renders children', () => {
    renderShell(<div>Page Content</div>);
    expect(screen.getByText('Page Content')).toBeInTheDocument();
  });

  it('renders CILI title in the app bar', () => {
    renderShell();
    expect(screen.getByText('CILI')).toBeInTheDocument();
  });

  it('shows Home and Search nav links in sidebar', () => {
    renderShell();
    expect(screen.getByText('Startseite')).toBeInTheDocument();
    expect(screen.getByText('Globale Suche')).toBeInTheDocument();
  });

  it('shows Admin link for ADMIN role', () => {
    renderShell();
    expect(screen.getByText('Administration')).toBeInTheDocument();
  });

  it('toggles sidebar on menu icon click without crashing', async () => {
    renderShell();
    await userEvent.click(screen.getByRole('button', { name: /menu/i }));
    expect(screen.getByText('CILI')).toBeInTheDocument();
  });

  it('starts with sidebar closed on mobile (matchMedia matches)', () => {
    vi.mocked(window.matchMedia).mockImplementation((query: string) => ({
      matches: true,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));
    renderShell();
    // On mobile the temporary drawer starts closed — nav items not in the DOM
    expect(screen.queryByText('Startseite')).not.toBeInTheDocument();
  });
});
