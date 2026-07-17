import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import { BASE } from './base';
import { store } from '../store/store';
import { setAccessToken, clearCredentials } from '../store/authSlice';

const axiosClient: AxiosInstance = axios.create({
  baseURL: `${BASE}/api`,
  headers: { 'Content-Type': 'application/json' },
});

axiosClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('accessToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  if (config.data instanceof FormData) {
    delete config.headers['Content-Type'];
  }
  return config;
});

let isRefreshing = false;
type QueueEntry = { resolve: (token: string) => void; reject: (err: unknown) => void };
let failedQueue: QueueEntry[] = [];

function processQueue(error: unknown, token: string | null = null) {
  failedQueue.forEach(({ resolve, reject }) => { error ? reject(error) : resolve(token!); });
  failedQueue = [];
}

axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const orig = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    if (error.response?.status === 401 && !orig._retry) {
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => { failedQueue.push({ resolve, reject }); })
          .then((token) => { orig.headers.Authorization = `Bearer ${token}`; return axiosClient(orig); });
      }
      orig._retry = true;
      isRefreshing = true;
      try {
        const rt = localStorage.getItem('refreshToken');
        if (!rt) throw new Error('No refresh token');
        const { data } = await axios.post<{ accessToken: string; refreshToken: string }>(
          `${BASE}/api/auth/refresh`, { refreshToken: rt }
        );
        store.dispatch(setAccessToken(data.accessToken));
        localStorage.setItem('refreshToken', data.refreshToken);
        processQueue(null, data.accessToken);
        orig.headers.Authorization = `Bearer ${data.accessToken}`;
        return axiosClient(orig);
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        store.dispatch(clearCredentials());
        globalThis.dispatchEvent(new Event('auth:logout'));
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }
    throw error;
  }
);

export default axiosClient;
