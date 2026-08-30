import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AUTH_STORAGE_KEY } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import { ProtectedRoute } from './ProtectedRoute';

function renderRoute(role?: 'CLAIMANT' | 'CLAIMS_OFFICER' | 'ADMIN') {
  if (role) localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ username: 'user', token: 'token', role, expiresAt: Date.now() + 60_000 }));
  render(<MemoryRouter initialEntries={['/reports']}><AuthProvider><Routes>
    <Route path="/login" element={<div>Login page</div>} />
    <Route path="/" element={<div>Home page</div>} />
    <Route path="/reports" element={<ProtectedRoute roles={['ADMIN']}><div>Admin reports</div></ProtectedRoute>} />
  </Routes></AuthProvider></MemoryRouter>);
}

describe('ProtectedRoute', () => {
  it('allows a role included by the route', () => { renderRoute('ADMIN'); expect(screen.getByText('Admin reports')).toBeInTheDocument(); });
  it('redirects an unauthorized role', () => { renderRoute('CLAIMANT'); expect(screen.getByText('Home page')).toBeInTheDocument(); });
  it('redirects an anonymous user to login', () => { renderRoute(); expect(screen.getByText('Login page')).toBeInTheDocument(); });
});
