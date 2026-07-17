import { describe, it, expect, vi, beforeEach } from 'vitest';
import axiosClient from './axiosClient';
import { login, logout, refreshAccessToken } from './auth';

vi.mock('./axiosClient', () => ({ default: { post: vi.fn() } }));

const mockedPost = vi.mocked((axiosClient as unknown as { post: ReturnType<typeof vi.fn> }).post);

describe('auth API', () => {
  beforeEach(() => { vi.clearAllMocks(); localStorage.clear(); });

  it('login posts credentials and returns LoginResponse', async () => {
    mockedPost.mockResolvedValueOnce({
      data: { accessToken: 'at123', refreshToken: 'rt456', expiresIn: 900,
               user: { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' } },
    });
    const result = await login({ username: 'admin', password: 'secret' });
    expect(mockedPost).toHaveBeenCalledWith('/auth/login', { username: 'admin', password: 'secret' });
    expect(result.accessToken).toBe('at123');
    expect(result.user.username).toBe('admin');
  });

  it('logout posts refresh token body', async () => {
    mockedPost.mockResolvedValueOnce({ data: {} });
    await logout('rt456');
    expect(mockedPost).toHaveBeenCalledWith('/auth/logout', { refreshToken: 'rt456' });
  });

  it('refreshAccessToken posts token and returns new tokens', async () => {
    mockedPost.mockResolvedValueOnce({
      data: { accessToken: 'newAt', refreshToken: 'newRt', expiresIn: 900,
               user: { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' } },
    });
    const result = await refreshAccessToken('rt456');
    expect(mockedPost).toHaveBeenCalledWith('/auth/refresh', { refreshToken: 'rt456' });
    expect(result.accessToken).toBe('newAt');
  });
});
