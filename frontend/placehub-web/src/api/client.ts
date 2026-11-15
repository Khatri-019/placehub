import axios, { AxiosError } from 'axios';
import { clearSession, getToken } from '../auth/session';
import { emitToast } from '../components/toastBus';

/** The only backend URL the frontend knows is the gateway. Baked in at build time. */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  config.headers.set('X-Correlation-Id', crypto.randomUUID());
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status;
    const url = error.config?.url ?? '';
    const isAuthCall = url.startsWith('/api/auth/');
    if (status === 401 && !isAuthCall) {
      clearSession();
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign('/login?expired=1');
      }
    } else if (status === 403 && !isAuthCall) {
      emitToast({ kind: 'error', message: 'You are not allowed to do that.' });
    }
    return Promise.reject(error);
  },
);
