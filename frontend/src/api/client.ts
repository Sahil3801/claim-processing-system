import axios, { AxiosError } from 'axios';
import type { ApiErrorResponse } from '../types';

export const AUTH_STORAGE_KEY = 'claims.auth';
export const AUTH_EXPIRED_EVENT = 'claims:auth-expired';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
});

api.interceptors.request.use((config) => {
  const stored = localStorage.getItem(AUTH_STORAGE_KEY);
  if (stored) {
    try {
      const token = (JSON.parse(stored) as { token?: string }).token;
      if (token) config.headers.Authorization = `Bearer ${token}`;
    } catch {
      localStorage.removeItem(AUTH_STORAGE_KEY);
    }
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
    }
    return Promise.reject(error);
  },
);

export function errorMessage(error: unknown): string {
  if (!axios.isAxiosError<ApiErrorResponse>(error)) return 'Something went wrong. Please try again.';
  const body = error.response?.data;
  if (body?.violations && Object.keys(body.violations).length) {
    return Object.values(body.violations).join(' ');
  }
  return body?.message || (error.response?.status === 401
    ? 'Your credentials are incorrect or your session has expired.'
    : error.message || 'The request could not be completed.');
}
