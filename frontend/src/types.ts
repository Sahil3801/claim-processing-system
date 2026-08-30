export type UserRole = 'CLAIMANT' | 'CLAIMS_OFFICER' | 'ADMIN';

export type ClaimStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'SETTLED';

export interface AuthTokenResponse {
  username: string;
  token: string;
}

export interface AuthSession extends AuthTokenResponse {
  role: UserRole;
  expiresAt: number;
}

export interface User {
  userId: number;
  username: string;
  email: string;
  role: UserRole;
  status: string;
}

export interface Claim {
  claimId: number;
  userId: number;
  emailId?: string | null;
  claimDate: string;
  claimAmount: number;
  claimType: string;
  description: string;
  claimStatus: ClaimStatus;
  lastUpdated: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ClaimFilters {
  page?: number;
  size?: number;
  status?: ClaimStatus | '';
  claimType?: string;
  userId?: number;
  sort?: string;
}

export interface CreateClaimRequest {
  userId: number;
  claimAmount: number;
  claimType: string;
  description: string;
  emailId?: string;
}

export interface ApiErrorResponse {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  violations?: Record<string, string>;
}

export interface OutcomeSummary {
  totalClaims: number;
  totalClaimAmount: number;
  averageClaimAmount: number;
}

export interface ClaimsSummary {
  totalClaims: number;
  totalClaimAmount: number;
  averageClaimAmount: number;
  pending: OutcomeSummary;
  approved: OutcomeSummary;
  rejected: OutcomeSummary;
  settled: OutcomeSummary;
}

export interface StatusReport {
  claimStatus: ClaimStatus;
  totalClaims: number;
  totalClaimAmount: number;
  averageClaimAmount: number;
}

export interface ClaimTypeReport {
  claimType: string;
  totalClaims: number;
  totalClaimAmount: number;
  averageClaimAmount: number;
}

export interface DailyReport {
  reportDate: string;
  totalClaims: number;
  totalClaimAmount: number;
  averageClaimAmount: number;
}
