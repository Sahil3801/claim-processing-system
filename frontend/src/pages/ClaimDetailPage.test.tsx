import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, type AxiosResponse } from 'axios';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getClaim, transitionClaim } from '../api/claims';
import { AUTH_STORAGE_KEY } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import type { Claim, ClaimStatus } from '../types';
import { ClaimDetailPage } from './ClaimDetailPage';

vi.mock('../api/claims', () => ({
  getClaim: vi.fn(),
  submitClaim: vi.fn(),
  transitionClaim: vi.fn(),
}));

function claim(claimStatus: ClaimStatus): Claim {
  return {
    claimId: 12, userId: 7, emailId: 'claimant@example.com', claimDate: '2026-01-02T10:00:00Z',
    claimAmount: 125.5, claimType: 'MEDICAL', description: 'Hospital visit', claimStatus,
    lastUpdated: '2026-01-03T10:00:00Z',
  };
}

function apiError(status: number, error: string, message: string) {
  return new AxiosError(message, String(status), undefined, undefined,
    { status, data: { status, error, message } } as AxiosResponse);
}

function renderPage() {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({
    username: 'officer', token: 'token', role: 'CLAIMS_OFFICER', expiresAt: Date.now() + 60_000,
  }));
  render(<MemoryRouter initialEntries={['/claims/12']}><AuthProvider><Routes>
    <Route path="/claims/:id" element={<ClaimDetailPage />} />
  </Routes></AuthProvider></MemoryRouter>);
}

describe('ClaimDetailPage conflicts', () => {
  beforeEach(() => { vi.mocked(getClaim).mockReset(); vi.mocked(transitionClaim).mockReset(); });

  it('reloads the claim and explains when another officer decided first', async () => {
    vi.mocked(getClaim)
      .mockResolvedValueOnce(claim('UNDER_REVIEW'))
      .mockResolvedValueOnce(claim('REJECTED'));
    vi.mocked(transitionClaim).mockRejectedValueOnce(
      apiError(409, 'CONCURRENT_CLAIM_UPDATE', 'Claim was changed by another request; reload it and retry'));
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Approve' }));

    expect(await screen.findByText(/Another user updated this claim while you were working/)).toHaveTextContent('It is now Rejected');
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(getClaim).toHaveBeenCalledTimes(2);
  });

  it('keeps showing other errors without reloading', async () => {
    vi.mocked(getClaim).mockResolvedValueOnce(claim('UNDER_REVIEW'));
    vi.mocked(transitionClaim).mockRejectedValueOnce(apiError(403, 'UNAUTHORIZED_CLAIM_ACCESS', 'Not allowed'));
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Approve' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Not allowed'));
    expect(getClaim).toHaveBeenCalledTimes(1);
  });
});
