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

/** Machine-readable error code from the API error envelope, such as CONCURRENT_CLAIM_UPDATE. */
export function errorCode(error: unknown): string | undefined {
  return axios.isAxiosError<ApiErrorResponse>(error) ? error.response?.data?.error : undefined;
}

export function errorStatus(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined;
}

// API field names as the user sees them on forms.
const fieldLabels: Record<string, string> = {
  claimAmount: 'Claim amount',
  claimType: 'Claim type',
  description: 'Description',
  emailId: 'Email for updates',
  email: 'Email address',
  username: 'Username',
  password: 'Password',
  reason: 'Reason',
};

/** "claimAmount must be greater than zero" reads as "Claim amount must be greater than zero." */
function readableViolation(field: string, message: string): string {
  const label = fieldLabels[field];
  let text = label && message.startsWith(field) ? label + message.slice(field.length) : message;
  text = text.charAt(0).toUpperCase() + text.slice(1);
  return /[.!?]$/.test(text) ? text : `${text}.`;
}

// Plain-language text for API errors whose raw message is written for developers.
const codeMessages: Record<string, string> = {
  CLAIM_NOT_FOUND: 'This claim could not be found. Check the claim number and try again.',
  UNAUTHORIZED_CLAIM_ACCESS: 'You do not have access to this claim.',
  ACCESS_DENIED: 'You do not have permission to do that.',
  USER_NOT_FOUND: 'That account could not be found.',
  DATA_INTEGRITY_CONFLICT: 'This conflicts with an existing record. Check the details and try again.',
  IDEMPOTENCY_KEY_REUSE: 'This request was already used for a different action. Refresh the page and try again.',
  MISSING_IDEMPOTENCY_KEY: 'The request could not be sent correctly. Refresh the page and try again.',
  INVALID_REPORT_DATE_RANGE: 'Choose a start date on or before the end date.',
  INVALID_REQUEST: 'Some of the information sent was not valid. Check the form and try again.',
};

export function errorMessage(error: unknown): string {
  if (!axios.isAxiosError<ApiErrorResponse>(error)) return 'Something went wrong. Please try again.';
  // Transport and server failures carry no useful API message; describe them in plain language.
  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
    return 'The server is taking too long to respond. It may be starting up, so please try again in a minute.';
  }
  if (!error.response) return 'Could not reach the claims service. Check your connection and try again.';
  if (error.response.status >= 500) {
    return 'The claims service is unavailable right now. Please try again shortly.';
  }
  const body = error.response.data;
  if (body?.violations && Object.keys(body.violations).length) {
    return Object.entries(body.violations).map(([field, message]) => readableViolation(field, message)).join(' ');
  }
  if (body?.error && codeMessages[body.error]) return codeMessages[body.error];
  if (error.response.status === 401) return 'Your session has expired. Please sign in again.';
  return body?.message || 'The request could not be completed. Please try again.';
}
