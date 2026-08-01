import authReducer, {clearCredentials, setAccessToken, setCredentials} from './authSlice';

const mockUser = {id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' as const};

describe('authSlice', () => {
  beforeEach(() => localStorage.clear());

  it('initial state is unauthenticated when localStorage is empty', () => {
    const state = authReducer(undefined, {type: '@@INIT'});
    expect(state.isAuthenticated).toBe(false);
    expect(state.user).toBeNull();
  });

  it('setCredentials stores user, token and marks authenticated', () => {
    const state = authReducer(undefined, setCredentials({
      user: mockUser,
      accessToken: 'at',
      refreshToken: 'rt'
    }));
    expect(state.isAuthenticated).toBe(true);
    expect(state.user?.username).toBe('admin');
    expect(localStorage.getItem('accessToken')).toBe('at');
    expect(localStorage.getItem('refreshToken')).toBe('rt');
  });

  it('setAccessToken updates only the token field', () => {
    const after = authReducer(
        authReducer(undefined, setCredentials({
          user: mockUser,
          accessToken: 'at',
          refreshToken: 'rt'
        })),
        setAccessToken('newAt')
    );
    expect(after.accessToken).toBe('newAt');
    expect(after.user?.username).toBe('admin');
  });

  it('clearCredentials resets all auth state and localStorage', () => {
    const loggedIn = authReducer(undefined, setCredentials({
      user: mockUser,
      accessToken: 'at',
      refreshToken: 'rt'
    }));
    const state = authReducer(loggedIn, clearCredentials());
    expect(state.isAuthenticated).toBe(false);
    expect(state.user).toBeNull();
    expect(localStorage.getItem('accessToken')).toBeNull();
  });
});
