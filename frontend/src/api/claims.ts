import { api } from './client';
import type { Claim, ClaimFilters, ClaimStatus, CreateClaimRequest, PageResponse } from '../types';

function idempotencyKey(operation: string): string {
  return `${operation}-${crypto.randomUUID()}`;
}

export async function createClaim(request: CreateClaimRequest): Promise<Claim> {
  const { data } = await api.post<Claim>('/claims', request, {
    headers: { 'Idempotency-Key': idempotencyKey('create') },
  });
  return data;
}

export async function submitClaim(claimId: number): Promise<Claim> {
  const { data } = await api.post<Claim>(`/claims/${claimId}/submit`, undefined, {
    headers: { 'Idempotency-Key': idempotencyKey(`submit-${claimId}`) },
  });
  return data;
}

export async function getClaim(claimId: number): Promise<Claim> {
  const { data } = await api.get<Claim>(`/claims/${claimId}`);
  return data;
}

export async function getMyClaims(filters: ClaimFilters): Promise<PageResponse<Claim>> {
  const { data } = await api.get<PageResponse<Claim>>('/claims/my', { params: filters });
  return data;
}

export async function getClaims(filters: ClaimFilters): Promise<PageResponse<Claim>> {
  const { data } = await api.get<PageResponse<Claim>>('/claims', { params: filters });
  return data;
}

export async function transitionClaim(
  claimId: number,
  action: 'review' | 'approve' | 'reject' | 'settle',
  reason?: string,
): Promise<Claim> {
  const { data } = await api.post<Claim>(`/claims/${claimId}/${action}`, reason ? { reason } : {});
  return data;
}

export const claimStatuses: ClaimStatus[] = [
  'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'SETTLED',
];
