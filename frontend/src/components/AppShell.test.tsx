import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AUTH_STORAGE_KEY } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import type { UserRole } from '../types';
import { AppShell } from './AppShell';

function renderAt(path: string, role: UserRole) {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({
    username: 'user', token: 'token', role, expiresAt: Date.now() + 60_000,
  }));
  render(<MemoryRouter initialEntries={[path]}><AuthProvider><Routes>
    <Route path="/" element={<AppShell />}><Route path="*" element={<div />} /></Route>
  </Routes></AuthProvider></MemoryRouter>);
}

const activeLinks = () => screen.getAllByRole('link').filter((link) => link.classList.contains('active'))
  .map((link) => link.textContent);

describe('AppShell navigation', () => {
  it('highlights only the claims queue, not the officer dashboard, on the queue page', () => {
    renderAt('/officer/claims', 'CLAIMS_OFFICER');
    expect(activeLinks()).toEqual(['Claims queue']);
  });

  it('highlights only the officer dashboard on the dashboard page', () => {
    renderAt('/officer', 'ADMIN');
    expect(activeLinks()).toEqual(['Officer dashboard']);
  });

  it('highlights only new claim, not my claims, on the new-claim page', () => {
    renderAt('/claims/new', 'CLAIMANT');
    expect(activeLinks()).toEqual(['New claim']);
  });
});
