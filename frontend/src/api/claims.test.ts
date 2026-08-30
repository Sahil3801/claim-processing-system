import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './client';
import { createClaim, submitClaim, transitionClaim } from './claims';

vi.mock('./client', () => ({ api: { post: vi.fn(), get: vi.fn() } }));

const claim = { claimId: 7, claimStatus: 'DRAFT' };

describe('claims API', () => {
  beforeEach(() => {
    vi.mocked(api.post).mockReset();
    vi.mocked(api.post).mockResolvedValue({ data: claim });
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('00000000-0000-4000-8000-000000000001');
  });

  it('adds a unique idempotency key when creating a claim', async () => {
    const request = { userId: 2, claimAmount: 50, claimType: 'Medical', description: 'Treatment' };
    await createClaim(request);
    expect(api.post).toHaveBeenCalledWith('/claims', request, { headers: { 'Idempotency-Key': 'create-00000000-0000-4000-8000-000000000001' } });
  });

  it('adds an operation-specific idempotency key when submitting', async () => {
    await submitClaim(7);
    expect(api.post).toHaveBeenCalledWith('/claims/7/submit', undefined, { headers: { 'Idempotency-Key': 'submit-7-00000000-0000-4000-8000-000000000001' } });
  });

  it('sends the mandatory rejection reason', async () => {
    await transitionClaim(7, 'reject', 'Coverage excluded');
    expect(api.post).toHaveBeenCalledWith('/claims/7/reject', { reason: 'Coverage excluded' });
  });
});
