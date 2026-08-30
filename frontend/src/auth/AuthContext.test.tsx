import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { login as loginRequest } from '../api/auth';
import { AUTH_STORAGE_KEY } from '../api/client';
import { AuthProvider, useAuth } from './AuthContext';

vi.mock('../api/auth', () => ({ login: vi.fn() }));

function token(role: string, expiresInSeconds = 3600) {
  const payload = btoa(JSON.stringify({ role, exp: Math.floor(Date.now() / 1000) + expiresInSeconds }));
  return `header.${payload}.signature`;
}

function Probe() {
  const auth = useAuth();
  return <div><span>{auth.session?.role ?? 'SIGNED_OUT'}</span><button onClick={() => void auth.login('alex', 'password123')}>Login</button></div>;
}

describe('AuthProvider', () => {
  beforeEach(() => vi.mocked(loginRequest).mockReset());

  it('decodes the database role from the JWT and persists the session', async () => {
    vi.mocked(loginRequest).mockResolvedValue({ username: 'alex', token: token('CLAIMS_OFFICER') });
    render(<AuthProvider><Probe /></AuthProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));
    await screen.findByText('CLAIMS_OFFICER');
    expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? '{}').role).toBe('CLAIMS_OFFICER');
  });

  it('discards an expired stored session', async () => {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ username: 'old', token: token('ADMIN', -10), role: 'ADMIN', expiresAt: Date.now() - 1 }));
    render(<AuthProvider><Probe /></AuthProvider>);
    await waitFor(() => expect(screen.getByText('SIGNED_OUT')).toBeInTheDocument());
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });
});
